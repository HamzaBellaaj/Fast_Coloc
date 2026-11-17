package tn.fastcoloc.entity;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="profile_photos", schema="fastcoloc") 
public class ProfilePhoto {
 public ProfilePhoto() {}
 @Id @Column(name="id", columnDefinition="uuid", nullable=false)
 private UUID id;
 public UUID getId() { return id; }
 public void setId(UUID value) { this.id=value; }
 @Column(name="user_id", columnDefinition="uuid", nullable=false)
 private UUID userId;
 public UUID getUserId() { return userId; }
 public void setUserId(UUID value) { this.userId=value; }
 @Column(name="bucket", columnDefinition="text", nullable=false)
 private String bucket;
 public String getBucket() { return bucket; }
 public void setBucket(String value) { this.bucket=value; }
 @Column(name="object_path", columnDefinition="text", nullable=false)
 private String objectPath;
 public String getObjectPath() { return objectPath; }
 public void setObjectPath(String value) { this.objectPath=value; }
 @Column(name="caption", columnDefinition="varchar(500)", length=500, nullable=true)
 private String caption;
 public String getCaption() { return caption; }
 public void setCaption(String value) { this.caption=value; }
 @Column(name="mime_type", columnDefinition="text", nullable=false)
 private String mimeType;
 public String getMimeType() { return mimeType; }
 public void setMimeType(String value) { this.mimeType=value; }
 @Column(name="byte_size", columnDefinition="bigint", nullable=false)
 private Long byteSize;
 public Long getByteSize() { return byteSize; }
 public void setByteSize(Long value) { this.byteSize=value; }
 @Column(name="display_order", columnDefinition="integer", nullable=false)
 private Integer displayOrder;
 public Integer getDisplayOrder() { return displayOrder; }
 public void setDisplayOrder(Integer value) { this.displayOrder=value; }
 @Column(name="is_primary", columnDefinition="boolean", nullable=false)
 private Boolean isPrimary;
 public Boolean getIsPrimary() { return isPrimary; }
 public void setIsPrimary(Boolean value) { this.isPrimary=value; }
 @Column(name="created_at", columnDefinition="timestamptz", nullable=false)
 private Instant createdAt;
 public Instant getCreatedAt() { return createdAt; }
 public void setCreatedAt(Instant value) { this.createdAt=value; }


}
