package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="account_access_events", schema="fastcoloc") 
public class AccountAccessEvent {
 public AccountAccessEvent() {}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", columnDefinition="bigint", nullable=false)
 private Long id;
 public Long getId() { return id; }
 public void setId(Long value) { this.id=value; }
 @Column(name="subject_id", columnDefinition="uuid", nullable=true)
 private UUID subjectId;
 public UUID getSubjectId() { return subjectId; }
 public void setSubjectId(UUID value) { this.subjectId=value; }
 @Column(name="actor_id", columnDefinition="uuid", nullable=true)
 private UUID actorId;
 public UUID getActorId() { return actorId; }
 public void setActorId(UUID value) { this.actorId=value; }
 @Column(name="event_type", columnDefinition="text", nullable=false)
 private String eventType;
 public String getEventType() { return eventType; }
 public void setEventType(String value) { this.eventType=value; }
 @Column(name="reason", columnDefinition="varchar(1000)", length=1000, nullable=false)
 private String reason;
 public String getReason() { return reason; }
 public void setReason(String value) { this.reason=value; }
 @Column(name="created_at", columnDefinition="timestamptz", nullable=false)
 private Instant createdAt;
 public Instant getCreatedAt() { return createdAt; }
 public void setCreatedAt(Instant value) { this.createdAt=value; }


}
