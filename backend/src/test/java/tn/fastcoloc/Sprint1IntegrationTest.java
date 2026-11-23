package tn.fastcoloc;

import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP and JWT/JWKS + non-owner PostgreSQL. Only external Auth/Storage are local simulators. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Sprint1IntegrationTest {
 static final UUID USER=UUID.fromString("1f2d539d-acb1-4516-a440-7f2918e3f1c2");
 static final UUID OTHER=UUID.fromString("b2b911c7-41a6-4297-9df3-e49bdddb7c7f");
 static final ObjectMapper JSON=new ObjectMapper();static final EmbeddedPostgres PG;static final ECKey KEY;static final HttpServer EXTERNAL;static final String AUTH;
 static final Map<String,byte[]> OBJECTS=new ConcurrentHashMap<>();static volatile JsonNode lastBody;static volatile String lastExternalQuery;static volatile boolean google=true;static volatile boolean autoConfirm=false;
 static final byte[] PNG=png();
 static byte[] png(){try{var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}
 static {
  try {
   PG=EmbeddedPostgres.builder().setPort(0).start();
   try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement()){
    s.execute("create role anon;create role authenticated;create schema auth;create table auth.users(id uuid primary key,email text);revoke create on schema public from public");
    s.execute(Files.readString(Path.of("src/main/resources/db/drafts/V001__sprint1_proposal.sql")));
    s.execute(Files.readString(Path.of("supabase/migrations/20261111220503_provision_fastcoloc_backend.sql")));
    s.execute("alter role fastcoloc_backend login");
    s.execute(Files.readString(Path.of("supabase/migrations/20261112050615_sprint1_backend_capabilities.sql")));
    s.execute(Files.readString(Path.of("supabase/migrations/20261116215125_sprint2_listings.sql")));
    s.execute(Files.readString(Path.of("supabase/migrations/20261117062435_listing_request_location.sql")));
    s.execute(Files.readString(Path.of("supabase/migrations/20261116233807_us29_listing_moderation.sql")));
    s.execute(Files.readString(Path.of("supabase/migrations/20261117000621_admin_contact_read.sql")));
    s.execute("insert into fastcoloc.countries values('TN','Tunisia');insert into fastcoloc.governorates(country_code,name) values('TN','Tunis');insert into fastcoloc.localities(governorate_id,name) values(1,'Tunis')");
   }
   KEY=new ECKeyGenerator(Curve.P_256).keyID("sprint1-test-key").generate();
   EXTERNAL=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AUTH="http://127.0.0.1:"+EXTERNAL.getAddress().getPort();
   EXTERNAL.createContext("/",Sprint1IntegrationTest::external);EXTERNAL.start();
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",()->PG.getJdbcUrl("fastcoloc_backend","postgres"));r.add("spring.datasource.username",()->"fastcoloc_backend");r.add("spring.datasource.password",()->"");
  r.add("fastcoloc.database.require-ssl",()->false);r.add("fastcoloc.auth.url",()->AUTH);r.add("fastcoloc.auth.publishable-key",()->"test-public");r.add("fastcoloc.auth.secret-key",()->"test-server");
  r.add("logging.level.org.springframework",()->"WARN");r.add("debug",()->false);
 }
 @Autowired TestRestTemplate http;
 @org.springframework.test.context.bean.override.mockito.MockitoBean tn.fastcoloc.geography.LocationGateway geography;
 @BeforeEach void geographicProvidersAreLocalFixtures() throws Exception {
  org.mockito.Mockito.when(geography.landCover(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble())).thenReturn(JSON.readTree("{\"values\":[50]}"));
  org.mockito.Mockito.when(geography.reverse(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble())).thenReturn(JSON.readTree("{\"address\":{\"country_code\":\"tn\",\"road\":\"Rue fixture\",\"city\":\"Tunis\"}}"));
 }
 @Test void geographySaveRejectsWaterAndPersistsCreateAndEditCoordinates() throws Exception {
  listingAccounts();
  var create = listingBody("OFFER",350);
  create.put("latitude",36.812341);create.put("longitude",10.181231);create.put("street","Adresse initiale");
  var response = request(HttpMethod.POST,"/api/v1/listings",create,USER);
  assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
  String id = JSON.readTree(response.getBody()).path("id").asText();
  var saved = JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/"+id,null,USER).getBody());
  assertThat(saved.path("latitude").asDouble()).isEqualTo(36.812341);
  assertThat(saved.path("street").asText()).isEqualTo("Adresse initiale");
  var update = listingBody("OFFER",400);update.put("version",0);update.put("latitude",36.812342);update.put("longitude",10.181232);update.put("street","Adresse corrigée");
  assertThat(request(HttpMethod.PUT,"/api/v1/listings/"+id,update,USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  saved = JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/"+id,null,USER).getBody());
  assertThat(saved.path("longitude").asDouble()).isEqualTo(10.181232);
  assertThat(saved.path("street").asText()).isEqualTo("Adresse corrigée");
  org.mockito.Mockito.when(geography.landCover(36.85,10.25)).thenReturn(JSON.readTree("{\"values\":[80]}"));
  update.put("latitude",36.85);update.put("longitude",10.25);update.put("version",1);
  assertThat(request(HttpMethod.PUT,"/api/v1/listings/"+id,update,USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(request(HttpMethod.POST,"/api/v1/listings",update,USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  saved = JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/"+id,null,USER).getBody());
  assertThat(saved.path("latitude").asDouble()).isEqualTo(36.812342);
  assertThat(saved.path("street").asText()).isEqualTo("Adresse corrigée");
  assertThat(request(HttpMethod.POST,"/api/v1/geography/position",Map.of("latitude",36.85,"longitude",10.25),USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(request(HttpMethod.POST,"/api/v1/geography/position",Map.of("latitude",36.8,"longitude",10.18),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
 }

 @Test void emailRedirectsAreEncodedExactlyOnceForWebAndAndroid()throws Exception{
  for(String client:List.of("android","web"))for(String route:List.of("signup","forgot-password","resend-confirmation")){
   String body=route.equals("signup")?"{\"email\":\"fixture@example.invalid\",\"password\":\"Local-test-password-123\"}":"{\"email\":\"fixture@example.invalid\"}";
   assertThat(request(HttpMethod.POST,"/api/v1/auth/"+route+"?client="+client,body,null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
   assertThat(java.net.URLDecoder.decode(lastExternalQuery,StandardCharsets.UTF_8)).isEqualTo("redirect_to="+(client.equals("android")?"fastcoloc://auth/callback":"http://localhost:4200/auth/callback"));
  }
 }
 @Test void androidGoogleUsesFixedNativeCallbackAndPreservesState()throws Exception{
  String state="s".repeat(43),challenge="c".repeat(43);
  var response=request(HttpMethod.GET,"/api/v1/auth/google?client=android&state="+state+"&codeChallenge="+challenge,null,null);
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  String url=JSON.readTree(response.getBody()).path("authorizationUrl").asText();
  String decoded=java.net.URLDecoder.decode(url,StandardCharsets.UTF_8);
  assertThat(decoded).contains("redirect_to=fastcoloc://auth/callback?state="+state,"code_challenge="+challenge,"code_challenge_method=s256");
 }
 @Test void androidGoogleRequiresStateAndRejectsArbitraryClient()throws Exception{
  String challenge="c".repeat(43);
  assertThat(request(HttpMethod.GET,"/api/v1/auth/google?client=android&codeChallenge="+challenge,null,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(request(HttpMethod.GET,"/api/v1/auth/google?client=https://evil.invalid&codeChallenge="+challenge,null,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
 }
 @Test void defaultGoogleCallbackRemainsWeb()throws Exception{
  var response=request(HttpMethod.GET,"/api/v1/auth/google?codeChallenge="+"c".repeat(43),null,null);
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  String decoded=java.net.URLDecoder.decode(JSON.readTree(response.getBody()).path("authorizationUrl").asText(),StandardCharsets.UTF_8);
  assertThat(decoded).contains("redirect_to=http://localhost:4200/auth/callback").doesNotContain("fastcoloc://");
 }
 @BeforeEach void reset()throws Exception{sql("truncate auth.users cascade;insert into auth.users values('"+USER+"','user@example.invalid'),('"+OTHER+"','other@example.invalid')");OBJECTS.clear();google=true;autoConfirm=false;lastBody=null;}
 @AfterAll void stop()throws Exception{EXTERNAL.stop(0);PG.close();}
 static void sql(String q)throws Exception{try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute(q);}}
 static String token(UUID id)throws Exception{
  var claims=new JWTClaimsSet.Builder().issuer(AUTH+"/auth/v1").subject(id.toString()).audience("authenticated").issueTime(java.util.Date.from(Instant.now().minusSeconds(1))).expirationTime(java.util.Date.from(Instant.now().plusSeconds(600))).claim("role","authenticated").claim("session_id",UUID.randomUUID().toString()).claim("is_anonymous",false).build();
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY.getKeyID()).build(),claims);jwt.sign(new ECDSASigner(KEY));return jwt.serialize();
 }
 static void external(HttpExchange e){
  try {
   String path=e.getRequestURI().getPath(),method=e.getRequestMethod();lastExternalQuery=e.getRequestURI().getRawQuery();byte[] bytes=e.getRequestBody().readAllBytes();JsonNode body=bytes.length==0?JSON.createObjectNode():path.matches("/storage/v1/object/(profile-photos|listing-photos)/.*")?JSON.createObjectNode():JSON.readTree(bytes);lastBody=body;
   if(path.endsWith("jwks.json")){send(e,200,new JWKSet(KEY.toPublicJWK()).toString());return;}
   if(path.endsWith("/settings")){send(e,200,"{\"external\":{\"email\":true,\"google\":"+google+"},\"mailer_autoconfirm\":"+autoConfirm+",\"disable_signup\":false}");return;}
   if(path.equals("/auth/v1/token")||path.equals("/auth/v1/verify")){send(e,200,JSON.writeValueAsString(Map.of("access_token",token(USER),"refresh_token","local-refresh-token","token_type","bearer","expires_in",600)));return;}
   if(path.equals("/auth/v1/signup")){send(e,200,"{\"id\":\""+USER+"\",\"email_confirmed_at\":null}");return;}
   if(path.equals("/auth/v1/recover")||path.equals("/auth/v1/resend")||path.equals("/auth/v1/logout")){send(e,200,"{}");return;}
   if(path.equals("/auth/v1/user")){
    String header=e.getRequestHeaders().getFirst("Authorization");String id=SignedJWT.parse(header.substring(7)).getJWTClaimsSet().getSubject();
    send(e,200,"{\"id\":\""+id+"\",\"email\":\"test@example.invalid\",\"is_anonymous\":false,\"email_confirmed_at\":\"2026-01-01T00:00:00Z\"}");return;
   }
   if(path.startsWith("/auth/v1/admin/users/")&&method.equals("DELETE")){String id=path.substring(path.lastIndexOf('/')+1);UUID.fromString(id);sql("delete from auth.users where id='"+id+"'");send(e,200,"{}");return;}
   if(path.startsWith("/storage/")){
    if(!"test-server".equals(e.getRequestHeaders().getFirst("apikey"))){send(e,401,"{}");return;}
    if(path.equals("/storage/v1/object/sign/listing-photos")){var signed=JSON.createArrayNode();for(var p:body.path("paths"))signed.addObject().put("path",p.asText()).put("signedURL","/object/sign/listing-photos/"+p.asText()+"?token=local-only");send(e,200,signed.toString());return;}
    if(path.startsWith("/storage/v1/object/listing-photos/")&&method.equals("POST")){OBJECTS.put("listing:"+path.substring("/storage/v1/object/listing-photos/".length()),bytes);send(e,200,"{}");return;}
    if(path.equals("/storage/v1/object/listing-photos")&&method.equals("DELETE")){for(var p:body.path("prefixes"))OBJECTS.remove("listing:"+p.asText());send(e,200,"[]");return;}
    if(path.equals("/storage/v1/object/list/listing-photos")){String prefix="listing:"+body.path("prefix").asText();var list=JSON.createArrayNode();Set<String> names=new HashSet<>();for(String p:OBJECTS.keySet())if(p.startsWith(prefix)){String suffix=p.substring(prefix.length());String name=suffix.split("/")[0];if(names.add(name)){var row=list.addObject().put("name",name);if(suffix.contains("/"))row.putNull("id");else row.put("id",UUID.randomUUID().toString());}}send(e,200,list.toString());return;}
    if(path.startsWith("/storage/v1/object/sign/")){send(e,200,"{\"signedURL\":\"/object/sign/profile-photos/test?token=local-only\"}");return;}
    if(path.startsWith("/storage/v1/object/profile-photos/")&&method.equals("POST")){OBJECTS.put(path.substring("/storage/v1/object/profile-photos/".length()),bytes);send(e,200,"{}");return;}
    if(path.equals("/storage/v1/object/profile-photos")&&method.equals("DELETE")){for(var p:body.path("prefixes"))OBJECTS.remove(p.asText());send(e,200,"[]");return;}
    if(path.equals("/storage/v1/object/list/profile-photos")){String prefix=body.path("prefix").asText();List<Map<String,String>> list=new ArrayList<>();for(String p:OBJECTS.keySet())if(!p.startsWith("listing:")&&p.startsWith(prefix))list.add(Map.of("name",p.substring(prefix.length()),"id",UUID.randomUUID().toString()));send(e,200,JSON.writeValueAsString(list));return;}
   }
   send(e,404,"{}");
  }catch(Exception error){try{send(e,500,"{}");}catch(Exception ignored){}}
 }
 static void send(HttpExchange e,int status,String value)throws Exception{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}}
 ResponseEntity<String> request(HttpMethod method,String path,Object body,UUID user)throws Exception{
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(user!=null)headers.setBearerAuth(token(user));return http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
 }
 void sync(UUID id)throws Exception{assertThat(request(HttpMethod.POST,"/api/v1/users/sync","{}",id).getStatusCode()).isEqualTo(HttpStatus.OK);}
 UUID photo(UUID user)throws Exception{
  var r=uploadResponse(user,PNG);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.CREATED);return UUID.fromString(JSON.readTree(r.getBody()).path("id").asText());
 }
 ResponseEntity<String> uploadResponse(UUID user,byte[] bytes)throws Exception{
  var parts=new LinkedMultiValueMap<String,Object>();parts.add("file",new ByteArrayResource(bytes){public String getFilename(){return "test.png";}});parts.add("caption","Photo de test");
  var h=new HttpHeaders();h.setContentType(MediaType.MULTIPART_FORM_DATA);h.setBearerAuth(token(user));return http.exchange("/api/v1/profile-photos",HttpMethod.POST,new HttpEntity<>(parts,h),String.class);
 }
 void firstFour(UUID user)throws Exception{
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/1","{\"firstName\":\"Amel\",\"lastName\":\"Test\",\"age\":23}",user).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/2","{\"countryCode\":\"TN\",\"governorateId\":1,\"localityId\":1,\"street\":\"Rue test\",\"postalCode\":\"1000\"}",user).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/3","{\"mainActivities\":[],\"hobbies\":[],\"spokenLanguages\":[\"fr\"],\"religion\":null,\"smoking\":null}",user).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/4","{\"mobilePhone\":\"12345678\"}",user).getStatusCode()).isEqualTo(HttpStatus.OK);
 }
 @Test void completeFiveStepsResumeAndInvalidateAfterPhotoDeletion()throws Exception{
  sync(USER);firstFour(USER);UUID p=photo(USER);
  assertThat(request(HttpMethod.POST,"/api/v1/onboarding/complete",null,USER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/5",Map.of("photoIds",List.of(p)),USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  var done=request(HttpMethod.POST,"/api/v1/onboarding/complete",null,USER);assertThat(done.getStatusCode()).as(done.getBody()).isEqualTo(HttpStatus.OK);assertThat(JSON.readTree(done.getBody()).path("profile").path("completedAt").isTextual()).isTrue();
  assertThat(request(HttpMethod.GET,"/api/v1/profile-photos/"+p+"/signed-url",null,USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.DELETE,"/api/v1/profile-photos/"+p,null,USER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  assertThat(OBJECTS).isEmpty();assertThat(request(HttpMethod.POST,"/api/v1/onboarding/complete",null,USER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
 }
 @Test void geographyMismatchAndUnknownIdentityFieldsAreRejected()throws Exception{
  sync(USER);assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/2","{\"countryCode\":\"FR\",\"governorateId\":1,\"localityId\":1,\"street\":\"Rue\",\"postalCode\":\"1000\"}",USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(request(HttpMethod.PUT,"/api/v1/users/me","{\"firstName\":\"A\",\"lastName\":\"B\",\"age\":23,\"role\":\"ADMIN\"}",USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
 }
 @Test void preferencesAreTransactionalAndNeutralPersonalChoicesPersist()throws Exception{
  sync(USER);firstFour(USER);
  assertThat(request(HttpMethod.PUT,"/api/v1/users/me/preferences","{\"minBudget\":100,\"maxBudget\":500,\"localityIds\":[1]}",USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.PUT,"/api/v1/users/me/preferences","{\"minBudget\":600,\"maxBudget\":500,\"localityIds\":[1]}",USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  var details=JSON.readTree(request(HttpMethod.GET,"/api/v1/users/me/details",null,USER).getBody());assertThat(details.path("preferences").path("min_budget").asInt()).isEqualTo(100);assertThat(details.path("personal").path("smoking").isNull()).isTrue();
 }
 @Test void usersCannotReadOtherPhotosAndBlockedUsersCannotEdit()throws Exception{
  sync(USER);sync(OTHER);UUID p=photo(USER);
  assertThat(request(HttpMethod.GET,"/api/v1/profile-photos/"+p+"/signed-url",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/5",Map.of("photoIds",List.of(p)),OTHER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");
  assertThat(request(HttpMethod.GET,"/api/v1/users/me/details",null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
 }
 @Test void adminListBlockUnblockAndContactsRequireActiveAdminAndAudit()throws Exception{
  sync(USER);sync(OTHER);
  assertThat(request(HttpMethod.GET,"/api/v1/admin/users",null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
  assertThat(request(HttpMethod.GET,"/api/v1/admin/users?limit=1",null,USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.PATCH,"/api/v1/admin/users/"+OTHER+"/status","{\"status\":\"BLOCKED\",\"reason\":\"Test\"}",USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.GET,"/api/v1/users/me",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  assertThat(request(HttpMethod.PATCH,"/api/v1/admin/users/"+OTHER+"/contacts","{\"contacts\":{\"mobilePhone\":\"87654321\"},\"reason\":\"Correction\"}",USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.PATCH,"/api/v1/admin/users/"+OTHER+"/status","{\"status\":\"ACTIVE\",\"reason\":\"Test terminé\"}",USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.GET,"/api/v1/users/me",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.OK);
  try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select count(*) from fastcoloc.account_access_events")){r.next();assertThat(r.getInt(1)).isEqualTo(3);}
 }
 @Test void adminContactsPrefillRequiresAdminAndPreservesOtherValues()throws Exception{
  sync(USER);sync(OTHER);
  var initial=Map.of("mobilePhone","21000000","residentialPhone","71000000","callAvailability","Après 18 h","msnContact","contact-test","skypeContact","skype-test","websiteUrl","https://example.com");
  assertThat(request(HttpMethod.PUT,"/api/v1/onboarding/steps/4",initial,OTHER).getStatusCode()).isEqualTo(HttpStatus.OK);
  String path="/api/v1/admin/users/"+OTHER+"/contacts";
  assertThat(request(HttpMethod.GET,path,null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
  var r=request(HttpMethod.GET,path,null,USER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
  var c=JSON.readTree(r.getBody());assertThat(c.path("mobile_phone").asText()).isEqualTo("21000000");assertThat(c.path("website_url").asText()).isEqualTo("https://example.com");assertThat(c.size()).isEqualTo(6);
  var updated=new HashMap<String,String>(initial);updated.put("mobilePhone","22000000");
  assertThat(request(HttpMethod.PATCH,path,Map.of("contacts",updated,"reason","Correction isolée du mobile"),USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  c=JSON.readTree(request(HttpMethod.GET,path,null,USER).getBody());assertThat(c.path("mobile_phone").asText()).isEqualTo("22000000");assertThat(c.path("residential_phone").asText()).isEqualTo("71000000");assertThat(c.path("msn_contact").asText()).isEqualTo("contact-test");
  assertThat(request(HttpMethod.GET,"/api/v1/admin/users/"+UUID.randomUUID()+"/contacts",null,USER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  try(var connection=java.sql.DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"));var s=connection.createStatement()){
   connection.setAutoCommit(false);s.execute("select set_config('fastcoloc.user_id','"+OTHER+"',true)");
   assertThatThrownBy(()->s.executeQuery("select fastcoloc.admin_read_contacts('"+OTHER+"')")).isInstanceOf(java.sql.SQLException.class);connection.rollback();
  }
  sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");assertThat(request(HttpMethod.GET,path,null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
 }
 @Test void adminCannotSelfBlockAndUserCannotInvokePrivilegedFunctionDirectly()throws Exception{
  sync(USER);sync(OTHER);sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
  assertThat(request(HttpMethod.PATCH,"/api/v1/admin/users/"+USER+"/status","{\"status\":\"BLOCKED\",\"reason\":\"Test\"}",USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  try(var c=java.sql.DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"));var s=c.createStatement()){
   c.setAutoCommit(false);s.execute("select set_config('fastcoloc.user_id','"+OTHER+"',true)");assertThatThrownBy(()->s.execute("select fastcoloc.admin_users(20,0,null,null)")).isInstanceOf(java.sql.SQLException.class);c.rollback();
  }
 }
 @Test void adminDeletionCleansPrivateObjectsAndAuthCascadeAndKeepsAudit()throws Exception{
  sync(USER);sync(OTHER);photo(OTHER);sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
  var r=request(HttpMethod.DELETE,"/api/v1/admin/users/"+OTHER,Map.of("reason","Fin du test"),USER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.NO_CONTENT);assertThat(OBJECTS).isEmpty();
  try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var rows=s.executeQuery("select count(*) from fastcoloc.profiles where id='"+OTHER+"'")){rows.next();assertThat(rows.getInt(1)).isZero();}
 }
 @Test void authSignupLoginRefreshRecoveryPkceAndLogoutUseExpectedContracts()throws Exception{
  assertThat(request(HttpMethod.POST,"/api/v1/auth/signup","{\"email\":\"test@example.invalid\",\"password\":\"LocalTest!Password123\"}",null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  assertThat(request(HttpMethod.POST,"/api/v1/auth/login","{\"email\":\"test@example.invalid\",\"password\":\"LocalTest!Password123\"}",null).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.POST,"/api/v1/auth/refresh","{\"refreshToken\":\"local-refresh-token\"}",null).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(lastBody.path("refresh_token").asText()).isEqualTo("local-refresh-token");
  assertThat(request(HttpMethod.POST,"/api/v1/auth/forgot-password","{\"email\":\"test@example.invalid\"}",null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  assertThat(request(HttpMethod.POST,"/api/v1/auth/exchange",Map.of("code","local-code","codeVerifier","v".repeat(43)),null).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(lastBody.path("code_verifier").asText()).hasSize(43);
  assertThat(request(HttpMethod.PUT,"/api/v1/auth/password",Map.of("password","NewLocalTest!12345"),USER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  assertThat(request(HttpMethod.POST,"/api/v1/auth/logout",null,USER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
 }
 @Test void disabledGoogleAndDisabledConfirmationAreExplicitAndSwaggerLoads()throws Exception{
  assertThat(request(HttpMethod.GET,"/api/v1/auth/google",null,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  google=false;assertThat(request(HttpMethod.GET,"/api/v1/auth/google?codeChallenge="+"c".repeat(43),null,null).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  autoConfirm=true;assertThat(request(HttpMethod.POST,"/api/v1/auth/signup",Map.of("email","test@example.invalid","password","LocalTest!Password123"),null).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  var docs=http.getForEntity("/v3/api-docs",String.class);assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(JSON.readTree(docs.getBody()).path("paths").path("/api/v1/users/me").path("get").path("security").get(0).has("supabaseBearer")).isTrue();
 }
 @Test void malformedPhotosAndQuotaAreRejectedAndCaptionPatchPreservesOrder()throws Exception{
  sync(USER);
  assertThat(uploadResponse(USER,new byte[]{(byte)137,80,78,71,13,10,26,10}).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(uploadResponse(USER,"<html>not an image</html>".getBytes(StandardCharsets.UTF_8)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  List<UUID> photos=new ArrayList<>();for(int i=0;i<5;i++)photos.add(photo(USER));
  assertThat(uploadResponse(USER,PNG).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(request(HttpMethod.PATCH,"/api/v1/profile-photos/"+photos.get(1),Map.of("caption","Nouvelle description"),USER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  var details=JSON.readTree(request(HttpMethod.GET,"/api/v1/users/me/details",null,USER).getBody());
  for(var p:details.path("photos"))if(p.path("id").asText().equals(photos.get(1).toString())){assertThat(p.path("caption").asText()).isEqualTo("Nouvelle description");assertThat(p.path("display_order").asInt()).isEqualTo(1);}
 }
 Map<String,Object> listingBody(String kind,int rent){var b=new LinkedHashMap<String,Object>();b.put("kind",kind);b.put("title","Une vraie colocation Tunis");b.put("description","Appartement lumineux proche des transports à partager.");b.put("localityId",1);b.put("street","Rue de la fixture");b.put("latitude",36.8);b.put("longitude",10.18);b.put("rent",rent);b.put("capacity",2);b.put("propertyType","Appartement");b.put("roommateType","Étudiant");b.put("equipment",List.of("WiFi","Cuisine"));b.put("availableFrom","2026-01-01");b.put("publicationDays",30);return b;}
 void listingAccounts()throws Exception{sync(USER);sync(OTHER);sql("update fastcoloc.profiles set completed_at=now()");}
 JsonNode adminGet(String path)throws Exception{var r=request(HttpMethod.GET,"/api/v1/admin/listings"+path,null,OTHER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);return JSON.readTree(r.getBody());}
 JsonNode moderate(UUID id,String action,long version)throws Exception{var r=request(HttpMethod.POST,"/api/v1/admin/listings/"+id+"/"+action,Map.of("reason","Contrôle US29 local","version",version),OTHER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);return JSON.readTree(r.getBody());}
 @Test void us29AdminVisibilityFiltersHideRestoreAndExpiry()throws Exception{
  listingAccounts();UUID id=listing("OFFER",350);publish(id);listing("REQUEST",200);
  sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+OTHER+"'");
  assertThat(adminGet("?sort=RENT_ASC&size=1").path("total").asInt()).isEqualTo(2);
  assertThat(adminGet("?kind=REQUEST&status=DRAFT&q=Tunis").path("items").size()).isEqualTo(1);
  assertThat(adminGet("?from=2000-01-01&to=2000-01-02").path("total").asInt()).isZero();
  assertThat(request(HttpMethod.GET,"/api/v1/admin/listings?from=2030-01-01&to=2000-01-01",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  var before=adminGet("/"+id);var hidden=moderate(id,"hide",1);
  assertThat(hidden.path("display_status").asText()).isEqualTo("HIDDEN");assertThat(found("",null)).isZero();assertThat(found("",USER)).isZero();
  assertThat(request(HttpMethod.GET,"/api/v1/listings/"+id,null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(request(HttpMethod.GET,"/api/v1/listings/"+id,null,USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.POST,"/api/v1/listings/"+id+"/renew",null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  assertThat(request(HttpMethod.POST,"/api/v1/listings/"+id+"/publish",null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  var edit=listingBody("OFFER",390);edit.put("version",2);
  assertThat(request(HttpMethod.PUT,"/api/v1/listings/"+id,edit,USER).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(found("",null)).isZero();
  var restored=moderate(id,"restore",3);assertThat(restored.path("expires_at")).isEqualTo(before.path("expires_at"));assertThat(found("",null)).isEqualTo(1);
  sql("update fastcoloc.listings set published_at=now()-interval '3 days',expires_at=now()-interval '1 day' where id='"+id+"'");
  moderate(id,"hide",4);assertThat(moderate(id,"restore",5).path("display_status").asText()).isEqualTo("EXPIRED");assertThat(found("",null)).isZero();
  assertThat(adminGet("/"+id+"/history").size()).isEqualTo(4);
 }
 @Test void us29AuthorizationDatabasePermissionsAndAuditedEdit()throws Exception{
  listingAccounts();UUID id=listing("OFFER",350);publish(id);
  assertThat(request(HttpMethod.GET,"/api/v1/admin/listings",null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  assertThat(request(HttpMethod.POST,"/api/v1/admin/listings/"+id+"/hide",Map.of("reason","Refus","version",1),USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+OTHER+"'");
  var edit=listingBody("OFFER",420);edit.put("version",1);
  var r=request(HttpMethod.PUT,"/api/v1/admin/listings/"+id,Map.of("listing",edit,"reason","Correction du loyer"),OTHER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
  assertThat(JSON.readTree(r.getBody()).path("rent").asInt()).isEqualTo(420);
  assertThat(request(HttpMethod.PUT,"/api/v1/admin/listings/"+id,Map.of("listing",edit,"reason","Version périmée"),OTHER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  var event=adminGet("/"+id+"/history").get(0);assertThat(event.path("before_state").path("rent").asInt()).isEqualTo(350);assertThat(event.path("after_state").path("rent").asInt()).isEqualTo(420);assertThat(event.path("actor_id").asText()).isEqualTo(OTHER.toString());
  moderate(id,"hide",2);
  try(var c=java.sql.DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"));var s=c.createStatement()){
   c.setAutoCommit(false);s.execute("select set_config('fastcoloc.user_id','"+USER+"',true)");
   assertThatThrownBy(()->s.executeUpdate("update fastcoloc.listings set is_hidden=false where id='"+id+"'")).isInstanceOf(java.sql.SQLException.class);c.rollback();
   s.execute("select set_config('fastcoloc.user_id','"+USER+"',true)");
   assertThatThrownBy(()->s.executeUpdate("update fastcoloc.listings set expires_at=now()+interval '10 days' where id='"+id+"'")).isInstanceOf(java.sql.SQLException.class);c.rollback();
   s.execute("select set_config('fastcoloc.user_id','"+USER+"',true)");
   assertThatThrownBy(()->s.executeQuery("select fastcoloc.admin_listing_detail('"+id+"')")).isInstanceOf(java.sql.SQLException.class);c.rollback();
   assertThatThrownBy(()->s.executeQuery("select * from fastcoloc.listing_moderation_events")).isInstanceOf(java.sql.SQLException.class);c.rollback();
  }
  sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+OTHER+"'");
  assertThat(request(HttpMethod.GET,"/api/v1/admin/listings",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
 }
 @Test void us29DeletionCleansPhotosFavoritesAndPreservesHistory()throws Exception{
  listingAccounts();UUID id=listing("OFFER",300);publish(id);
  var headers=new HttpHeaders();headers.setBearerAuth(token(USER));headers.setContentType(MediaType.MULTIPART_FORM_DATA);
  var form=new LinkedMultiValueMap<String,Object>();form.add("file",new ByteArrayResource(PNG){@Override public String getFilename(){return "us29.png";}});
  assertThat(http.exchange("/api/v1/listings/"+id+"/photos",HttpMethod.POST,new HttpEntity<>(form,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  request(HttpMethod.PUT,"/api/v1/listings/"+id+"/favorite",null,OTHER);sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+OTHER+"'");
  assertThat(adminGet("/"+id).path("photos").get(0).has("object_path")).isFalse();
  moderate(id,"hide",1);assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/favorites",null,OTHER).getBody()).path("total").asInt()).isZero();
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/map",null,USER).getBody()).path("total").asInt()).isZero();
  assertThat(request(HttpMethod.DELETE,"/api/v1/admin/listings/"+id,Map.of("reason","Suppression isolée US29","version",1),OTHER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(OBJECTS).hasSize(1);
  assertThat(request(HttpMethod.DELETE,"/api/v1/admin/listings/"+id,Map.of("reason","Suppression isolée US29","version",2),OTHER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  assertThat(OBJECTS).isEmpty();var history=adminGet("/"+id+"/history");assertThat(history.size()).isEqualTo(2);assertThat(history.get(0).path("action").asText()).isEqualTo("DELETE");assertThat(history.get(0).path("after_state").isNull()).isTrue();
  try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select (select count(*) from fastcoloc.listings)+(select count(*) from fastcoloc.listing_photos)+(select count(*) from fastcoloc.listing_favorites)")){r.next();assertThat(r.getInt(1)).isZero();}
 }
 UUID listing(String kind,int rent)throws Exception{var r=request(HttpMethod.POST,"/api/v1/listings",listingBody(kind,rent),USER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.CREATED);return UUID.fromString(JSON.readTree(r.getBody()).path("id").asText());}
 void publish(UUID id)throws Exception{var r=request(HttpMethod.POST,"/api/v1/listings/"+id+"/publish",null,USER);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);}
 long found(String query,UUID actor)throws Exception{var r=request(HttpMethod.GET,"/api/v1/listings"+query,null,actor);assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);return JSON.readTree(r.getBody()).path("total").asLong();}
 @Test void requestWithoutGpsAndOfferValidationUseRealApi() throws Exception {
  listingAccounts();
  var requestBody=listingBody("REQUEST",600);requestBody.remove("latitude");requestBody.remove("longitude");requestBody.put("street","");
  var created=request(HttpMethod.POST,"/api/v1/listings",requestBody,USER);
  assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
  var row=JSON.readTree(created.getBody());var id=UUID.fromString(row.path("id").asText());
  assertThat(row.path("latitude").isNull()).isTrue();assertThat(row.path("rent").asInt()).isEqualTo(600);publish(id);
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/"+id,null,OTHER).getBody()).path("status").asText()).isEqualTo("PUBLISHED");
  requestBody.put("kind","OFFER");assertThat(request(HttpMethod.POST,"/api/v1/listings",requestBody,USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  var offer=listing("OFFER",350);publish(offer);
  var badDates=listingBody("REQUEST",600);badDates.put("availableUntil","2020-01-01");assertThat(request(HttpMethod.POST,"/api/v1/listings",badDates,USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  var incomplete=listingBody("REQUEST",600);incomplete.remove("longitude");assertThat(request(HttpMethod.POST,"/api/v1/listings",incomplete,USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
 }
 @Test void sprint2DraftPublishFiltersPaginationAndRequestAreShared()throws Exception{
  listingAccounts();UUID offer=listing("OFFER",350);assertThat(found("",null)).isZero();
  assertThat(request(HttpMethod.GET,"/api/v1/listings/"+offer,null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  publish(offer);UUID demand=listing("REQUEST",200);publish(demand);
  assertThat(found("",null)).isEqualTo(2);assertThat(found("?kind=OFFER&minRent=300&maxRent=400&minCapacity=2&localityId=1&equipment=WiFi&availableOn=2026-10-01",OTHER)).isEqualTo(1);
  assertThat(found("?kind=REQUEST",OTHER)).isEqualTo(1);assertThat(found("?maxRent=100",OTHER)).isZero();
  assertThat(found("?south=35&north=36&west=9&east=11",OTHER)).isZero();
  assertThat(found("?south=36&north=37&west=9&east=11",OTHER)).isEqualTo(2);
  var page=JSON.readTree(request(HttpMethod.GET,"/api/v1/listings?size=1&page=0&sort=RENT_ASC",null,OTHER).getBody());assertThat(page.path("total").asInt()).isEqualTo(2);assertThat(page.path("items").size()).isEqualTo(1);assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(demand.toString());
  assertThat(page.path("items").get(0).has("street")).isFalse();
  assertThat(request(HttpMethod.GET,"/api/v1/listings/"+offer,null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
 }
 @Test void sprint2OwnershipVersionFavoritesAndDeletion()throws Exception{
  listingAccounts();UUID id=listing("OFFER",350);publish(id);
  var b=listingBody("OFFER",410);b.put("version",1);
  assertThat(request(HttpMethod.PUT,"/api/v1/listings/"+id,b,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(request(HttpMethod.DELETE,"/api/v1/listings/"+id,null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(request(HttpMethod.POST,"/api/v1/listings/"+id+"/renew",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  var changed=request(HttpMethod.PUT,"/api/v1/listings/"+id,b,USER);assertThat(changed.getStatusCode()).as(changed.getBody()).isEqualTo(HttpStatus.OK);
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/"+id,null,OTHER).getBody()).path("rent").asInt()).isEqualTo(410);
  assertThat(request(HttpMethod.PUT,"/api/v1/listings/"+id,b,USER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  for(int i=0;i<2;i++)assertThat(request(HttpMethod.PUT,"/api/v1/listings/"+id+"/favorite",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/favorites",null,OTHER).getBody()).path("total").asInt()).isEqualTo(1);
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/favorites",null,USER).getBody()).path("total").asInt()).isZero();
  assertThat(request(HttpMethod.DELETE,"/api/v1/listings/"+id+"/favorite",null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/favorites",null,OTHER).getBody()).path("total").asInt()).isZero();
  assertThat(request(HttpMethod.DELETE,"/api/v1/listings/"+id,null,USER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);assertThat(found("",OTHER)).isZero();
 }
 @Test void sprint2ExpirationRenewalAndInvalidInputs()throws Exception{
  sync(USER);assertThat(request(HttpMethod.POST,"/api/v1/listings",listingBody("OFFER",100),USER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  sync(OTHER);sql("update fastcoloc.profiles set completed_at=now()");UUID id=listing("OFFER",350);publish(id);
  sql("update fastcoloc.listings set published_at=now()-interval '3 days',expires_at=now()-interval '1 day' where id='"+id+"'");
  assertThat(found("",null)).isZero();assertThat(request(HttpMethod.GET,"/api/v1/listings/"+id,null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(request(HttpMethod.GET,"/api/v1/listings/"+id,null,USER).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(request(HttpMethod.POST,"/api/v1/listings/"+id+"/renew",null,USER).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(found("",null)).isEqualTo(1);
  sql("update fastcoloc.profiles set completed_at=null where id='"+USER+"'");
  assertThat(request(HttpMethod.POST,"/api/v1/listings/"+id+"/renew",null,USER).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  sql("update fastcoloc.profiles set completed_at=now() where id='"+USER+"'");
  for(String query:List.of("?minRent=400&maxRent=200","?page=-1","?size=999","?sort=rent;drop","?south=35","?kind=OTHER"))assertThat(request(HttpMethod.GET,"/api/v1/listings"+query,null,OTHER).getStatusCode().is4xxClientError()).as(query).isTrue();
  var b=listingBody("OFFER",300);b.put("authorId",OTHER);assertThat(request(HttpMethod.POST,"/api/v1/listings",b,USER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");
  assertThat(request(HttpMethod.POST,"/api/v1/listings/"+id+"/renew",null,USER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
 }
 @Test void sprint2PhotosUsePrivateStorageAndOwnership()throws Exception{
  listingAccounts();UUID id=listing("OFFER",300);
  var headers=new HttpHeaders();headers.setBearerAuth(token(USER));headers.setContentType(MediaType.MULTIPART_FORM_DATA);
  var form=new LinkedMultiValueMap<String,Object>();form.add("file",new ByteArrayResource(PNG){@Override public String getFilename(){return "room.png";}});form.add("caption","La chambre");
  var r=http.exchange("/api/v1/listings/"+id+"/photos",HttpMethod.POST,new HttpEntity<>(form,headers),String.class);
  assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);var p=JSON.readTree(r.getBody()).path("photos").get(0);assertThat(p.path("url").asText()).contains("/object/sign/listing-photos/");assertThat(p.has("object_path")).isFalse();
  assertThat(OBJECTS).hasSize(1);publish(id);
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/"+id,null,OTHER).getBody()).path("photos").get(0).path("caption").asText()).isEqualTo("La chambre");
  assertThat(request(HttpMethod.DELETE,"/api/v1/listings/"+id+"/photos/"+p.path("id").asText(),null,OTHER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(request(HttpMethod.DELETE,"/api/v1/listings/"+id+"/photos/"+p.path("id").asText(),null,USER).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);assertThat(OBJECTS).isEmpty();
 }
 @Test void sprint2RuntimeRlsRejectsCrossAuthorAndHasNoClientGrants()throws Exception{
  listingAccounts();UUID id=listing("OFFER",300);publish(id);
  try(var c=java.sql.DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"));var s=c.createStatement()){
   c.setAutoCommit(false);s.execute("select set_config('fastcoloc.user_id','"+OTHER+"',true)");
   assertThat(s.executeUpdate("update fastcoloc.listings set title='Unauthorized' where id='"+id+"'")).isZero();
   assertThat(s.executeUpdate("delete from fastcoloc.listings where id='"+id+"'")).isZero();c.rollback();
  }
  try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select count(*) from information_schema.role_table_grants where table_schema='fastcoloc' and table_name in ('listings','listing_photos','listing_favorites') and grantee in ('anon','authenticated','PUBLIC')")){r.next();assertThat(r.getInt(1)).isZero();}
 }
 @Test void sprint2MapContainsAllMatchingOffersAcrossCatalogPages()throws Exception{
  listingAccounts();for(int i=0;i<13;i++)publish(listing("OFFER",100+i));publish(listing("REQUEST",100));listing("OFFER",100);
  var catalog=JSON.readTree(request(HttpMethod.GET,"/api/v1/listings?size=1",null,OTHER).getBody());assertThat(catalog.path("items").size()).isEqualTo(1);assertThat(catalog.path("total").asInt()).isEqualTo(14);
  var map=JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/map?size=50",null,OTHER).getBody());assertThat(map.path("items").size()).isEqualTo(13);assertThat(map.path("total").asInt()).isEqualTo(13);
  for(var row:map.path("items")){assertThat(row.path("kind").asText()).isEqualTo("OFFER");assertThat(row.has("photos")).isFalse();assertThat(row.path("latitude").asDouble()).isEqualTo(36.8);}
  assertThat(JSON.readTree(request(HttpMethod.GET,"/api/v1/listings/map?maxRent=102",null,OTHER).getBody()).path("total").asInt()).isEqualTo(3);
  assertThat(request(HttpMethod.GET,"/api/v1/listings/map",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
 }
 @Test void sprint2ExistingAccountDeletionAlsoCleansNewListingPhotos()throws Exception{
  listingAccounts();photo(USER);UUID id=listing("OFFER",300);
  var headers=new HttpHeaders();headers.setBearerAuth(token(USER));headers.setContentType(MediaType.MULTIPART_FORM_DATA);
  var form=new LinkedMultiValueMap<String,Object>();form.add("file",new ByteArrayResource(PNG){@Override public String getFilename(){return "fixture.png";}});
  assertThat(http.exchange("/api/v1/listings/"+id+"/photos",HttpMethod.POST,new HttpEntity<>(form,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(OBJECTS).hasSize(2);sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+OTHER+"'");
  var deleted=request(HttpMethod.DELETE,"/api/v1/admin/users/"+USER,Map.of("reason","Suppression de fixture locale"),OTHER);assertThat(deleted.getStatusCode()).as(deleted.getBody()).isEqualTo(HttpStatus.NO_CONTENT);assertThat(OBJECTS).isEmpty();
  try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select count(*) from fastcoloc.listings")){r.next();assertThat(r.getInt(1)).isZero();}
 }
}
