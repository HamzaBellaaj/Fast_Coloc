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
 @Size(max=250) String street,
 @DecimalMin("30") @DecimalMax("38") BigDecimal latitude,
 @DecimalMin("7") @DecimalMax("12") BigDecimal longitude,
 @NotNull @DecimalMin("0") @DecimalMax("999999999.999") @Digits(integer=9,fraction=3) BigDecimal rent,
 @NotNull @Min(1) @Max(50) Integer capacity,
 @NotBlank @Size(max=80) String propertyType,
 @Size(max=80) String roommateType,
 @NotNull @Size(max=30) List<@NotBlank @Size(max=80) String> equipment,
 @NotNull LocalDate availableFrom,
 LocalDate availableUntil,
 @NotNull @Min(1) @Max(365) Integer publicationDays,
 @PositiveOrZero Long version
) {
 @com.fasterxml.jackson.annotation.JsonIgnore
 @AssertTrue(message="Une offre nécessite une adresse et une position GPS complète.")
 public boolean isOfferLocationValid(){return kind!=Kind.OFFER || street!=null&&!street.isBlank()&&latitude!=null&&longitude!=null;}
 @com.fasterxml.jackson.annotation.JsonIgnore
 @AssertTrue(message="Renseigne les deux coordonnées ou laisse la position vide.")
 public boolean isCoordinatePairValid(){return (latitude==null)==(longitude==null);}
 public enum Kind { OFFER, REQUEST } }
