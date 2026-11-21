package tn.fastcoloc.recommendations;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(boolean success, boolean profileIncomplete, List<Item> recommendations) {
    public record Item(Map<String,Object> listing, int score, String raison) {}
}
