package tn.fastcoloc.ai;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MultipartFile;
import tn.fastcoloc.api.ApiException;

@Service
public class RentEstimateService {
    private final RestClient client;
    private final String webhook;
    private final ObjectMapper mapper;
    public RentEstimateService(ObjectMapper mapper,
            @Value("${fastcoloc.ai.rent-webhook-url}") String webhook,
            @Value("${fastcoloc.ai.timeout-seconds:180}") int timeout) {
        this.mapper = mapper;
        this.webhook = webhook;
        // n8n handles HTTP upgrades for WebSockets. Avoid an h2c upgrade on multipart requests.
        var http = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        client = RestClient.builder().requestFactory(factory).build();
    }
    public RentEstimateResponse estimate(RentEstimateRequest request, List<MultipartFile> photos) {
        final RentEstimatePhotos.Photo photo;
        try {
            photo = RentEstimatePhotos.prepare(photos);
        } catch (IOException ex) {
            throw error(HttpStatus.BAD_REQUEST, "ESTIMATE_PHOTOS_INVALID", "Une photo est illisible. Vérifie les images sélectionnées.");
        }
        try {
            String boundary = "FastColoc" + UUID.randomUUID().toString().replace("-", "");
            var multipart = new ByteArrayOutputStream();
            // n8n treats a part with Content-Type as a file, including text/plain.
            // Leave the text part untyped: message stays in body, photo becomes binary file0.
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"message\"\r\n\r\n"
                + request.message() + (photos.size() > 1 ? ". La photo jointe regroupe les " + photos.size()
                    + " photos numérotées du même logement. Analyse toutes les vues pour une seule estimation, sans additionner les loyers ni les surfaces." : "")
                + "\r\n").getBytes(StandardCharsets.UTF_8));
            String extension = photo.extension();
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"logement."
                + extension + "\"\r\nContent-Type: " + photo.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            multipart.write(photo.bytes());
            multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            String body = client.post().uri(webhook)
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .body(multipart.toByteArray()).retrieve().body(String.class);
            var result = mapper.readValue(body == null ? "" : body, RentEstimateResponse.class);
            if (result == null)
                throw error(HttpStatus.BAD_GATEWAY, "ESTIMATE_INVALID", "La réponse IA est vide. Réessaie.");
            var e = result.estimation();
            if (!result.success() || e == null || !e.disponible())
                throw error(HttpStatus.BAD_GATEWAY, "ESTIMATE_UNAVAILABLE", "Estimation indisponible. Réessaie dans un instant.");
            if (e.loyer_min_tnd() == null || e.loyer_max_tnd() == null || e.loyer_conseille_tnd() == null
                    || e.loyer_min_tnd().signum() < 0 || e.loyer_min_tnd().compareTo(e.loyer_max_tnd()) > 0
                    || e.loyer_conseille_tnd().compareTo(e.loyer_min_tnd()) < 0
                    || e.loyer_conseille_tnd().compareTo(e.loyer_max_tnd()) > 0
                    || e.loyer_max_tnd().compareTo(new java.math.BigDecimal("999999999")) > 0)
                throw error(HttpStatus.BAD_GATEWAY, "ESTIMATE_INVALID", "La réponse IA est invalide. Réessaie.");
            return result;
        } catch (ResourceAccessException ex) {
            Throwable cause = ex;
            while (cause.getCause() != null) cause = cause.getCause();
            boolean timeout = cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException;
            throw error(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
                timeout ? "ESTIMATE_TIMEOUT" : "ESTIMATE_CONNECTION", timeout
                ? "L’analyse prend trop de temps. Réessaie." : "Le service d’estimation est injoignable. Réessaie.");
        } catch (RestClientException | IOException ex) {
            throw error(HttpStatus.BAD_GATEWAY, "ESTIMATE_INVALID", "Le service IA n’a pas renvoyé une estimation valide. Réessaie.");
        }
    }
    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
