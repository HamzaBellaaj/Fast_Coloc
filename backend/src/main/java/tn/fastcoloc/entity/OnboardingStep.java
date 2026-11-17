package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="onboarding_steps", schema="fastcoloc") @IdClass(OnboardingStep.Key.class)
public class OnboardingStep {
 public OnboardingStep() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Id @Column(name="step_number", columnDefinition="smallint", nullable=false)
 private Short stepNumber;
 public Short getStepNumber() { return stepNumber; }
 public void setStepNumber(Short value) { this.stepNumber=value; }
 @Column(name="validated_at", columnDefinition="timestamptz", nullable=false)
 private Instant validatedAt;
 public Instant getValidatedAt() { return validatedAt; }
 public void setValidatedAt(Instant value) { this.validatedAt=value; }

 public static class Key implements java.io.Serializable {
  public UUID userId;
  public Short stepNumber;
  public Key() {}
  @Override public boolean equals(Object other) { return other instanceof Key k && Objects.equals(userId,k.userId) && Objects.equals(stepNumber,k.stepNumber); }
  @Override public int hashCode() { return Objects.hash(userId,stepNumber); }
 }
}
