package tn.fastcoloc.recommendations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import tn.fastcoloc.api.ApiException;

@Service
public class RecommendationGateway {
    private final RestClient client;
    private final String webhook;
    private final ObjectMapper mapper;
    public RecommendationGateway(ObjectMapper mapper,
            @Value("${fastcoloc.ai.recommendations-webhook-url}") String webhook,
            @Value("${fastcoloc.ai.recommendations-timeout-seconds:180}") int timeout) {
        this.mapper = mapper; this.webhook = webhook;
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        client = RestClient.builder().requestFactory(factory).build();
    }
    public JsonNode recommend(Map<String,Object> payload) {
        try {
            String body = client.post().uri(webhook).contentType(MediaType.APPLICATION_JSON)
                .body(payload).retrieve().body(String.class);
            return mapper.readTree(body == null ? "" : body);
        } catch (ResourceAccessException e) {
            Throwable cause = e; while (cause.getCause() != null) cause = cause.getCause();
            boolean timeout = cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException;
            throw new ApiException(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
                timeout ? "RECOMMENDATIONS_TIMEOUT" : "RECOMMENDATIONS_CONNECTION",
                timeout ? "Le calcul prend trop de temps. Réessaie." : "Le service de recommandations est injoignable. Réessaie.");
        } catch (RestClientException | IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RECOMMENDATIONS_INVALID", "Le service IA n’a pas renvoyé un classement valide. Réessaie.");
        }
    }
}
