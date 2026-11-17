package tn.fastcoloc.listings;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Same contract for Web and native Android; author/status/dates are server controlled. */
public record ListingWrite(
 @NotNull Kind kind,
 @NotBlank @Size(min=5,max=160) String title,
 @NotBlank @Size(min=20,max=10000) String description,
 @NotNull @Positive Long localityId,
 @NotBlank @Size(max=250) String street,
 @NotNull @DecimalMin("30") @DecimalMax("38") BigDecimal latitude,
 @NotNull @DecimalMin("7") @DecimalMax("12") BigDecimal longitude,
 @NotNull @DecimalMin("0") @DecimalMax("999999999.999") @Digits(integer=9,fraction=3) BigDecimal rent,
 @NotNull @Min(1) @Max(50) Integer capacity,
 @NotBlank @Size(max=80) String propertyType,
 @Size(max=80) String roommateType,
 @NotNull @Size(max=30) List<@NotBlank @Size(max=80) String> equipment,
 @NotNull LocalDate availableFrom,
 LocalDate availableUntil,
 @NotNull @Min(1) @Max(365) Integer publicationDays,
 @PositiveOrZero Long version
) { public enum Kind { OFFER, REQUEST } }
