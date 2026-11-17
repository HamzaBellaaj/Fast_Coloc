package tn.fastcoloc.media;

import java.io.IOException;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.auth.SupabaseGateway;
import tn.fastcoloc.onboarding.*;
import tn.fastcoloc.profiles.ProfileDataService;

@Service
public class PhotoService {
 private static final Logger log=LoggerFactory.getLogger(PhotoService.class);
 private final JdbcTemplate jdbc;private final OnboardingService onboarding;private final SupabaseGateway storage;private final ProfileDataService data;
 private final long maxBytes;private final int maxPhotos;
 public PhotoService(JdbcTemplate jdbc,OnboardingService onboarding,SupabaseGateway storage,ProfileDataService data,
   @Value("${fastcoloc.photos.max-bytes:5242880}")long maxBytes,@Value("${fastcoloc.photos.max-count:5}")int maxPhotos){this.jdbc=jdbc;this.onboarding=onboarding;this.storage=storage;this.data=data;this.maxBytes=maxBytes;this.maxPhotos=maxPhotos;}
 @Transactional public Map<String,Object> upload(UUID user,MultipartFile file,String caption){
  storage.requireServer();if(caption!=null&&caption.length()>500)throw invalid("Description trop longue.");
  if(file.isEmpty()||file.getSize()>maxBytes)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"PHOTO_SIZE","Photo vide ou trop volumineuse.");
  byte[] bytes;try{bytes=file.getBytes();}catch(IOException e){throw invalid("Photo illisible.");}
  String mime=mime(bytes);validateImage(bytes);onboarding.bindAndLock(user);
  if(jdbc.queryForObject("select count(*) from fastcoloc.profile_photos where user_id=?",Integer.class,user)>=maxPhotos)throw new ApiException(HttpStatus.CONFLICT,"PHOTO_QUOTA","Nombre maximal de photos atteint.");
  int order=jdbc.queryForObject("select coalesce(max(display_order),-1)+1 from fastcoloc.profile_photos where user_id=?",Integer.class,user);
  UUID id=UUID.randomUUID();String path=user+"/"+id+(mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":".jpg");
  // Storage is outside the DB transaction. Compensate failed/rolled-back metadata creation.
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{removeObjects(List.of(path));}catch(RuntimeException e){log.warn("Photo upload compensation requires retry for object {}",id);}}
  });
  storage.upload(path,bytes,mime);
  jdbc.update("insert into fastcoloc.profile_photos(id,user_id,object_path,caption,mime_type,byte_size,display_order,is_primary) values(?,?,?,?,?,?,?,?)",id,user,path,caption,mime,(long)bytes.length,order,order==0);
  return Map.of("id",id,"mimeType",mime,"byteSize",bytes.length,"displayOrder",order);
 }
 @Transactional public void update(UUID user,UUID photo,StepRequests.Caption r){
  onboarding.bindAndLock(user);String path=path(user,photo);
  if(Boolean.TRUE.equals(r.primary()))jdbc.update("update fastcoloc.profile_photos set is_primary=false where user_id=? and is_primary",user);
  if(r.displayOrder()!=null&&jdbc.queryForObject("select count(*) from fastcoloc.profile_photos where user_id=? and display_order=? and id<>?",Integer.class,user,r.displayOrder(),photo)>0)throw new ApiException(HttpStatus.CONFLICT,"PHOTO_ORDER","Rang déjà utilisé.");
  jdbc.update("update fastcoloc.profile_photos set caption=coalesce(?,caption),display_order=coalesce(?,display_order),is_primary=coalesce(?,is_primary) where id=? and user_id=?",r.caption(),r.displayOrder(),r.primary(),photo,user);
 }
 @Transactional public void delete(UUID user,UUID photo){
  onboarding.bindAndLock(user);String path=path(user,photo);
  removeObjects(List.of(path)); // idempotent remote removal; a failed SQL delete can be retried.
  jdbc.update("delete from fastcoloc.profile_photos where id=? and user_id=?",photo,user);
  jdbc.queryForObject("select fastcoloc.invalidate_photo_step()",Boolean.class);
 }
 @Transactional public Map<String,Object> signedUrl(UUID user,UUID photo){
  onboarding.bindAndLock(user);var result=storage.admin(HttpMethod.POST,"/storage/v1/object/sign/profile-photos/"+path(user,photo),Map.of("expiresIn",60));
  String url=result.path("signedURL").asText();if(url.isBlank())url=result.path("signedUrl").asText();
  if(url.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","URL signée indisponible.");
  return Map.of("url",url.startsWith("http")?url:storage.url()+"/storage/v1"+url,"expiresIn",60);
 }
 public void removeObjects(List<String> paths){storage.admin(HttpMethod.DELETE,"/storage/v1/object/profile-photos",Map.of("prefixes",paths));}
 private String path(UUID user,UUID photo){var rows=jdbc.queryForList("select object_path from fastcoloc.profile_photos where id=? and user_id=?",String.class,photo,user);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"PHOTO_NOT_FOUND","Photo introuvable.");return rows.getFirst();}
 public String mime(byte[] b){
  if(b.length>=8&&b[0]==(byte)0x89&&b[1]==0x50&&b[2]==0x4e&&b[3]==0x47&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";
  if(b.length>=3&&b[0]==(byte)0xff&&b[1]==(byte)0xd8&&b[2]==(byte)0xff)return "image/jpeg";
  if(b.length>=12&&new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
  throw invalid("Formats autorisés : JPEG, PNG et WebP.");
 }
 public void validateImage(byte[] bytes){
  try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))){
   var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid("Image non décodable.");
   var reader=readers.next();
   try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||(long)w*h>20000000L)throw invalid("Dimensions de photo non autorisées.");if(reader.read(0)==null)throw invalid("Image non décodable.");}
   finally{reader.dispose();}
  }catch(IOException|IllegalArgumentException e){throw invalid("Image endommagée ou non décodable.");}
 }
 private ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHOTO",message);}
}
