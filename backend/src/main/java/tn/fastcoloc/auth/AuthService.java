package tn.fastcoloc.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tn.fastcoloc.api.ApiException;

@Service
public class AuthService {
 private final SupabaseGateway api;private final String callback;
 public AuthService(SupabaseGateway api,@Value("${fastcoloc.auth.callback-url}")String callback){this.api=api;this.callback=callback;}
 public Map<String,Object> config(){var s=api.auth(HttpMethod.GET,"/settings",null,null);return Map.of("emailEnabled",s.path("external").path("email").asBoolean(),"googleEnabled",s.path("external").path("google").asBoolean(),"confirmationRequired",!s.path("mailer_autoconfirm").asBoolean(true),"signupEnabled",!s.path("disable_signup").asBoolean(),"callbackUrl",callback);}
 public Map<String,String> signup(AuthRequests.Signup r){return signup(r,"web");}
 public Map<String,String> signup(AuthRequests.Signup r,String client){
  if(!Boolean.TRUE.equals(config().get("confirmationRequired")))throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONFIRMATION_CONFIGURATION_REQUIRED","La confirmation e-mail obligatoire doit être activée dans Supabase.");
  checkPassword(r.password());var body=new HashMap<String,Object>();body.put("email",r.email());body.put("password",r.password());challenge(body,r.codeChallenge());
  api.auth(HttpMethod.POST,"/signup?redirect_to="+encode(callback(client,null)),body,null);return generic();
 }
 public JsonNode login(AuthRequests.Login r){try{return api.auth(HttpMethod.POST,"/token?grant_type=password",Map.of("email",r.email(),"password",r.password()),null);}catch(ApiException e){if(e.status()==HttpStatus.BAD_REQUEST)throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Identifiants invalides ou e-mail non confirmé.");throw e;}}
 public JsonNode refresh(AuthRequests.Refresh r){return api.auth(HttpMethod.POST,"/token?grant_type=refresh_token",Map.of("refresh_token",r.refreshToken()),null);}
 public JsonNode exchange(AuthRequests.Exchange r){return api.auth(HttpMethod.POST,"/token?grant_type=pkce",Map.of("auth_code",r.code(),"code_verifier",r.codeVerifier()),null);}
 public JsonNode verify(AuthRequests.Verify r){return api.auth(HttpMethod.POST,"/verify",Map.of("token_hash",r.tokenHash(),"type",r.type()),null);}
 public Map<String,String> recover(AuthRequests.Email r){return recover(r,"web");}
 public Map<String,String> recover(AuthRequests.Email r,String client){var body=new HashMap<String,Object>();body.put("email",r.email());challenge(body,r.codeChallenge());api.auth(HttpMethod.POST,"/recover?redirect_to="+encode(callback(client,null)),body,null);return generic();}
 public Map<String,String> resend(AuthRequests.Email r){return resend(r,"web");}
 public Map<String,String> resend(AuthRequests.Email r,String client){api.auth(HttpMethod.POST,"/resend?redirect_to="+encode(callback(client,null)),Map.of("email",r.email(),"type","signup"),null);return generic();}
 public void password(String token,AuthRequests.Password r){checkPassword(r.password());api.auth(HttpMethod.PUT,"/user",Map.of("password",r.password()),token);}
 public void logout(String token,String scope){if(!Set.of("local","global","others").contains(scope))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","Scope de déconnexion invalide.");api.auth(HttpMethod.POST,"/logout?scope="+scope,null,token);}
 public Map<String,Object> session(String token){var u=api.auth(HttpMethod.GET,"/user",null,token);Map<String,Object> result=new LinkedHashMap<>();result.put("id",u.path("id").asText());result.put("email",u.path("email").asText());result.put("emailConfirmed",!u.path("email_confirmed_at").isNull()&&!u.path("email_confirmed_at").asText("").isBlank());return result;}
 public Map<String,String> google(String challenge){return google(challenge,"web",null);}
 public Map<String,String> google(String challenge,String client,String state){
  if("android".equals(client)&&(state==null||!state.matches("[A-Za-z0-9_-]{43}")))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATE","State Android requis.");
  if(!Boolean.TRUE.equals(config().get("googleEnabled")))throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GOOGLE_NOT_CONFIGURED","Google OAuth doit être activé avec ses identifiants dans Supabase.");
  return Map.of("authorizationUrl",UriComponentsBuilder.fromUriString(api.url()+"/auth/v1/authorize").queryParam("provider","google").queryParam("redirect_to",callback(client,state)).queryParam("code_challenge",challenge).queryParam("code_challenge_method","s256").build().encode().toUriString());
 }
 private String callback(String client,String state){
  if("web".equals(client))return callback;
  if(!"android".equals(client))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CLIENT","Client non autorisé.");
  String mobile="fastcoloc://auth/callback";
  if(state!=null){if(!state.matches("[A-Za-z0-9_-]{43}"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATE","State invalide.");mobile+="?state="+state;}
  return mobile;
 }
 private void challenge(Map<String,Object> body,String challenge){if(challenge!=null){body.put("code_challenge",challenge);body.put("code_challenge_method","s256");}}
 private void checkPassword(String password){if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PASSWORD","Mot de passe trop long en UTF-8.");}
 private Map<String,String> generic(){return Map.of("message","Si cette demande est admissible, un e-mail vous sera envoyé.");}
 private String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
}
