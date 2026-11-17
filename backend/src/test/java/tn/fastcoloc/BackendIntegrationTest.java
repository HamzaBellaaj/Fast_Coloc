package tn.fastcoloc;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.profiles.ProfileService;
import tn.fastcoloc.profiles.SyncRequest;
import tn.fastcoloc.security.AuthIdentityVerifier;
import tn.fastcoloc.security.DatabaseIdentity;
import tn.fastcoloc.entity.PersonalDetails;
import tn.fastcoloc.entity.RoommatePreferences;
import tn.fastcoloc.entity.OnboardingStep;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real local PostgreSQL, real non-owner JDBC role, real ES256 signatures and local JWKS HTTP. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendIntegrationTest {
    static final UUID USER=UUID.fromString("d0f010cf-2f5f-4f6c-a029-f3aa5085e621");
    static final UUID OTHER=UUID.fromString("9ea3f77e-0bbc-43b8-ad65-dae021cdd6b0");
    static final EmbeddedPostgres PG;
    static final ECKey KEY;
    static final HttpServer JWKS;
    static final String AUTH_URL;
    static {
        try {
            PG=EmbeddedPostgres.builder().setPort(0).start();
            try(var c=PG.getPostgresDatabase().getConnection(); var s=c.createStatement()) {
                s.execute("create role anon; create role authenticated; create schema auth; create table auth.users(id uuid primary key)");
                s.execute(Files.readString(Path.of("src/main/resources/db/drafts/V001__sprint1_proposal.sql")));
                // PG14 fixture alignment with the observed remote PG17 public schema ACL.
                s.execute("revoke create on schema public from public");
                s.execute(Files.readString(Path.of("supabase/migrations/20261111220503_provision_fastcoloc_backend.sql")));
                s.execute(Files.readString(Path.of("supabase/migrations/20261111220503_provision_fastcoloc_backend.sql")));
                // Local test cluster uses trust authentication, never a remote password.
                s.execute("alter role fastcoloc_backend login");
            }
            KEY=new ECKeyGenerator(Curve.P_256).keyID("local-test-key").generate();
            JWKS=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            JWKS.createContext("/auth/v1/.well-known/jwks.json",e->{
                byte[] bytes=new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type","application/json"); e.sendResponseHeaders(200,bytes.length);
                try(var out=e.getResponseBody()) { out.write(bytes); }
            });
            JWKS.start(); AUTH_URL="http://127.0.0.1:"+JWKS.getAddress().getPort();
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->PG.getJdbcUrl("fastcoloc_backend","postgres"));
        r.add("spring.datasource.username",()->"fastcoloc_backend");
        r.add("spring.datasource.password",()->"");
        r.add("fastcoloc.database.require-ssl",()->false);
        r.add("fastcoloc.auth.url",()->AUTH_URL);
        r.add("fastcoloc.auth.publishable-key",()->"local-public-test-key");
        r.add("fastcoloc.auth.secret-key",()->"local-server-test-key");
        r.add("spring.datasource.hikari.maximum-pool-size",()->3);
        r.add("debug",()->false);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate runtime;
    @Autowired ProfileService service;
    @Autowired EntityManager entities;
    @Autowired DatabaseIdentity identity;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AuthIdentityVerifier verifier;

    @BeforeEach void resetDatabase() throws Exception {
        try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement()) {
            s.execute("truncate auth.users cascade");
            s.execute("insert into auth.users values ('"+USER+"'),('"+OTHER+"')");
        }
    }
    @AfterAll void shutdown() throws Exception { JWKS.stop(0); PG.close(); }

    String token(UUID id, java.util.function.Consumer<JWTClaimsSet.Builder> override, ECKey key) throws Exception {
        Instant now=Instant.now();
        var b=new JWTClaimsSet.Builder().issuer(AUTH_URL+"/auth/v1").subject(id.toString()).audience("authenticated")
                .issueTime(Date.from(now.minusSeconds(1))).expirationTime(Date.from(now.plusSeconds(300)))
                .claim("role","authenticated").claim("session_id",UUID.randomUUID().toString()).claim("is_anonymous",false);
        override.accept(b);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY.getKeyID()).build(),b.build());
        jwt.sign(new ECDSASigner(key)); return jwt.serialize();
    }
    ResponseEntity<String> request(HttpMethod method,String path,String body,String token) {
        var h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); if(token!=null) h.setBearerAuth(token);
        return http.exchange(path,method,new HttpEntity<>(body,h),String.class);
    }
    ResponseEntity<String> sync(String body) throws Exception { return request(HttpMethod.POST,"/api/v1/users/sync",body,token(USER,b->{},KEY)); }
    void sql(String sql) throws Exception { try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement()) { s.execute(sql); } }
    long count(String table) throws Exception { try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select count(*) from fastcoloc."+table)) { r.next(); return r.getLong(1); } }

    void provision() throws Exception { sql(Files.readString(Path.of("supabase/migrations/20261111220503_provision_fastcoloc_backend.sql"))); }
    void futureOnboarding() throws Exception { sql(Files.readString(Path.of("tools/onboarding-rights.proposal.sql"))); }
    long runtimeSql(UUID subject,String query) throws Exception {
        try(var c=DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"))) {
            c.setAutoCommit(false);
            try {
                if(subject!=null) try(var p=c.prepareStatement("select set_config('fastcoloc.user_id',?,true)")) { p.setString(1,subject.toString()); p.execute(); }
                long result;
                try(var s=c.createStatement()) {
                    if(s.execute(query)) { try(var rows=s.getResultSet()) { result=rows.next()?rows.getLong(1):0; } }
                    else result=s.getUpdateCount();
                }
                c.commit(); return result;
            } catch(Exception e) { c.rollback(); throw e; }
        }
    }

    @Test void livenessIsPublicAndPrivateRequiresBearer() {
        assertThat(http.getForEntity("/api/v1/health",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var r=http.getForEntity("/api/v1/users/me",String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
    }
    @Test void provisionAndReadOwnProfileWithRealJpaAndRls() throws Exception {
        assertThat(sync("{\"firstName\":\"Amel\",\"age\":23}").getStatusCode()).isEqualTo(HttpStatus.OK);
        var r=request(HttpMethod.GET,"/api/v1/users/me",null,token(USER,b->{},KEY));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("Amel",USER.toString(),"\"role\":\"USER\"","\"validatedSteps\":[]");
        for(String t:List.of("profiles","account_access","profile_addresses","personal_details","contact_details","roommate_preferences")) assertThat(count(t)).isEqualTo(1);
        assertThat(count("onboarding_steps")).isZero();
    }
    @Test void syncIsIdempotentAndDoesNotOverwriteExistingNamesOrRoles() throws Exception {
        sync("{\"firstName\":\"Amel\"}");
        sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
        var r=sync("{\"firstName\":\"Changed\"}");
        assertThat(r.getBody()).contains("Amel","ADMIN").doesNotContain("Changed");
        assertThat(count("profiles")).isEqualTo(1);
    }
    @Test void blockedAccountCannotReadOrSyncWithStillValidJwt() throws Exception {
        sync("{}"); sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");
        for(String path:List.of("/api/v1/users/me","/api/v1/users/sync")) {
            var r=request(path.endsWith("sync")?HttpMethod.POST:HttpMethod.GET,path,"{}",token(USER,b->{},KEY));
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); assertThat(r.getBody()).contains("ACCOUNT_BLOCKED");
        }
    }
    @Test void adminAuthorityComesFromDatabaseAndNeverUserMetadata() throws Exception {
        sync("{}"); String jwt=token(USER,b->b.claim("user_metadata",Map.of("role","ADMIN")),KEY);
        assertThat(request(HttpMethod.GET,"/api/v1/admin/access",null,jwt).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
        assertThat(request(HttpMethod.GET,"/api/v1/admin/access",null,jwt).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void malformedClaimsIssuerAudienceDatesAndSignaturesAreRejected() throws Exception {
        List<java.util.function.Consumer<JWTClaimsSet.Builder>> changes=List.of(
            b->b.issuer("https://other.invalid/auth/v1"), b->b.audience("other"), b->b.subject( "1-1-1-1-1"),
            b->b.claim("session_id","invalid"), b->b.claim("role","service_role"), b->b.claim("is_anonymous",true),
            b->b.expirationTime(Date.from(Instant.now().minusSeconds(120))), b->b.notBeforeTime(Date.from(Instant.now().plusSeconds(120))),
            b->b.issueTime(Date.from(Instant.now().plusSeconds(120))), b->b.expirationTime(null), b->b.claim("is_anonymous",null));
        for(var change:changes) assertThat(request(HttpMethod.POST,"/api/v1/users/sync","{}",token(USER,change,KEY)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var wrong=new ECKeyGenerator(Curve.P_256).generate();
        assertThat(request(HttpMethod.POST,"/api/v1/users/sync","{}",token(USER,b->{},wrong)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.GET,"/api/v1/users/me",null,"broken.jwt.token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("profiles")).isZero(); verifyNoInteractions(verifier);
    }
    @Test void symmetricAndUnsignedTokensAreNeverAccepted() throws Exception {
        var valid=SignedJWT.parse(token(USER,b->{},KEY));
        var symmetric=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),valid.getJWTClaimsSet());
        symmetric.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
        String unsigned=new PlainJWT(valid.getJWTClaimsSet()).serialize();
        for(String jwt:List.of(symmetric.serialize(),unsigned))
            assertThat(request(HttpMethod.POST,"/api/v1/users/sync","{}",jwt).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(verifier); assertThat(count("profiles")).isZero();
    }
    @Test void baseRoleCannotReadSectionsUnusedByImplementedEndpoints() throws Exception {
        sync("{}");
        sql("update fastcoloc.personal_details set spoken_languages=ARRAY['fr','ar'] where user_id='"+USER+"'");
        sql("update fastcoloc.roommate_preferences set additional_preferences='{\"quiet\":true}'::jsonb where user_id='"+USER+"'");
        sql("insert into fastcoloc.onboarding_steps(user_id,step_number) values ('"+USER+"',1)");
        for(Class<?> type:List.of(PersonalDetails.class,RoommatePreferences.class)) {
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                identity.bind(USER); return entities.find(type,USER);
            })).isInstanceOf(jakarta.persistence.PersistenceException.class);
        }
    }
    @Test void unconfirmedEmailCreatesNoBusinessData() throws Exception {
        doThrow(new ApiException(HttpStatus.FORBIDDEN,"EMAIL_NOT_CONFIRMED","Confirm email")).when(verifier).requireConfirmed(any());
        var r=sync("{}"); assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).contains("EMAIL_NOT_CONFIRMED"); assertThat(count("profiles")).isZero();
    }
    @Test void authOutageFailsClosedWithoutWrites() throws Exception {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AUTH_UNAVAILABLE","Unavailable")).when(verifier).requireConfirmed(any());
        assertThat(sync("{}").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE); assertThat(count("profiles")).isZero();
    }
    @Test void payloadCannotSelectIdentityRoleOrInvalidAge() throws Exception {
        for(String payload:List.of("{\"id\":\""+OTHER+"\"}","{\"appRole\":\"ADMIN\"}","{\"status\":\"ACTIVE\"}","{\"completedAt\":\"2026-01-01T00:00:00Z\"}","{\"age\":131}","{\"firstName\":\""+"x".repeat(101)+"\"}"))
            assertThat(sync(payload).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("profiles")).isZero();
    }
    @Test void transactionRollsBackWhenAuthForeignKeyDoesNotExist() throws Exception {
        sql("delete from auth.users where id='"+USER+"'");
        assertThat(sync("{}").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(count("profiles")).isZero(); assertThat(count("account_access")).isZero();
    }
    @Test void lateFailureRollsBackTheEntireProfileAggregate() throws Exception {
        sql("create function fastcoloc.test_reject_contact() returns trigger language plpgsql as $$ begin raise exception 'local test failure'; end $$; create trigger test_reject_contact before insert on fastcoloc.contact_details for each row execute function fastcoloc.test_reject_contact()");
        try {
            assertThat(sync("{}").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            for(String table:List.of("profiles","account_access","profile_addresses","personal_details","contact_details")) assertThat(count(table)).isZero();
        } finally { sql("drop trigger test_reject_contact on fastcoloc.contact_details; drop function fastcoloc.test_reject_contact()"); }
    }
    @Test void anotherUserCannotReadProfileAndSubjectIsNotCallerSelectable() throws Exception {
        sync("{\"firstName\":\"SecretName\"}");
        var r=request(HttpMethod.GET,"/api/v1/users/me",null,token(OTHER,b->{},KEY));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); assertThat(r.getBody()).doesNotContain("SecretName");
        assertThat(request(HttpMethod.GET,"/api/v1/users/"+USER,null,token(OTHER,b->{},KEY)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void rlsAndColumnGrantsProtectOtherUsersAndPreventPrivilegeEscalation() throws Exception {
        sync("{}");
        try(var c=DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"));var s=c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("select set_config('fastcoloc.user_id','"+OTHER+"',true)");
            try(var r=s.executeQuery("select count(*) from fastcoloc.profiles")) { r.next(); assertThat(r.getInt(1)).isZero(); }
            assertThatThrownBy(()->s.execute("update fastcoloc.account_access set app_role='ADMIN'" )).isInstanceOf(SQLException.class);
            c.rollback();
            assertThatThrownBy(()->s.execute("insert into fastcoloc.account_access(user_id,app_role) values ('"+OTHER+"','ADMIN')")).isInstanceOf(SQLException.class);
            c.rollback();
            assertThatThrownBy(()->s.execute("insert into fastcoloc.profiles(id) values ('"+USER+"')")).isInstanceOf(SQLException.class);
            c.rollback();
            assertThatThrownBy(()->s.execute("create table public.forbidden_runtime_object(id integer)")).isInstanceOf(SQLException.class);
            c.rollback();
            assertThatThrownBy(()->s.execute("create table fastcoloc.forbidden_runtime_object(id integer)")).isInstanceOf(SQLException.class);
            c.rollback();
        }
        assertThat(runtime.queryForObject("select count(*) from fastcoloc.profiles",Integer.class)).isZero(); // pooled identity cleared
        for(String role:List.of("anon","authenticated")) {
            try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement()) {
                s.execute("set role "+role); // Supabase API roles deliberately have NOLOGIN.
                assertThatThrownBy(()->s.executeQuery("select * from fastcoloc.profiles")).isInstanceOf(SQLException.class);
            }
        }
    }
    @Test void simultaneousFirstLoginsCreateOneCompleteAggregate() throws Exception {
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Callable<UUID>> tasks=new ArrayList<>();
            for(int i=0;i<4;i++) tasks.add(()->service.sync(USER,new SyncRequest("Amel",null,null,null)).id());
            for(var future:executor.invokeAll(tasks)) assertThat(future.get(15,TimeUnit.SECONDS)).isEqualTo(USER);
        }
        assertThat(count("profiles")).isEqualTo(1); assertThat(count("account_access")).isEqualTo(1);
    }
    @Test void corsRejectsUnknownOrigin() {
        var h=new HttpHeaders(); h.setOrigin("https://untrusted.invalid"); h.setAccessControlRequestMethod(HttpMethod.GET);
        assertThat(http.exchange("/api/v1/users/me",HttpMethod.OPTIONS,new HttpEntity<>(h),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void blockedSqlContextCannotReadOrInsertBusinessRowsButCanReadItsStatus() throws Exception {
        sync("{}"); sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");
        assertThat(runtimeSql(USER,"select count(*) from fastcoloc.account_access where status='BLOCKED'")).isEqualTo(1);
        for(String table:List.of("profiles","profile_addresses","personal_details","contact_details","roommate_preferences","onboarding_steps"))
            assertThat(runtimeSql(USER,"select count(*) from fastcoloc."+table)).isZero();
        assertThatThrownBy(()->runtimeSql(USER,"insert into fastcoloc.profiles(id) values ('"+USER+"') on conflict do nothing"))
                .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("42501");
        assertThatThrownBy(()->runtimeSql(USER,"insert into fastcoloc.contact_details(user_id) values ('"+USER+"') on conflict do nothing"))
                .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("42501");
    }

    @Test void basePermissionsAreColumnScopedAndAllFourSequencesDenied() throws Exception {
        assertThat(runtimeSql(null,"select has_column_privilege(current_user,'fastcoloc.account_access','status','SELECT')::int")).isEqualTo(1);
        for(String column:List.of("blocked_at","blocked_reason","updated_at"))
            assertThat(runtimeSql(null,"select has_column_privilege(current_user,'fastcoloc.account_access','"+column+"','SELECT')::int")).isZero();
        for(String table:List.of("countries","governorates","localities","preferred_localities","profile_photos","consent_records","account_access_events"))
            assertThatThrownBy(()->runtimeSql(USER,"select count(*) from fastcoloc."+table)).isInstanceOf(SQLException.class);
        for(String sequence:List.of("governorates_id_seq","localities_id_seq","consent_records_id_seq","account_access_events_id_seq")) {
            assertThat(runtimeSql(null,"select has_sequence_privilege(current_user,'fastcoloc."+sequence+"','USAGE,SELECT,UPDATE')::int")).isZero();
            assertThatThrownBy(()->runtimeSql(USER,"select nextval('fastcoloc."+sequence+"')")).isInstanceOf(SQLException.class);
        }
        assertThatThrownBy(()->runtimeSql(USER,"select count(*) from auth.users")).isInstanceOf(SQLException.class);
    }

    @Test void adminBusinessRoleCannotChangeSqlIdentityPermissionsOrOtherProfiles() throws Exception {
        sync("{}"); service.sync(OTHER,new SyncRequest("PrivateOther",null,null,null));
        sql("update fastcoloc.account_access set app_role='ADMIN' where user_id='"+USER+"'");
        assertThat(runtimeSql(USER,"select count(*) from fastcoloc.profiles")).isEqualTo(1);
        for(String query:List.of("update fastcoloc.account_access set app_role='ADMIN'", "update fastcoloc.account_access set status='ACTIVE',blocked_at=null",
                "insert into fastcoloc.account_access(user_id,status) values ('"+USER+"','ACTIVE')", "set role postgres",
                "truncate fastcoloc.profiles cascade", "alter table fastcoloc.profiles disable row level security"))
            assertThatThrownBy(()->runtimeSql(USER,query)).isInstanceOf(SQLException.class);
    }

    @Test void missingAndMalformedSqlContextsFailClosedAndValuesAreNotSqlCode() throws Exception {
        sync("{}");
        assertThat(runtimeSql(null,"select count(*) from fastcoloc.profiles")).isZero();
        try(var c=DriverManager.getConnection(PG.getJdbcUrl("fastcoloc_backend","postgres"));var s=c.createStatement()) {
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("select set_config('fastcoloc.user_id',?,true)")) { p.setString(1,"x' OR true --"); p.execute(); }
            assertThatThrownBy(()->s.executeQuery("select * from fastcoloc.profiles"))
                .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("22P02");
            c.rollback();
        }
        // Explicit threat boundary: a holder of the trusted JDBC credential CAN set another UUID.
        assertThat(runtimeSql(USER,"select count(*) from fastcoloc.profiles")).isEqualTo(1);
        assertThat(runtimeSql(OTHER,"select count(*) from fastcoloc.profiles")).isZero();
        var r=sync("{\"firstName\":\"Robert'); DROP TABLE fastcoloc.profiles; --\"}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK); assertThat(count("profiles")).isEqualTo(1);
    }

    @Test void provisioningIsIdempotentPreservesCredentialsAndRemovesUnexpectedColumnGrants() throws Exception {
        String hash;
        sql("alter role fastcoloc_backend password '"+UUID.randomUUID()+"'"); // disposable generated test credential only
        try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select rolpassword from pg_authid where rolname='fastcoloc_backend'")) { r.next(); hash=r.getString(1); }
        sql("grant update(status),insert(app_role) on fastcoloc.account_access to fastcoloc_backend");
        provision(); provision();
        try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select rolpassword,rolcanlogin from pg_authid where rolname='fastcoloc_backend'")) {
            r.next(); assertThat(r.getString(1)).isEqualTo(hash); assertThat(r.getBoolean(2)).isTrue();
        }
        assertThat(runtimeSql(null,"select has_column_privilege(current_user,'fastcoloc.account_access','status','UPDATE')::int")).isZero();
        assertThat(runtimeSql(null,"select has_column_privilege(current_user,'fastcoloc.account_access','app_role','INSERT')::int")).isZero();
    }

    @Test void provisioningRefusesUnsafeRoleAndSharedPublicCreateWithoutChangingThem() throws Exception {
        try {
            sql("alter role fastcoloc_backend bypassrls");
            assertThatThrownBy(this::provision).isInstanceOf(SQLException.class).hasMessageContaining("unsafe");
        } finally { sql("alter role fastcoloc_backend nobypassrls"); }
        try {
            sql("grant create on schema public to public");
            assertThatThrownBy(this::provision).isInstanceOf(SQLException.class).hasMessageContaining("PUBLIC CREATE");
        } finally { sql("revoke create on schema public from public"); }
        provision();
    }

    @Test void restrictiveBoundariesSurviveAnAdditionalPermissivePolicy() throws Exception {
        sync("{}"); service.sync(OTHER,new SyncRequest("Other",null,null,null));
        sql("create policy test_extra_permissive on fastcoloc.profiles for select to fastcoloc_backend using(true)");
        try {
            assertThat(runtimeSql(USER,"select count(*) from fastcoloc.profiles")).isEqualTo(1);
            sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");
            assertThat(runtimeSql(USER,"select count(*) from fastcoloc.profiles")).isZero();
        } finally { sql("drop policy test_extra_permissive on fastcoloc.profiles"); }
    }

    @Test void provisioningRejectsExternalGrantsAndClientMembershipInsteadOfNormalizingThemSilently() throws Exception {
        try {
            sql("grant select on auth.users to fastcoloc_backend");
            assertThatThrownBy(this::provision).isInstanceOf(SQLException.class).hasMessageContaining("Unexpected role capabilities");
        } finally { sql("revoke select on auth.users from fastcoloc_backend"); }
        try {
            sql("grant fastcoloc_backend to authenticated");
            assertThatThrownBy(this::provision).isInstanceOf(SQLException.class).hasMessageContaining("private schema client access");
        } finally { sql("revoke fastcoloc_backend from authenticated"); }
        provision();
    }

    @Test void optionalOnboardingRightsSupportFiveStepsButNotRoleStatusOrCompletion() throws Exception {
        sync("{}");
        try {
            futureOnboarding(); futureOnboarding();
            sql("insert into fastcoloc.countries(code,name) values('TN','Tunisia') on conflict do nothing");
            assertThat(runtimeSql(USER,"update fastcoloc.profiles set first_name='Amel',age=23,version=version+1,updated_at=now() where id='"+USER+"'")).isEqualTo(1);
            assertThat(runtimeSql(USER,"update fastcoloc.profile_addresses set country_code='TN',street='Private street' where user_id='"+USER+"'")).isEqualTo(1);
            assertThat(runtimeSql(USER,"update fastcoloc.personal_details set spoken_languages=ARRAY['fr','ar'],nationality_code='TN' where user_id='"+USER+"'")).isEqualTo(1);
            assertThat(runtimeSql(USER,"update fastcoloc.contact_details set mobile_phone='12345678' where user_id='"+USER+"'")).isEqualTo(1);
            assertThat(runtimeSql(USER,"insert into fastcoloc.profile_photos(id,user_id,object_path,mime_type,byte_size,display_order) values('"+UUID.randomUUID()+"','"+USER+"','"+USER+"/test.webp','image/webp',100,0)")).isEqualTo(1);
            assertThat(runtimeSql(USER,"insert into fastcoloc.onboarding_steps(user_id,step_number) select '"+USER+"',generate_series(1,5)::smallint")).isEqualTo(5);
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                identity.bind(USER);
                assertThat(entities.find(PersonalDetails.class,USER).getSpokenLanguages()).containsExactly("fr","ar");
                var key=new OnboardingStep.Key(); key.userId=USER; key.stepNumber=1;
                assertThat(entities.find(OnboardingStep.class,key).getValidatedAt()).isNotNull();
            });
            for(String query:List.of("update fastcoloc.profiles set completed_at=now()", "update fastcoloc.profiles set id='"+OTHER+"'",
                    "update fastcoloc.account_access set app_role='ADMIN'", "update fastcoloc.account_access set status='ACTIVE'"))
                assertThatThrownBy(()->runtimeSql(USER,query)).isInstanceOf(SQLException.class);
        } finally { provision(); }
        assertThatThrownBy(()->runtimeSql(USER,"update fastcoloc.profiles set first_name='Denied'")).isInstanceOf(SQLException.class);
    }

    @Test void optionalOnboardingCannotEditOtherUsersAndDeniesBlockedUpdatesAndInserts() throws Exception {
        sync("{}"); service.sync(OTHER,new SyncRequest("Other",null,null,null));
        try {
            futureOnboarding();
            assertThat(runtimeSql(USER,"update fastcoloc.contact_details set mobile_phone='intrusion' where user_id='"+OTHER+"'")).isZero();
            assertThatThrownBy(()->runtimeSql(USER,"insert into fastcoloc.onboarding_steps(user_id,step_number) values('"+OTHER+"',1)")).isInstanceOf(SQLException.class);
            sql("update fastcoloc.account_access set status='BLOCKED',blocked_at=now() where user_id='"+USER+"'");
            assertThat(runtimeSql(USER,"update fastcoloc.profiles set first_name='Denied'")).isZero();
            assertThat(runtimeSql(USER,"update fastcoloc.contact_details set mobile_phone='Denied'")).isZero();
            assertThat(runtimeSql(USER,"select count(*) from fastcoloc.countries")).isZero();
            assertThatThrownBy(()->runtimeSql(USER,"insert into fastcoloc.onboarding_steps(user_id,step_number) values('"+USER+"',1)")).isInstanceOf(SQLException.class);
        } finally { provision(); }
    }

    @Test void quarantineRollbackIsIdempotentAndPreservesAllBusinessData() throws Exception {
        sync("{}");
        try {
            String rollback=Files.readString(Path.of("tools/runtime-role.rollback.sql"));
            sql(rollback); sql(rollback);
            assertThat(count("profiles")).isEqualTo(1); assertThat(count("account_access")).isEqualTo(1);
            try(var c=PG.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("select rolcanlogin,has_schema_privilege('fastcoloc_backend','fastcoloc','USAGE'),has_column_privilege('fastcoloc_backend','fastcoloc.account_access','app_role','INSERT') from pg_roles where rolname='fastcoloc_backend'")) {
                r.next(); assertThat(r.getBoolean(1)).isFalse(); assertThat(r.getBoolean(2)).isFalse(); assertThat(r.getBoolean(3)).isFalse();
            }
        } finally { provision(); sql("alter role fastcoloc_backend login"); }
    }
}
