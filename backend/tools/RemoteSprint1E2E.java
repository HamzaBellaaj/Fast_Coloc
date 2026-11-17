import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Explicit remote acceptance run. Creates only tagged disposable Auth users and removes them in finally. */
public class RemoteSprint1E2E {
 static final ObjectMapper JSON=new ObjectMapper();static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 static final Properties CONFIG=new Properties();static final List<String> IDS=new ArrayList<>();static final List<String> PASSES=new ArrayList<>();
 static String root="http://localhost:8001",supabase="https://nklsbubnyznlfpxpxsrp.supabase.co",secret;static final String RUN=UUID.randomUUID().toString();
 record Reply(int status,JsonNode body){}
 static Reply call(String base,String method,String path,Object body,String bearer,boolean admin)throws Exception{
  var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40)).header("User-Agent","FastColocBackendE2E/1.0").header("Content-Type","application/json");
  if(admin){request.header("apikey",secret);request.header("Authorization","Bearer "+secret);}else if(bearer!=null)request.header("Authorization","Bearer "+bearer);
  request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body)));
  var response=HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
  JsonNode parsed;try{parsed=response.body().isBlank()?JSON.nullNode():JSON.readTree(response.body());}catch(Exception e){parsed=JSON.createObjectNode();}
  return new Reply(response.statusCode(),parsed);
 }
 static Reply api(String method,String path,Object body,String token)throws Exception{return call(root,method,path,body,token,false);}
 static JsonNode admin(String method,String path,Object body)throws Exception{var r=call(supabase,method,path,body,null,true);if(r.status<200||r.status>=300)throw new IllegalStateException("upstream_admin_http_"+r.status);return r.body;}
 static void pass(String label,boolean result){if(!result)throw new IllegalStateException("assertion_"+label);PASSES.add(label);System.out.println("PASS "+label);}
 static Reply expect(String label,int status,String method,String path,Object body,String token)throws Exception{var r=api(method,path,body,token);pass(label+"_http_"+status,r.status==status);return r;}
 static String password(){return "Fc!aA9"+UUID.randomUUID().toString().replace("-","");}
 static String create(String suffix,String password)throws Exception{var u=admin("POST","/auth/v1/admin/users",Map.of("email","fastcoloc-e2e-"+RUN+"-"+suffix+"@example.com","password",password,"email_confirm",true,"user_metadata",Map.of("fastcoloc_e2e_run",RUN)));String id=u.path("id").asText();UUID.fromString(id);IDS.add(id);return id;}
 static Connection connection(boolean operator)throws Exception{var p=new Properties();p.setProperty("user",operator?"postgres.nklsbubnyznlfpxpxsrp":CONFIG.getProperty("FASTCOLOC_DB_USERNAME"));p.setProperty("password",CONFIG.getProperty(operator?"FASTCOLOC_DB_ADMIN_PASSWORD":"FASTCOLOC_DB_PASSWORD"));p.setProperty("connectTimeout","10");return DriverManager.getConnection(CONFIG.getProperty("FASTCOLOC_JDBC_URL"),p);}
 static void context(Connection c,String id)throws Exception{try(var p=c.prepareStatement("select set_config('fastcoloc.user_id',?,true)")){p.setString(1,id);p.execute();}}
 static long visible(String id)throws Exception{try(var c=connection(false);var s=c.createStatement()){c.setAutoCommit(false);if(id!=null)context(c,id);try(var r=s.executeQuery("select count(*) from fastcoloc.profiles")){r.next();long n=r.getLong(1);c.rollback();return n;}}}
 static String upload(String token)throws Exception{
  var pngOut=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",pngOut);byte[] png=pngOut.toByteArray();String boundary="FastColoc"+RUN;
  var body=new java.io.ByteArrayOutputStream();body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"e2e.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(png);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
  var req=HttpRequest.newBuilder(URI.create(root+"/api/v1/profile-photos")).timeout(Duration.ofSeconds(40)).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
  var response=HTTP.send(req,HttpResponse.BodyHandlers.ofString());pass("private_photo_upload",response.statusCode()==201);return JSON.readTree(response.body()).path("id").asText();
 }
 static void cleanup(String id)throws Exception{
  var files=admin("POST","/storage/v1/object/list/profile-photos",Map.of("prefix",id+"/","limit",100,"offset",0));
  List<String> paths=new ArrayList<>();if(files.isArray())for(var f:files)paths.add(id+"/"+f.path("name").asText());
  if(!paths.isEmpty())admin("DELETE","/storage/v1/object/profile-photos",Map.of("prefixes",paths));
  var r=call(supabase,"DELETE","/auth/v1/admin/users/"+id,null,null,true);if(r.status!=200&&r.status!=204&&r.status!=404)throw new IllegalStateException("cleanup_http_"+r.status);
 }
 public static void main(String[] args)throws Exception{
  try(var reader=Files.newBufferedReader(Path.of(".env.local"))){CONFIG.load(reader);}secret=CONFIG.getProperty("FASTCOLOC_SUPABASE_SECRET_KEY");boolean success=false;String failure="";
  try{
   expect("health",200,"GET","/api/v1/health",null,null);expect("missing_token",401,"GET","/api/v1/users/me",null,null);
   var cfg=expect("auth_configuration",200,"GET","/api/v1/auth/config",null,null);pass("email_confirmation_required",cfg.body.path("confirmationRequired").asBoolean());
   String pa=password(),pb=password();String a=create("a",pa),b=create("b",pb);pass("disposable_auth_users_created",IDS.size()==2);
   String emailA="fastcoloc-e2e-"+RUN+"-a@example.com",emailB="fastcoloc-e2e-"+RUN+"-b@example.com";
   var la=expect("email_password_login",200,"POST","/api/v1/auth/login",Map.of("email",emailA,"password",pa),null);String ta=la.body.path("access_token").asText(),refresh=la.body.path("refresh_token").asText();
   String tb=expect("second_login",200,"POST","/api/v1/auth/login",Map.of("email",emailB,"password",pb),null).body.path("access_token").asText();
   expect("pending_profile_denied",403,"GET","/api/v1/users/me",null,ta);
   expect("profile_sync",200,"POST","/api/v1/users/sync",Map.of(),ta);expect("profile_sync_idempotent",200,"POST","/api/v1/users/sync",Map.of(),ta);expect("second_sync",200,"POST","/api/v1/users/sync",Map.of(),tb);
   pass("jdbc_no_context_denied",visible(null)==0);pass("jdbc_own_context_only",visible(a)==1&&visible(b)==1);
   try(var c=connection(false);var s=c.createStatement()){c.setAutoCommit(false);context(c,a);try{s.executeUpdate("update fastcoloc.account_access set app_role='ADMIN'");throw new IllegalStateException("runtime_escalation_allowed");}catch(SQLException e){pass("jdbc_role_escalation_denied","42501".equals(e.getSQLState()));}finally{c.rollback();}}
   expect("step1",200,"PUT","/api/v1/onboarding/steps/1",Map.of("firstName","Sprint1","lastName","E2E","age",25),ta);
   var govs=expect("geography_governorates",200,"GET","/api/v1/geography/governorates?countryCode=TN",null,ta).body;pass("geography_populated",govs.size()==24);
   long governorate=0,locality=0;for(var g:govs){var locations=api("GET","/api/v1/geography/localities?governorateId="+g.path("id").asLong(),null,ta);if(locations.status==200&&locations.body.isArray()&&!locations.body.isEmpty()){governorate=g.path("id").asLong();locality=locations.body.get(0).path("id").asLong();break;}}
   pass("locality_found",locality>0);
   expect("step2",200,"PUT","/api/v1/onboarding/steps/2",Map.of("countryCode","TN","governorateId",governorate,"localityId",locality,"street","Rue test E2E","postalCode","1000"),ta);
   expect("step3",200,"PUT","/api/v1/onboarding/steps/3",Map.of("mainActivities",List.of("test"),"hobbies",List.of(),"spokenLanguages",List.of("fr","ar")),ta);
   expect("step4",200,"PUT","/api/v1/onboarding/steps/4",Map.of("mobilePhone","12345678"),ta);
   expect("incomplete_completion_denied",409,"POST","/api/v1/onboarding/complete",null,ta);
   String photo=upload(ta);expect("other_photo_denied",404,"GET","/api/v1/profile-photos/"+photo+"/signed-url",null,tb);
   var signed=expect("private_signed_url",200,"GET","/api/v1/profile-photos/"+photo+"/signed-url",null,ta);
   var image=HTTP.send(HttpRequest.newBuilder(URI.create(signed.body.path("url").asText())).GET().build(),HttpResponse.BodyHandlers.ofByteArray());pass("signed_photo_download",image.statusCode()==200);
   var publicImage=HTTP.send(HttpRequest.newBuilder(URI.create(supabase+"/storage/v1/object/public/profile-photos/"+a+"/"+photo+".png")).GET().build(),HttpResponse.BodyHandlers.discarding());pass("unsigned_public_photo_denied",publicImage.statusCode()!=200);
   expect("step5",200,"PUT","/api/v1/onboarding/steps/5",Map.of("photoIds",List.of(photo)),ta);
   var done=expect("onboarding_completion",200,"POST","/api/v1/onboarding/complete",null,ta);pass("completed_at_persisted",done.body.path("profile").path("completedAt").isTextual());
   expect("preferences",200,"PUT","/api/v1/users/me/preferences",Map.of("minBudget",100,"maxBudget",400,"localityIds",List.of(locality)),ta);
   expect("invalid_budget",400,"PUT","/api/v1/users/me/preferences",Map.of("minBudget",500,"maxBudget",100,"localityIds",List.of(locality)),ta);
   expect("user_admin_route_denied",403,"GET","/api/v1/admin/users",null,ta);
   try(var c=connection(true);var p=c.prepareStatement("update fastcoloc.account_access set app_role='ADMIN' where user_id=?")){p.setObject(1,UUID.fromString(a));pass("operator_bootstrap_temporary_admin",p.executeUpdate()==1);}
   expect("admin_list",200,"GET","/api/v1/admin/users?limit=20",null,ta);
   expect("admin_block",200,"PATCH","/api/v1/admin/users/"+b+"/status",Map.of("status","BLOCKED","reason","Temporary E2E block"),ta);
   expect("blocked_read_denied",403,"GET","/api/v1/users/me",null,tb);expect("blocked_sync_denied",403,"POST","/api/v1/users/sync",Map.of(),tb);pass("blocked_sql_reads_denied",visible(b)==0);
   expect("admin_contacts",200,"PATCH","/api/v1/admin/users/"+b+"/contacts",Map.of("contacts",Map.of("mobilePhone","87654321"),"reason","E2E correction"),ta);
   expect("admin_unblock",200,"PATCH","/api/v1/admin/users/"+b+"/status",Map.of("status","ACTIVE","reason","End of temporary E2E block"),ta);expect("unblocked_read",200,"GET","/api/v1/users/me",null,tb);
   expect("admin_delete",204,"DELETE","/api/v1/admin/users/"+b,Map.of("reason","Disposable E2E user cleanup"),ta);
   var renewed=expect("refresh_session",200,"POST","/api/v1/auth/refresh",Map.of("refreshToken",refresh),null);ta=renewed.body.path("access_token").asText();
   var recovery=admin("POST","/auth/v1/admin/generate_link",Map.of("type","recovery","email",emailA));String hash=recovery.path("hashed_token").asText();if(hash.isBlank())hash=recovery.path("properties").path("hashed_token").asText();pass("recovery_link_generated_for_fixture",!hash.isBlank());
   var verified=expect("recovery_token_verified",200,"POST","/api/v1/auth/verify",Map.of("tokenHash",hash,"type","recovery"),null);String recovered=verified.body.path("access_token").asText();String next=password();
   expect("password_reset",204,"PUT","/api/v1/auth/password",Map.of("password",next),recovered);
   pass("old_password_rejected",api("POST","/api/v1/auth/login",Map.of("email",emailA,"password",pa),null).status==401);
   var newLogin=expect("new_password_login",200,"POST","/api/v1/auth/login",Map.of("email",emailA,"password",next),null);String finalToken=newLogin.body.path("access_token").asText(),finalRefresh=newLogin.body.path("refresh_token").asText();
   expect("logout",204,"POST","/api/v1/auth/logout?scope=global",null,finalToken);pass("refresh_revoked_after_logout",api("POST","/api/v1/auth/refresh",Map.of("refreshToken",finalRefresh),null).status!=200);
   expect("photo_delete_after_relogin",204,"DELETE","/api/v1/profile-photos/"+photo,null,expect("login_for_final_cleanup",200,"POST","/api/v1/auth/login",Map.of("email",emailA,"password",next),null).body.path("access_token").asText());
   success=true;
  }catch(Exception e){failure=e instanceof IllegalStateException?e.getMessage():e.getClass().getSimpleName();System.out.println("REMOTE_E2E_FAILED="+failure);}
  finally{for(String id:IDS)try{cleanup(id);}catch(Exception e){success=false;failure="cleanup_requires_retry";System.out.println("CLEANUP_REQUIRES_RETRY");}}
  Files.writeString(Path.of("target/remote-e2e-report.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("at",Instant.now().toString(),"success",success,"passed",PASSES,"failure",failure,"temporaryAccounts",IDS.size())));
  System.out.println("REMOTE_E2E_SUCCESS="+success+" CHECKS="+PASSES.size());if(!success)System.exit(1);
 }
}
