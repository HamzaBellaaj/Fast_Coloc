package tn.fastcoloc.recommendations;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.fastcoloc.api.ApiException;

@Service
public class RecommendationService {
    private final RecommendationData data;
    private final RecommendationGateway gateway;
    public RecommendationService(RecommendationData data, RecommendationGateway gateway) {
        this.data = data; this.gateway = gateway;
    }
    public RecommendationResponse recommend(UUID actor) {
        var snapshot = data.snapshot(actor);
        if (snapshot.candidateIds().isEmpty()) return new RecommendationResponse(true, snapshot.profileIncomplete(), List.of());
        var response = gateway.recommend(snapshot.payload());
        if (response == null || !response.path("success").isBoolean() || !response.path("success").asBoolean()
                || !response.path("recommendations").isArray()) throw invalid();
        record Ranked(UUID id, int score, String raison) {}
        var ranking = new ArrayList<Ranked>();
        var seen = new LinkedHashSet<UUID>();
        for (var entry : response.path("recommendations")) {
            UUID id;
            try { id = UUID.fromString(entry.path("annonce_id").asText()); }
            catch (IllegalArgumentException e) { continue; }
            if (!snapshot.candidateIds().contains(id) || seen.contains(id) || !entry.path("score").isIntegralNumber()
                    || !entry.path("score").canConvertToInt() || !entry.path("raison").isTextual()
                    || entry.path("raison").asText().isBlank()) continue;
            seen.add(id);
            String reason = entry.path("raison").asText();
            ranking.add(new Ranked(id, Math.clamp(entry.path("score").asInt(), 0, 100), reason.substring(0, Math.min(250, reason.length()))));
            if (ranking.size() == 10) break;
        }
        if (!response.path("recommendations").isEmpty() && ranking.isEmpty()) throw invalid();
        var current = data.currentListings(actor, seen);
        var items = new ArrayList<RecommendationResponse.Item>();
        for (var ranked : ranking) {
            var listing = current.get(ranked.id());
            if (listing != null) items.add(new RecommendationResponse.Item(listing, ranked.score(), ranked.raison()));
        }
        return new RecommendationResponse(true, snapshot.profileIncomplete(), items);
    }
    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "RECOMMENDATIONS_INVALID", "Le classement reçu est invalide. Réessaie.");
    }
}
