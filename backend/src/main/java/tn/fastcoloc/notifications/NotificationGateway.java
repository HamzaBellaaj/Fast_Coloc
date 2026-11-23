package tn.fastcoloc.notifications;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class NotificationGateway {
 public static class DeliveryFailure extends RuntimeException {private final String code;public DeliveryFailure(String code){super(code);this.code=code;}public String code(){return code;}}
 private final boolean enabled;private final URI webhook;private final String token;private final ObjectMapper json;
 private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public NotificationGateway(@Value("${fastcoloc.notifications.enabled:false}")boolean enabled,@Value("${fastcoloc.notifications.webhook-url:}")String url,@Value("${fastcoloc.notifications.webhook-token:}")String token,ObjectMapper json){
  this.enabled=enabled;this.token=token;this.json=json;URI parsed=null;
  try{URI uri=URI.create(url);boolean local=java.util.Set.of("localhost","127.0.0.1","::1").contains(uri.getHost());if(uri.getUserInfo()==null&&("https".equals(uri.getScheme())||local&&"http".equals(uri.getScheme())))parsed=uri;}catch(Exception ignored){}
  webhook=parsed;
 }
 public boolean configured(){return enabled&&webhook!=null&&token!=null&&!token.isBlank();}
 public void deliver(NotificationRepository.Delivery delivery){
  if(!configured())throw new DeliveryFailure("NOT_CONFIGURED");
  try{
   String body=json.writeValueAsString(Map.of("eventId",delivery.eventId(),"type","NEW_MESSAGE","to",delivery.email(),"conversationId",delivery.conversation()));
   var request=HttpRequest.newBuilder(webhook).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").header("Authorization","Bearer "+token).header("Idempotency-Key",delivery.eventId().toString()).POST(HttpRequest.BodyPublishers.ofString(body)).build();
   var response=client.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200)throw new DeliveryFailure(response.statusCode()==401||response.statusCode()==403?"WEBHOOK_AUTH":"WEBHOOK_UNAVAILABLE");
   var result=json.readTree(response.body());if(!delivery.eventId().toString().equals(result.path("eventId").asText())||!java.util.Set.of("sent","duplicate").contains(result.path("status").asText()))throw new DeliveryFailure("INVALID_ACKNOWLEDGEMENT");
  }catch(DeliveryFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new DeliveryFailure("INTERRUPTED");}catch(Exception e){throw new DeliveryFailure("TRANSPORT_FAILURE");}
 }
}
