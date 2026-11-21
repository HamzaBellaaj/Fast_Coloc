package tn.fastcoloc.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown=true)
public record RentEstimateResponse(boolean success, JsonNode logement, JsonNode analyse_visuelle,
                                   Estimation estimation, JsonNode comparables) {
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record Estimation(boolean disponible, BigDecimal loyer_min_tnd, BigDecimal loyer_max_tnd,
                             BigDecimal loyer_conseille_tnd, String fiabilite, String explication) {}
}
