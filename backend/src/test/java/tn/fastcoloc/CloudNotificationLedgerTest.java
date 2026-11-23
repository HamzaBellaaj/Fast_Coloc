package tn.fastcoloc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL permissions and concurrent claims using the cloud workflow's actual queries. */
class CloudNotificationLedgerTest {
 static EmbeddedPostgres pg;
 static Map<String,String> queries=new HashMap<>();
 @BeforeAll static void setup()throws Exception{
  pg=EmbeddedPostgres.builder().setPort(0).start();
  try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
   s.execute("create role anon;create role authenticated;create role fastcoloc_backend;create schema fastcoloc;create schema auth;create table fastcoloc.messages(id int);create table fastcoloc.profiles(id int);create table auth.users(id int);revoke create on schema public from public");
   s.execute(Files.readString(Path.of("supabase/migrations/20261123215000_cloud_notification_ledger.sql")));
   s.execute("alter role fastcoloc_mailer login");
  }
  var workflow=new ObjectMapper().readTree(Files.readString(Path.of("../infra/notifications/workflow.cloud.json")));
  for(var node:workflow.path("nodes"))if(node.path("type").asText().equals("n8n-nodes-base.postgres"))queries.put(node.path("name").asText(),node.path("parameters").path("query").asText());
 }
 @AfterAll static void close()throws Exception{if(pg!=null)pg.close();}
 static List<Map<String,String>> query(String sql,Object...values)throws Exception{
  var indexes=new ArrayList<Integer>();var matcher=Pattern.compile("\\$(\\d+)").matcher(sql);var replaced=new StringBuilder();
  while(matcher.find()){indexes.add(Integer.parseInt(matcher.group(1))-1);matcher.appendReplacement(replaced,"?");}matcher.appendTail(replaced);
  try(var c=DriverManager.getConnection(pg.getJdbcUrl("fastcoloc_mailer","postgres"));var p=c.prepareStatement(replaced.toString())){
   for(int i=0;i<indexes.size();i++)p.setObject(i+1,values[indexes.get(i)]);
   try(var r=p.executeQuery()){var rows=new ArrayList<Map<String,String>>();while(r.next()){var row=new HashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));rows.add(row);}return rows;}
  }
 }
 static Map<String,String> claim(UUID id)throws Exception{return query(queries.get("Claim delivery"),id).getFirst();}
 static void admin(String sql)throws Exception{try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute(sql);}}
 @Test void roleCannotAccessApplicationDataOrChangeSchema()throws Exception{
  try(var c=DriverManager.getConnection(pg.getJdbcUrl("fastcoloc_mailer","postgres"));var s=c.createStatement()){
   for(String sql:List.of("select * from fastcoloc.messages","select * from fastcoloc.profiles","select * from auth.users","create table fastcoloc_notifications.forbidden(id int)","set role fastcoloc_backend")){
    assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("42501");
   }
  }
  try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()){
   s.execute("set role fastcoloc_backend");assertThatThrownBy(()->s.executeQuery("select * from fastcoloc_notifications.notification_deliveries")).isInstanceOf(SQLException.class);
  }
 }
 @Test void concurrentClaimsAllowOnlyOneSender()throws Exception{
  UUID id=UUID.randomUUID();var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<String> task=()->{start.await();return claim(id).get("action");};var first=pool.submit(task);var second=pool.submit(task);start.countDown();
   assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("send","busy");
  }
 }
 @Test void acknowledgedGmailEventIsNotSentAgain()throws Exception{
  UUID id=UUID.randomUUID();var job=claim(id);assertThat(job.get("action")).isEqualTo("send");
  assertThat(query(queries.get("Record accepted email"),id,job.get("lease_token"),null)).isEmpty();
  assertThat(query(queries.get("Record accepted email"),id,job.get("lease_token"),"gmail-fixture-id")).hasSize(1);
  assertThat(claim(id).get("action")).isEqualTo("duplicate");
 }
 @Test void failedAndExpiredLeasesRetryWithoutStaleAcknowledgement()throws Exception{
  UUID id=UUID.randomUUID();var initial=claim(id);
  assertThat(query(queries.get("Record Gmail failure"),id,initial.get("lease_token"))).hasSize(1);
  var retry=claim(id);assertThat(retry.get("action")).isEqualTo("send");assertThat(retry.get("lease_token")).isNotEqualTo(initial.get("lease_token"));
  assertThat(query(queries.get("Record accepted email"),id,initial.get("lease_token"),"stale-id")).isEmpty();
  admin("update fastcoloc_notifications.notification_deliveries set lease_until=now()-interval '1 second' where event_id='"+id+"'");
  var recovered=claim(id);assertThat(recovered.get("action")).isEqualTo("send");assertThat(recovered.get("lease_token")).isNotEqualTo(retry.get("lease_token"));
  assertThat(query(queries.get("Record accepted email"),id,recovered.get("lease_token"),"accepted-id")).hasSize(1);
 }
}
