package tn.fastcoloc.profiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tn.fastcoloc.entity.Profile;

public record ProfileResponse(UUID id, String firstName, String lastName, String gender, Short age,
                              String role, String status, Instant completedAt, Long version,
                              Instant createdAt, Instant updatedAt, List<Integer> validatedSteps) {
    public static ProfileResponse of(Profile p, String role, List<Integer> steps) {
        return new ProfileResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getGender(), p.getAge(),
                role, "ACTIVE", p.getCompletedAt(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt(), steps);
    }
}
