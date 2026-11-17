package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="localities", schema="fastcoloc") 
public class Locality {
 public Locality() {}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", columnDefinition="bigint", nullable=false)
 private Long id;
 public Long getId() { return id; }
 public void setId(Long value) { this.id=value; }
 @Column(name="governorate_id", columnDefinition="bigint", nullable=false)
 private Long governorateId;
 public Long getGovernorateId() { return governorateId; }
 public void setGovernorateId(Long value) { this.governorateId=value; }
 @Column(name="name", columnDefinition="text", nullable=false)
 private String name;
 public String getName() { return name; }
 public void setName(String value) { this.name=value; }


}
