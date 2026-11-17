package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="contact_details", schema="fastcoloc") 
public class ContactDetails {
 public ContactDetails() {}
 @Id @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Column(name="mobile_phone", columnDefinition="varchar(30)", length=30, nullable=true)
 private String mobilePhone;
 public String getMobilePhone() { return mobilePhone; }
 public void setMobilePhone(String value) { this.mobilePhone=value; }
 @Column(name="residential_phone", columnDefinition="varchar(30)", length=30, nullable=true)
 private String residentialPhone;
 public String getResidentialPhone() { return residentialPhone; }
 public void setResidentialPhone(String value) { this.residentialPhone=value; }
 @Column(name="call_availability", columnDefinition="varchar(250)", length=250, nullable=true)
 private String callAvailability;
 public String getCallAvailability() { return callAvailability; }
 public void setCallAvailability(String value) { this.callAvailability=value; }
 @Column(name="msn_contact", columnDefinition="varchar(250)", length=250, nullable=true)
 private String msnContact;
 public String getMsnContact() { return msnContact; }
 public void setMsnContact(String value) { this.msnContact=value; }
 @Column(name="skype_contact", columnDefinition="varchar(250)", length=250, nullable=true)
 private String skypeContact;
 public String getSkypeContact() { return skypeContact; }
 public void setSkypeContact(String value) { this.skypeContact=value; }
 @Column(name="website_url", columnDefinition="varchar(500)", length=500, nullable=true)
 private String websiteUrl;
 public String getWebsiteUrl() { return websiteUrl; }
 public void setWebsiteUrl(String value) { this.websiteUrl=value; }


}
