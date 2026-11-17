package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="countries", schema="fastcoloc") 
public class Country {
 public Country() {}
 @Id @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR) @Column(name="code", columnDefinition="char(2)", length=2, nullable=false)
 private String code;
 public String getCode() { return code; }
 public void setCode(String value) { this.code=value; }
 @Column(name="name", columnDefinition="text", nullable=false)
 private String name;
 public String getName() { return name; }
 public void setName(String value) { this.name=value; }


}
