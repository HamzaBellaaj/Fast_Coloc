package tn.fastcoloc.auth;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.NullNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tn.fastcoloc.api.ApiException;

/** No request bodies, tokens or upstream errors are ever logged. */
@Component
public class SupabaseGateway {
 private final RestClient client;private final String publicKey;private final String secret;private final String url;
 public SupabaseGateway(@Value("${fastcoloc.auth.url}") String url,@Value("${fastcoloc.auth.publishable-key}") String publicKey,
      @Value("${fastcoloc.auth.secret-key:}") String secret){
  this.url=url;this.publicKey=publicKey;this.secret=secret;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(10));
  client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public String url(){return url;}
 public boolean serverConfigured(){return secret!=null&&!secret.isBlank();}
 public void requireServer(){if(!serverConfigured())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"SERVER_KEY_REQUIRED","Clé serveur Supabase non configurée.");}
 public JsonNode auth(HttpMethod method,String path,Object body,String bearer){return call(method,"/auth/v1"+path,body,bearer,false);}
 public JsonNode admin(HttpMethod method,String path,Object body){requireServer();return call(method,path,body,null,true);}
 public JsonNode call(HttpMethod method,String path,Object body,String bearer,boolean admin){
  if(admin)requireServer();
  try {
   // AuthService already percent-encodes redirect_to. The String-template overload
   // would encode '%' again and silently make Supabase fall back to its Site URL.
   var request=client.method(method).uri(java.net.URI.create(url.replaceAll("/+$","")+path)).header("apikey",admin?secret:publicKey);
   if(bearer!=null)request.header(HttpHeaders.AUTHORIZATION,"Bearer "+bearer);
   else if(admin)request.header(HttpHeaders.AUTHORIZATION,"Bearer "+secret);
   if(body!=null)request.contentType(MediaType.APPLICATION_JSON).body(body);
   JsonNode result=request.retrieve().body(JsonNode.class);return result==null?NullNode.instance:result;
  }catch(HttpClientErrorException e){
   if(e.getStatusCode().value()==429)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AUTH_RATE_LIMITED","Trop de tentatives, réessayez plus tard.");
   if(e.getStatusCode().value()==401||e.getStatusCode().value()==403)throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_REJECTED","Identifiants ou session non valides.");
   if(e.getStatusCode().value()==404)throw new ApiException(HttpStatus.NOT_FOUND,"REMOTE_NOT_FOUND","Ressource distante absente.");
   if(!admin && e.getStatusCode().value()<500)throw new ApiException(HttpStatus.BAD_REQUEST,"AUTH_REQUEST_REJECTED","Demande Auth refusée. Vérifiez les informations et la confirmation e-mail.");
   throw unavailable();
  }catch(RestClientException e){throw unavailable();}
 }
 public void upload(String objectPath,byte[] bytes,String mime){
  upload("profile-photos",objectPath,bytes,mime);
 }
 public void upload(String bucket,String objectPath,byte[] bytes,String mime){
  requireServer();
  try {client.post().uri("/storage/v1/object/"+bucket+"/"+objectPath).header("apikey",secret).header(HttpHeaders.AUTHORIZATION,"Bearer "+secret).header("x-upsert","false").contentType(MediaType.parseMediaType(mime)).body(bytes).retrieve().toBodilessEntity();}
  catch(RestClientException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Envoi Storage indisponible.");}
 }
 private ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"SUPABASE_UNAVAILABLE","Service Supabase indisponible ou configuration invalide.");}
}
