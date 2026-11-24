package tn.fastcoloc.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.fastcoloc.api.ApiException;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private static final Set<String> CAPTURE_EVENTS = Set.of("PAYMENT.CAPTURE.COMPLETED",
        "PAYMENT.CAPTURE.PENDING", "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.DECLINED");
    private final PaymentService service;
    private final PayPalService paypal;

    public PaymentController(PaymentService service, PayPalService paypal) {
        this.service = service;
        this.paypal = paypal;
    }

    public record CreateRequest(UUID listingId, int months, PaymentService.CheckoutMethod method, UUID requestId) {}
    public record ConfirmRequest(UUID id, boolean cancel) {}

    @PostMapping("/create")
    public PaymentService.Result create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRequest body) {
        return service.create(actor(jwt), body.listingId(), body.months(), body.method(), body.requestId());
    }

    @PostMapping("/capture")
    public PaymentService.Result confirm(@AuthenticationPrincipal Jwt jwt, @RequestBody ConfirmRequest body) {
        return service.confirm(actor(jwt), body.id(), body.cancel());
    }

    @PostMapping("/webhooks/paypal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void paypalWebhook(@RequestHeader HttpHeaders headers, @RequestBody JsonNode event) {
        if (!paypal.verify(headers, event)) throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_WEBHOOK", "Signature PayPal invalide.");
        String type = event.path("event_type").asText();
        JsonNode resource = event.path("resource");
        if ("CHECKOUT.ORDER.APPROVED".equals(type)) {
            service.webhook(resource.path("id").asText(), true);
        } else if (CAPTURE_EVENTS.contains(type)) {
            String order = resource.path("supplementary_data").path("related_ids").path("order_id").asText();
            service.webhook(order, false);
        }
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Connexion requise.");
        return UUID.fromString(jwt.getSubject());
    }
}
