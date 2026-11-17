package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="roommate_preferences", schema="fastcoloc") 
public class RoommatePreferences {
 public RoommatePreferences() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Column(name="min_budget", columnDefinition="numeric(12,3)", precision=12, scale=3, nullable=true)
 private BigDecimal minBudget;
 public BigDecimal getMinBudget() { return minBudget; }
 public void setMinBudget(BigDecimal value) { this.minBudget=value; }
 @Column(name="max_budget", columnDefinition="numeric(12,3)", precision=12, scale=3, nullable=true)
 private BigDecimal maxBudget;
 public BigDecimal getMaxBudget() { return maxBudget; }
 public void setMaxBudget(BigDecimal value) { this.maxBudget=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR) @Column(name="currency", columnDefinition="char(3)", length=3, nullable=false)
 private String currency;
 public String getCurrency() { return currency; }
 public void setCurrency(String value) { this.currency=value; }
 @Column(name="move_in_date", columnDefinition="date", nullable=true)
 private LocalDate moveInDate;
 public LocalDate getMoveInDate() { return moveInDate; }
 public void setMoveInDate(LocalDate value) { this.moveInDate=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name="additional_preferences", columnDefinition="jsonb", nullable=false)
 private Map<String,Object> additionalPreferences;
 public Map<String,Object> getAdditionalPreferences() { return additionalPreferences; }
 public void setAdditionalPreferences(Map<String,Object> value) { this.additionalPreferences=value; }


}
