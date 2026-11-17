package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="preferred_localities", schema="fastcoloc") @IdClass(PreferredLocality.Key.class)
public class PreferredLocality {
 public PreferredLocality() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Id @Column(name="locality_id", columnDefinition="bigint", nullable=false)
 private Long localityId;
 public Long getLocalityId() { return localityId; }
 public void setLocalityId(Long value) { this.localityId=value; }

 public static class Key implements java.io.Serializable {
  public UUID userId;
  public Long localityId;
  public Key() {}
  @Override public boolean equals(Object other) { return other instanceof Key k && Objects.equals(userId,k.userId) && Objects.equals(localityId,k.localityId); }
  @Override public int hashCode() { return Objects.hash(userId,localityId); }
 }
}
