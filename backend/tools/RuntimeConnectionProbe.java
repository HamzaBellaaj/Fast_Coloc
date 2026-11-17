import java.nio.file.*;
import java.io.*;
import java.sql.*;
import java.util.*;

/** Local runtime credential check; never prints credentials or application rows. */
public class RuntimeConnectionProbe {
 public static void main(String[] args) throws Exception {
  Properties config=new Properties();
  try(var reader=Files.newBufferedReader(Path.of(".env.local"))) { config.load(reader); }
  Properties credentials=new Properties();
  credentials.setProperty("user",config.getProperty("FASTCOLOC_DB_USERNAME"));
  credentials.setProperty("password",config.getProperty("FASTCOLOC_DB_PASSWORD"));
  credentials.setProperty("connectTimeout","10"); credentials.setProperty("socketTimeout","15");
  try(var c=DriverManager.getConnection(config.getProperty("FASTCOLOC_JDBC_URL"),credentials);var s=c.createStatement()) {
   c.setReadOnly(true);c.setAutoCommit(false);
   try(var r=s.executeQuery("select current_user,current_setting('transaction_read_only'),(select count(*) from fastcoloc.profiles)")) {
    r.next();System.out.println("runtime_role="+r.getString(1)+" read_only="+r.getString(2)+" visible_without_context="+r.getLong(3));
    if(!"fastcoloc_backend".equals(r.getString(1)))throw new IllegalStateException("Unexpected runtime identity");
   }
   c.rollback();System.out.println("RUNTIME_JDBC_TLS_VERIFY_FULL=SUCCESS");
  } catch(SQLException e) {System.out.println("RUNTIME_JDBC=FAILED SQLSTATE="+e.getSQLState());System.exit(1);}
 }
}
