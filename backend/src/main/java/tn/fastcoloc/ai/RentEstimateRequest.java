package tn.fastcoloc.ai;

import jakarta.validation.constraints.*;
import java.util.List;

public record RentEstimateRequest(
    @NotBlank @Size(max=300) String localisation,
    @NotBlank @Size(max=80) String type,
    @Size(max=250) String street,
    @Size(max=10000) String description,
    @Min(1) @Max(50) Integer capacity,
    @Size(max=30) List<@NotBlank @Size(max=80) String> equipment
) {
    public String message() {
        return "Estime le loyer mensuel en TND de ce logement en Tunisie. Localisation : " + localisation
            + ". Type : " + type + ". Adresse : " + value(street)
            + ". Capacité : " + (capacity == null ? "non précisée" : capacity)
            + ". Description (superficie, mobilier et autres caractéristiques si renseignés) : " + value(description)
            + ". Équipements : " + (equipment == null ? "non précisés" : String.join(", ", equipment));
    }
    private static String value(String text) { return text == null || text.isBlank() ? "non précisé" : text; }
}
