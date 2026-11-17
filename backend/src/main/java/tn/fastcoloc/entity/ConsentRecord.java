package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="consent_records", schema="fastcoloc") 
public class ConsentRecord {
 public ConsentRecord() {}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", columnDefinition="bigint", nullable=false)
 private Long id;
 public Long getId() { return id; }
 public void setId(Long value) { this.id=value; }
 @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Column(name="consent_code", columnDefinition="varchar(100)", length=100, nullable=false)
 private String consentCode;
 public String getConsentCode() { return consentCode; }
 public void setConsentCode(String value) { this.consentCode=value; }
 @Column(name="policy_version", columnDefinition="varchar(100)", length=100, nullable=false)
 private String policyVersion;
 public String getPolicyVersion() { return policyVersion; }
 public void setPolicyVersion(String value) { this.policyVersion=value; }
 @Column(name="accepted_at", columnDefinition="timestamptz", nullable=false)
 private Instant acceptedAt;
 public Instant getAcceptedAt() { return acceptedAt; }
 public void setAcceptedAt(Instant value) { this.acceptedAt=value; }


}
