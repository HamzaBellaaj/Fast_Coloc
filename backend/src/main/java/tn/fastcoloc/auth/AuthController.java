package tn.fastcoloc.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth") @Validated
public class AuthController {
 private final AuthService service;public AuthController(AuthService service){this.service=service;}
 @GetMapping("/config") public Map<String,Object> config(){return service.config();}
 @PostMapping("/signup") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,String> signup(@Valid @RequestBody AuthRequests.Signup r,@RequestParam(defaultValue="web") String client){return service.signup(r,client);}
 @PostMapping("/login") public JsonNode login(@Valid @RequestBody AuthRequests.Login r){return service.login(r);}
 @PostMapping("/refresh") public JsonNode refresh(@Valid @RequestBody AuthRequests.Refresh r){return service.refresh(r);}
 @PostMapping("/exchange") public JsonNode exchange(@Valid @RequestBody AuthRequests.Exchange r){return service.exchange(r);}
 @PostMapping("/verify") public JsonNode verify(@Valid @RequestBody AuthRequests.Verify r){return service.verify(r);}
 @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,String> recover(@Valid @RequestBody AuthRequests.Email r,@RequestParam(defaultValue="web") String client){return service.recover(r,client);}
 @PostMapping("/resend-confirmation") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,String> resend(@Valid @RequestBody AuthRequests.Email r,@RequestParam(defaultValue="web") String client){return service.resend(r,client);}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@AuthenticationPrincipal Jwt j,@RequestParam(defaultValue="local")String scope){service.logout(j.getTokenValue(),scope);}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
 @PutMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void password(@AuthenticationPrincipal Jwt j,@Valid @RequestBody AuthRequests.Password r){service.password(j.getTokenValue(),r);}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
 @GetMapping("/session") public Map<String,Object> session(@AuthenticationPrincipal Jwt j){return service.session(j.getTokenValue());}
 @GetMapping("/google") public Map<String,String> google(@RequestParam @Pattern(regexp="[A-Za-z0-9_-]{43}") String codeChallenge,@RequestParam(defaultValue="web") String client,@RequestParam(required=false) String state){return service.google(codeChallenge,client,state);}
}
