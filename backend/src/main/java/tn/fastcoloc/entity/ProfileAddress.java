package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="profile_addresses", schema="fastcoloc") 
public class ProfileAddress {
 public ProfileAddress() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR) @Column(name="country_code", columnDefinition="char(2)", length=2, nullable=true)
 private String countryCode;
 public String getCountryCode() { return countryCode; }
 public void setCountryCode(String value) { this.countryCode=value; }
 @Column(name="locality_id", columnDefinition="bigint", nullable=true)
 private Long localityId;
 public Long getLocalityId() { return localityId; }
 public void setLocalityId(Long value) { this.localityId=value; }
 @Column(name="street", columnDefinition="varchar(250)", length=250, nullable=true)
 private String street;
 public String getStreet() { return street; }
 public void setStreet(String value) { this.street=value; }
 @Column(name="postal_code", columnDefinition="varchar(20)", length=20, nullable=true)
 private String postalCode;
 public String getPostalCode() { return postalCode; }
 public void setPostalCode(String value) { this.postalCode=value; }


}
