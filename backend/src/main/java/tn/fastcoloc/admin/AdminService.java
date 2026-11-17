package tn.fastcoloc.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.auth.SupabaseGateway;
import tn.fastcoloc.security.DatabaseIdentity;
import tn.fastcoloc.profiles.ProfileDataService;

@Service
public class AdminService {
 private final JdbcTemplate jdbc;private final DatabaseIdentity identity;private final ProfileDataService data;private final SupabaseGateway api;private final TransactionTemplate tx;
 public AdminService(JdbcTemplate jdbc,DatabaseIdentity identity,ProfileDataService data,SupabaseGateway api,PlatformTransactionManager manager){this.jdbc=jdbc;this.identity=identity;this.data=data;this.api=api;this.tx=new TransactionTemplate(manager);}
 @Transactional(readOnly=true) public List<Map<String,Object>> users(UUID actor,int limit,int offset,String search,String status){identity.bind(actor);return data.rows("select * from fastcoloc.admin_users(?,?,?,?)",limit,offset,search,status);}
 @Transactional public String status(UUID actor,UUID target,AdminRequests.Status r){identity.bind(actor);return jdbc.queryForObject("select fastcoloc.admin_change_status(?,?,?)::text",String.class,target,r.status(),r.reason());}
 @Transactional public String contacts(UUID actor,UUID target,AdminRequests.Contacts r){identity.bind(actor);var c=r.contacts();return jdbc.queryForObject("select fastcoloc.admin_contacts(?,?,?,?,?,?,?,?)::text",String.class,target,c.mobilePhone(),c.residentialPhone(),c.callAvailability(),c.msnContact(),c.skypeContact(),c.websiteUrl(),r.reason());}
 @Transactional(readOnly=true) public String contacts(UUID actor,UUID target){identity.bind(actor);return jdbc.queryForObject("select fastcoloc.admin_read_contacts(?)::text",String.class,target);}
 /** Block first and commit. External cleanup is resumable, never presented as a distributed transaction. */
 public void delete(UUID actor,UUID target,AdminRequests.Deletion r){
  api.requireServer();
  tx.executeWithoutResult(status->{identity.bind(actor);jdbc.queryForObject("select fastcoloc.admin_change_status(?,'BLOCKED',?)::text",String.class,target,r.reason());jdbc.queryForObject("select fastcoloc.admin_delete_attempt(?,?)",Boolean.class,target,r.reason());});
  // Every object created by this backend is flat under the validated target UUID.
  for(int batch=0;batch<100;batch++){
   JsonNode files=api.admin(HttpMethod.POST,"/storage/v1/object/list/profile-photos",Map.of("prefix",target+"/","limit",100,"offset",0));
   if(!files.isArray())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DELETE_RETRY_REQUIRED","Nettoyage Storage à reprendre, compte bloqué.");
   if(files.isEmpty())break;
   List<String> paths=new ArrayList<>();for(JsonNode f:files){if(f.path("name").asText().contains("/"))throw new ApiException(HttpStatus.CONFLICT,"DELETE_REVIEW_REQUIRED","Chemin Storage inattendu.");paths.add(target+"/"+f.path("name").asText());}
   api.admin(HttpMethod.DELETE,"/storage/v1/object/profile-photos",Map.of("prefixes",paths));
   if(batch==99)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DELETE_RETRY_REQUIRED","Nettoyage Storage à reprendre, compte bloqué.");
  }
  cleanListingPhotos(target);
  try{api.admin(HttpMethod.DELETE,"/auth/v1/admin/users/"+target,null);}catch(ApiException e){if(e.status()!=HttpStatus.NOT_FOUND)throw e;}
  // Auth deletion cascades through the existing profiles FK; audit actor/subject use SET NULL.
 }
 /** Preserve the existing account-deletion guarantee for the new private photo bucket too. */
 private void cleanListingPhotos(UUID target){
  for(int batch=0;batch<100;batch++){
   JsonNode folders=api.admin(HttpMethod.POST,"/storage/v1/object/list/listing-photos",Map.of("prefix",target+"/","limit",100,"offset",0));
   if(!folders.isArray())throw cleanupRetry();if(folders.isEmpty())return;
   for(JsonNode folder:folders){
    String name=folder.path("name").asText();
    try{UUID.fromString(name);}catch(IllegalArgumentException e){throw cleanupRetry();}
    String prefix=target+"/"+name+"/";
    for(int filesBatch=0;filesBatch<100;filesBatch++){
     var files=api.admin(HttpMethod.POST,"/storage/v1/object/list/listing-photos",Map.of("prefix",prefix,"limit",100,"offset",0));
     if(!files.isArray())throw cleanupRetry();if(files.isEmpty())break;
     List<String> paths=new ArrayList<>();for(var file:files){String filename=file.path("name").asText();
      if(!filename.matches("[0-9a-f-]{36}\\.(png|jpg|webp)"))throw cleanupRetry();paths.add(prefix+filename);}
     api.admin(HttpMethod.DELETE,"/storage/v1/object/listing-photos",Map.of("prefixes",paths));if(filesBatch==99)throw cleanupRetry();
    }
   }
  }
  throw cleanupRetry();
 }
 private ApiException cleanupRetry(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DELETE_RETRY_REQUIRED","Nettoyage des photos d'annonces à reprendre, compte bloqué.");}
}
