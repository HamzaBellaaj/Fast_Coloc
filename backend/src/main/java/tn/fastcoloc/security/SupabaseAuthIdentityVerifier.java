package tn.fastcoloc.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tn.fastcoloc.api.ApiException;

@Component
public class SupabaseAuthIdentityVerifier implements AuthIdentityVerifier {
    private final RestClient client;
    private final String key;

    public SupabaseAuthIdentityVerifier(@Value("${fastcoloc.auth.url}") String url,
                                        @Value("${fastcoloc.auth.publishable-key}") String key) {
        this.key = key;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public void requireConfirmed(Jwt jwt) {
        JsonNode user;
        try {
            user = client.get().uri("/auth/v1/user").header("apikey", key)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session Supabase invalide.");
            }
            throw unavailable();
        } catch (RestClientException e) {
            throw unavailable();
        }
        if (user == null || !jwt.getSubject().equalsIgnoreCase(user.path("id").asText())
                || user.path("is_anonymous").asBoolean(true)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "Identité Supabase invalide.");
        }
        if (user.path("email").asText().isBlank() || user.path("email_confirmed_at").asText("").isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_CONFIRMED",
                    "Confirmez votre adresse e-mail avant de continuer.");
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE",
                "Vérification Supabase Auth indisponible.");
    }
}
