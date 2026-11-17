package tn.fastcoloc;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.security.SupabaseAuthIdentityVerifier;
import static org.assertj.core.api.Assertions.*;

class SupabaseAuthIdentityVerifierTest {
    HttpServer server; String body; int status; String authorization; String apiKey;
    final String id=UUID.randomUUID().toString();
    @BeforeEach void start() throws Exception {
        status=200;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/auth/v1/user",e->{
            authorization=e.getRequestHeaders().getFirst("Authorization"); apiKey=e.getRequestHeaders().getFirst("apikey");
            byte[] b=body.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().set("Content-Type","application/json");
            e.sendResponseHeaders(status,b.length); try(var out=e.getResponseBody()) { out.write(b); }
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    void verifyIdentity() {
        var verifier=new SupabaseAuthIdentityVerifier("http://127.0.0.1:"+server.getAddress().getPort(),"public-test-key");
        verifier.requireConfirmed(Jwt.withTokenValue("local-token").header("alg","ES256").subject(id).build());
    }
    @Test void confirmedEmailAndGoogleUseTheSameVerifiedUuid() {
        for(String provider:new String[]{"email","google"}) {
            body="{\"id\":\""+id+"\",\"email\":\"test@example.invalid\",\"is_anonymous\":false,\"email_confirmed_at\":\"2026-01-01T00:00:00Z\",\"app_metadata\":{\"provider\":\""+provider+"\"}}";
            assertThatCode(this::verifyIdentity).doesNotThrowAnyException();
            assertThat(authorization).isEqualTo("Bearer local-token"); assertThat(apiKey).isEqualTo("public-test-key");
        }
    }
    @Test void editableMetadataCannotClaimEmailConfirmation() {
        body="{\"id\":\""+id+"\",\"email\":\"test@example.invalid\",\"is_anonymous\":false,\"email_confirmed_at\":null,\"user_metadata\":{\"email_confirmed\":true}}";
        assertThatThrownBy(this::verifyIdentity).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("EMAIL_NOT_CONFIRMED"));
    }
    @Test void mismatchedIdentityAndAnonymousUsersAreRejected() {
        body="{\"id\":\""+UUID.randomUUID()+"\",\"is_anonymous\":false}";
        assertThatThrownBy(this::verifyIdentity).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_IDENTITY"));
        body="{\"id\":\""+id+"\",\"is_anonymous\":true}";
        assertThatThrownBy(this::verifyIdentity).isInstanceOf(ApiException.class);
    }
    @Test void authOutageAndRevokedIdentityFailClosed() {
        body="{}"; status=503;
        assertThatThrownBy(this::verifyIdentity).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(503));
        status=401;
        assertThatThrownBy(this::verifyIdentity).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(401));
    }
}
