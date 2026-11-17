package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="profiles", schema="fastcoloc") 
public class Profile {
 public Profile() {}
 @Id @Column(name="id", columnDefinition="uuid", nullable=false)
 private UUID id;
 public UUID getId() { return id; }
 public void setId(UUID value) { this.id=value; }
 @Column(name="first_name", columnDefinition="varchar(100)", length=100, nullable=true)
 private String firstName;
 public String getFirstName() { return firstName; }
 public void setFirstName(String value) { this.firstName=value; }
 @Column(name="last_name", columnDefinition="varchar(100)", length=100, nullable=true)
 private String lastName;
 public String getLastName() { return lastName; }
 public void setLastName(String value) { this.lastName=value; }
 @Column(name="gender", columnDefinition="varchar(80)", length=80, nullable=true)
 private String gender;
 public String getGender() { return gender; }
 public void setGender(String value) { this.gender=value; }
 @Column(name="age", columnDefinition="smallint", nullable=true)
 private Short age;
 public Short getAge() { return age; }
 public void setAge(Short value) { this.age=value; }
 @Column(name="completed_at", columnDefinition="timestamptz", nullable=true)
 private Instant completedAt;
 public Instant getCompletedAt() { return completedAt; }
 public void setCompletedAt(Instant value) { this.completedAt=value; }
 @Version @Column(name="version", columnDefinition="bigint", nullable=false)
 private Long version;
 public Long getVersion() { return version; }
 public void setVersion(Long value) { this.version=value; }
 @Column(name="created_at", columnDefinition="timestamptz", nullable=false)
 private Instant createdAt;
 public Instant getCreatedAt() { return createdAt; }
 public void setCreatedAt(Instant value) { this.createdAt=value; }
 @Column(name="updated_at", columnDefinition="timestamptz", nullable=false)
 private Instant updatedAt;
 public Instant getUpdatedAt() { return updatedAt; }
 public void setUpdatedAt(Instant value) { this.updatedAt=value; }


}
