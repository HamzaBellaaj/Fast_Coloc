package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="personal_details", schema="fastcoloc") 
public class PersonalDetails {
 public PersonalDetails() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY) @Column(name="main_activities", columnDefinition="text[]", nullable=false)
 private String[] mainActivities;
 public String[] getMainActivities() { return mainActivities; }
 public void setMainActivities(String[] value) { this.mainActivities=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY) @Column(name="hobbies", columnDefinition="text[]", nullable=false)
 private String[] hobbies;
 public String[] getHobbies() { return hobbies; }
 public void setHobbies(String[] value) { this.hobbies=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR) @Column(name="nationality_code", columnDefinition="char(2)", length=2, nullable=true)
 private String nationalityCode;
 public String getNationalityCode() { return nationalityCode; }
 public void setNationalityCode(String value) { this.nationalityCode=value; }
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY) @Column(name="spoken_languages", columnDefinition="text[]", nullable=false)
 private String[] spokenLanguages;
 public String[] getSpokenLanguages() { return spokenLanguages; }
 public void setSpokenLanguages(String[] value) { this.spokenLanguages=value; }
 @Column(name="religion", columnDefinition="varchar(120)", length=120, nullable=true)
 private String religion;
 public String getReligion() { return religion; }
 public void setReligion(String value) { this.religion=value; }
 @Column(name="smoking", columnDefinition="boolean", nullable=true)
 private Boolean smoking;
 public Boolean getSmoking() { return smoking; }
 public void setSmoking(Boolean value) { this.smoking=value; }
 @Column(name="cleanliness", columnDefinition="varchar(120)", length=120, nullable=true)
 private String cleanliness;
 public String getCleanliness() { return cleanliness; }
 public void setCleanliness(String value) { this.cleanliness=value; }
 @Column(name="biography", columnDefinition="varchar(2000)", length=2000, nullable=true)
 private String biography;
 public String getBiography() { return biography; }
 public void setBiography(String value) { this.biography=value; }


}
