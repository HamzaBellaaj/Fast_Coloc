package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="account_access", schema="fastcoloc") 
public class AccountAccess {
 public AccountAccess() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Column(name="app_role", columnDefinition="text", nullable=false)
 private String appRole;
 public String getAppRole() { return appRole; }
 public void setAppRole(String value) { this.appRole=value; }
 @Column(name="status", columnDefinition="text", nullable=false)
 private String status;
 public String getStatus() { return status; }
 public void setStatus(String value) { this.status=value; }
 @Column(name="blocked_at", columnDefinition="timestamptz", nullable=true)
 private Instant blockedAt;
 public Instant getBlockedAt() { return blockedAt; }
 public void setBlockedAt(Instant value) { this.blockedAt=value; }
 @Column(name="blocked_reason", columnDefinition="varchar(1000)", length=1000, nullable=true)
 private String blockedReason;
 public String getBlockedReason() { return blockedReason; }
 public void setBlockedReason(String value) { this.blockedReason=value; }
 @Column(name="updated_at", columnDefinition="timestamptz", nullable=false)
 private Instant updatedAt;
 public Instant getUpdatedAt() { return updatedAt; }
 public void setUpdatedAt(Instant value) { this.updatedAt=value; }


}
