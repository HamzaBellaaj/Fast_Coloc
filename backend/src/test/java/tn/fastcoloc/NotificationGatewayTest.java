package tn.fastcoloc;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import tn.fastcoloc.notifications.*;
class NotificationGatewayTest {
 HttpServer server;JsonNode body;String authorization,idempotency,upgrade;int status=200;String acknowledgement="sent";boolean wrongId=false;
 ObjectMapper json=new ObjectMapper();
 @BeforeEach void setup()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{body=json.readTree(e.getRequestBody());authorization=e.getRequestHeaders().getFirst("Authorization");upgrade=e.getRequestHeaders().getFirst("Upgrade");idempotency=e.getRequestHeaders().getFirst("Idempotency-Key");byte[] bytes=json.writeValueAsBytes(Map.of("eventId",wrongId?UUID.randomUUID().toString():body.path("eventId").asText(),"status",acknowledgement));e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();}
 @AfterEach void stop(){server.stop(0);}
 NotificationGateway gateway(){return new NotificationGateway(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/webhook","local-fixture-secret",json);}
 NotificationRepository.Delivery delivery(){return new NotificationRepository.Delivery(UUID.randomUUID(),"recipient@example.invalid",UUID.randomUUID(),UUID.randomUUID());}
 @Test void sendsOnlyMinimalMetadataWithAuthenticationAndIdempotency(){var d=delivery();gateway().deliver(d);assertThat(authorization).isEqualTo("Bearer local-fixture-secret");assertThat(idempotency).isEqualTo(d.eventId().toString());assertThat(body.size()).isEqualTo(4);assertThat(body.has("content")).isFalse();assertThat(body.path("type").asText()).isEqualTo("NEW_MESSAGE");}
 @Test void doesNotAttemptH2cUpgradeAgainstN8n(){gateway().deliver(delivery());assertThat(upgrade).isNull();}
 @Test void duplicateAcknowledgementIsAccepted(){acknowledgement="duplicate";gateway().deliver(delivery());}
 @Test void queuedOrWrongEventAcknowledgementsAreNotSuccess(){acknowledgement="queued";assertThatThrownBy(()->gateway().deliver(delivery())).isInstanceOf(NotificationGateway.DeliveryFailure.class).hasMessage("INVALID_ACKNOWLEDGEMENT");acknowledgement="sent";wrongId=true;assertThatThrownBy(()->gateway().deliver(delivery())).hasMessage("INVALID_ACKNOWLEDGEMENT");}
 @Test void authenticationFailureIsRetryableAndSanitized(){status=401;assertThatThrownBy(()->gateway().deliver(delivery())).hasMessage("WEBHOOK_AUTH");}
 @Test void disabledOrNonLocalUnencryptedUrlsAreNotUsed(){assertThat(new NotificationGateway(false,"http://127.0.0.1/x","token",json).configured()).isFalse();assertThat(new NotificationGateway(true,"http://example.com/x","token",json).configured()).isFalse();}
}
