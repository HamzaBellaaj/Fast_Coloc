package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="governorates", schema="fastcoloc") 
public class Governorate {
 public Governorate() {}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", columnDefinition="bigint", nullable=false)
 private Long id;
 public Long getId() { return id; }
 public void setId(Long value) { this.id=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR) @Column(name="country_code", columnDefinition="char(2)", length=2, nullable=false)
 private String countryCode;
 public String getCountryCode() { return countryCode; }
 public void setCountryCode(String value) { this.countryCode=value; }
 @Column(name="name", columnDefinition="text", nullable=false)
 private String name;
 public String getName() { return name; }
 public void setName(String value) { this.name=value; }


}
