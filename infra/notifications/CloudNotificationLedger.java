import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Provisions only the cloud mailer's ledger and login. Never prints secrets. */
class CloudNotificationLedger {
 static Map<String,String> env(Path path)throws Exception{
  var values=new HashMap<String,String>();
  for(String line:Files.readAllLines(path)){if(line.matches("^[A-Z0-9_]+=.*")){int i=line.indexOf('=');values.put(line.substring(0,i),line.substring(i+1).trim());}}
  return values;
 }
 public static void main(String[] args){
  try{run(args.length==0?"provision":args[0]);}catch(Exception e){System.err.println("Cloud ledger setup failed ("+e.getClass().getSimpleName()+"). No credentials were printed.");System.exit(1);}
 }
 static void run(String mode)throws Exception{
  var root=Path.of("").toAbsolutePath();var backend=env(root.resolve("backend/.env.local"));var cloud=env(root.resolve("infra/notifications/.env.cloud.local"));
  String jdbc=backend.get("FASTCOLOC_JDBC_URL"),user=backend.get("FASTCOLOC_DB_USERNAME"),adminPassword=backend.get("FASTCOLOC_DB_ADMIN_PASSWORD");
  String suffix=user.substring(user.indexOf('.'));String password=cloud.get("N8N_LEDGER_PASSWORD");
  if(password==null||password.length()<32||adminPassword==null)throw new IllegalStateException("Missing local credentials");
  // sslrootcert paths in the application URL are relative to the backend directory.
  jdbc=jdbc.replace("sslrootcert=config/","sslrootcert="+root.resolve("backend/config").toString().replace('\\','/')+"/");
  if(mode.equals("inspect-test")){
   try(var connection=DriverManager.getConnection(jdbc,"fastcoloc_mailer"+suffix,password);var p=connection.prepareStatement("select status,provider_message_id is not null as accepted from fastcoloc_notifications.notification_deliveries where event_id=?::uuid")){
    p.setString(1,cloud.get("N8N_LAST_TEST_EVENT_ID"));try(var r=p.executeQuery()){if(r.next())System.out.println("Test ledger status: "+r.getString(1)+"; Gmail acceptance recorded: "+r.getBoolean(2));else System.out.println("Test event not found in ledger.");}
   }return;
  }
  try(var connection=DriverManager.getConnection(jdbc,"postgres"+suffix,adminPassword);var s=connection.createStatement()){
   try(var r=s.executeQuery("select to_regclass('fastcoloc_notifications.notification_deliveries') is not null")){r.next();if(!r.getBoolean(1)){s.execute(Files.readString(root.resolve("backend/supabase/migrations/20261123215000_cloud_notification_ledger.sql")));System.out.println("PASS: separate cloud ledger created.");}else System.out.println("PASS: existing cloud ledger preserved.");}
   // Password is escaped by PostgreSQL itself, never interpolated from shell text.
   try(var p=connection.prepareStatement("select format('ALTER ROLE fastcoloc_mailer LOGIN PASSWORD %L', ?)")){p.setString(1,password);try(var r=p.executeQuery()){r.next();s.execute(r.getString(1));}}
  }
  try(var connection=DriverManager.getConnection(jdbc,"fastcoloc_mailer"+suffix,password);var s=connection.createStatement()){
   try(var r=s.executeQuery("select count(*) from fastcoloc_notifications.notification_deliveries")){r.next();}
   for(String table:List.of("fastcoloc.messages","fastcoloc.profiles","auth.users")){
    try{s.executeQuery("select * from "+table+" limit 0");throw new IllegalStateException("Unexpected application table access");}catch(SQLException expected){if(!"42501".equals(expected.getSQLState()))throw expected;}
   }
   System.out.println("PASS: mailer login reaches ledger; private messages, profiles and Auth users denied.");
  }
 }
}
