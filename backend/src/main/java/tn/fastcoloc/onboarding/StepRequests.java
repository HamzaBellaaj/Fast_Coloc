package tn.fastcoloc.onboarding;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Closed request contracts: identity, role, status and completion are never writable by clients. */
public final class StepRequests {
 private StepRequests() {}
 public record Identity(@NotBlank @Size(max=100) String firstName,@NotBlank @Size(max=100) String lastName,
                        @Size(max=80) String gender,@NotNull @Min(0) @Max(130) Short age) {}
 public record Geography(@NotNull @Pattern(regexp="[A-Z]{2}") String countryCode,
                         @NotNull @Positive Long governorateId,@NotNull @Positive Long localityId,
                         @NotBlank @Size(max=250) String street,@NotBlank @Size(max=20) String postalCode) {}
 public record Personal(@NotNull @Size(max=50) List<@NotBlank @Size(max=100) String> mainActivities,
                        @NotNull @Size(max=50) List<@NotBlank @Size(max=100) String> hobbies,
                        @Pattern(regexp="[A-Z]{2}") String nationalityCode,
                        @NotNull @Size(max=50) List<@NotBlank @Size(max=100) String> spokenLanguages,
                        @Size(max=120) String religion,Boolean smoking,@Size(max=120) String cleanliness,
                        @Size(max=2000) String biography) {}
 public record Contacts(@Size(max=30) @Pattern(regexp="[+0-9() .-]*") String mobilePhone,
                        @Size(max=30) @Pattern(regexp="[+0-9() .-]*") String residentialPhone,
                        @Size(max=250) String callAvailability,@Size(max=250) String msnContact,
                        @Size(max=250) String skypeContact,@Size(max=500) @Pattern(regexp="^$|https?://[^\\s]+") String websiteUrl) {}
 public record Photos(@NotNull @Size(min=1,max=5) List<@NotNull UUID> photoIds) {}
 public record Preferences(@DecimalMin("0") @Digits(integer=9,fraction=3) BigDecimal minBudget,
                           @DecimalMin("0") @Digits(integer=9,fraction=3) BigDecimal maxBudget,
                           LocalDate moveInDate,@NotNull @Size(max=30) List<@NotNull @Positive Long> localityIds) {}
 public record Caption(@Size(max=500) String caption,@Min(0) Integer displayOrder,Boolean primary) {}
}
