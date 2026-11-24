package tn.fastcoloc.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PayPalService {
    private final RestClient api;
    @Value("${paypal.client-id}") private String clientId;
    @Value("${paypal.client-secret}") private String secret;
    @Value("${paypal.webhook-id}") private String webhookId;

    public PayPalService(@Value("${paypal.base-url}") String url, RestTemplateBuilder builder) {
        api = RestClient.builder(builder.connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10)).build()).baseUrl(url).build();
    }

    private String token() {
        if (clientId.isBlank() || secret.isBlank()) throw PaymentService.unavailable();
        return api.post().uri("/v1/oauth2/token").headers(h -> h.setBasicAuth(clientId, secret))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials")
            .retrieve().body(JsonNode.class).path("access_token").asText();
    }

    PaymentService.Checkout create(UUID id, BigDecimal amount, String frontend, boolean guestCheckout) {
        var body = Map.of("intent", "CAPTURE",
            "purchase_units", List.of(Map.of("custom_id", id.toString(),
                "amount", Map.of("currency_code", "USD", "value", amount.toPlainString()))),
            "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                "return_url", frontend + "/payment/success?id=" + id,
                "cancel_url", frontend + "/payment/cancel?id=" + id,
                "landing_page", guestCheckout ? "GUEST_CHECKOUT" : "NO_PREFERENCE",
                "user_action", "PAY_NOW"))));
        JsonNode order = api.post().uri("/v2/checkout/orders").headers(h -> h.setBearerAuth(token()))
            .header("PayPal-Request-Id", id.toString()).contentType(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(JsonNode.class);
        for (JsonNode link : order.path("links")) {
            if (List.of("approve", "payer-action").contains(link.path("rel").asText()))
                return new PaymentService.Checkout(order.path("id").asText(), link.path("href").asText());
        }
        throw PaymentService.unavailable();
    }

    JsonNode order(String reference, UUID id, boolean capture) {
        String access = token();
        JsonNode details = get(reference, access);
        if (capture && "APPROVED".equals(details.path("status").asText())) {
            try {
                api.post().uri("/v2/checkout/orders/{reference}/capture", reference)
                    .headers(h -> h.setBearerAuth(access))
                    .header("PayPal-Request-Id", "capture-" + id)
                    .contentType(MediaType.APPLICATION_JSON).body("{}").retrieve().toBodilessEntity();
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() != 422) throw e;
                // An already captured order is reconciled below. Funding errors allow another attempt.
            }
            details = get(reference, access);
        }
        return details;
    }

    private JsonNode get(String reference, String access) {
        return api.get().uri("/v2/checkout/orders/{reference}", reference)
            .headers(h -> h.setBearerAuth(access)).retrieve().body(JsonNode.class);
    }

    boolean verify(HttpHeaders headers, JsonNode event) {
        if (webhookId.isBlank()) throw PaymentService.unavailable();
        var body = new java.util.HashMap<String, Object>();
        for (String field : List.of("auth-algo", "cert-url", "transmission-id", "transmission-sig", "transmission-time")) {
            String value = headers.getFirst("PayPal-" + field);
            if (value == null || value.isBlank()) return false;
            body.put(field.replace('-', '_'), value);
        }
        body.put("webhook_id", webhookId);
        body.put("webhook_event", event);
        try {
            return "SUCCESS".equals(api.post().uri("/v1/notifications/verify-webhook-signature")
                .headers(h -> h.setBearerAuth(token())).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class).path("verification_status").asText());
        } catch (RestClientException e) {
            throw PaymentService.unavailable();
        }
    }
}
