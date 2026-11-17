import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Operator-only bootstrap of the verified configured account. Dry run unless --apply is supplied. */
public class BootstrapAdmin {
 public static void main(String[] args)throws Exception{
  Properties cfg=new Properties();try(var r=Files.newBufferedReader(Path.of(".env.local"))){cfg.load(r);}
  String email=cfg.getProperty("FASTCOLOC_BOOTSTRAP_ADMIN_EMAIL",cfg.getProperty("FASTCOLOC_TEST_EMAIL"));
  if(email==null||email.isBlank())throw new IllegalStateException("Configure FASTCOLOC_BOOTSTRAP_ADMIN_EMAIL locally");
  String secret=cfg.getProperty("FASTCOLOC_SUPABASE_SECRET_KEY");
  var req=HttpRequest.newBuilder(URI.create("https://nklsbubnyznlfpxpxsrp.supabase.co/auth/v1/admin/users?per_page=1000")).header("apikey",secret).header("Authorization","Bearer "+secret).header("User-Agent","FastColocBackendOperator/1.0").GET().build();
  var response=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Auth operator lookup failed");
  JsonNode selected=null;for(JsonNode user:new ObjectMapper().readTree(response.body()).path("users"))if(email.equalsIgnoreCase(user.path("email").asText()))selected=user;
  if(selected==null||selected.path("email_confirmed_at").asText("").isBlank()||selected.path("is_anonymous").asBoolean(true))throw new IllegalStateException("Verified non-anonymous account required");
  UUID id=UUID.fromString(selected.path("id").asText());Properties credentials=new Properties();credentials.setProperty("user","postgres.nklsbubnyznlfpxpxsrp");credentials.setProperty("password",cfg.getProperty("FASTCOLOC_DB_ADMIN_PASSWORD"));
  try(var c=DriverManager.getConnection(cfg.getProperty("FASTCOLOC_JDBC_URL"),credentials)){
   c.setAutoCommit(false);
   try(var lock=c.createStatement()){lock.execute("select pg_advisory_xact_lock(hashtext('fastcoloc.admin.status'))");}
   String role,status;try(var p=c.prepareStatement("select app_role,status from fastcoloc.account_access where user_id=? for update")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Synchronize the confirmed profile first");role=r.getString(1);status=r.getString(2);}}
   if(!"ACTIVE".equals(status))throw new IllegalStateException("Blocked account cannot be bootstrapped");
   long admins;try(var s=c.createStatement();var r=s.executeQuery("select count(*) from fastcoloc.account_access where app_role='ADMIN' and status='ACTIVE'")){r.next();admins=r.getLong(1);}
   if(admins>0&&!"ADMIN".equals(role))throw new IllegalStateException("An active administrator already exists; use an explicit reviewed operation");
   boolean apply=Arrays.asList(args).contains("--apply");
   if(apply&&!"ADMIN".equals(role)){
    try(var p=c.prepareStatement("update fastcoloc.account_access set app_role='ADMIN',updated_at=now() where user_id=?")){p.setObject(1,id);p.executeUpdate();}
    try(var p=c.prepareStatement("insert into fastcoloc.account_access_events(subject_id,actor_id,event_type,reason) values(?,null,'ROLE_CHANGE','Initial administrator bootstrap by database operator after confirmed email ownership')")){p.setObject(1,id);p.executeUpdate();}
    c.commit();System.out.println("INITIAL_ADMIN_BOOTSTRAP=APPLIED_AND_AUDITED");
   }else{c.rollback();System.out.println("INITIAL_ADMIN_BOOTSTRAP="+("ADMIN".equals(role)?"ALREADY_ADMIN":"READY_DRY_RUN_ONLY"));}
  }
 }
}
