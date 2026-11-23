package tn.fastcoloc.messaging;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
@RequestMapping("/api/v1/conversations")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class MessagingController {
 private final MessagingService service;
 public MessagingController(MessagingService service){this.service=service;}
 public record Start(@NotNull UUID listingId,@NotBlank @Size(max=4000) String content,@NotNull UUID clientId){}
 public record Send(@NotBlank @Size(max=4000) String content,@NotNull UUID clientId){}
 public record Read(@Positive long throughId){}
 private UUID actor(Jwt jwt){return UUID.fromString(jwt.getSubject());}
 @GetMapping public Map<String,Object> inbox(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(1000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size,@RequestParam(required=false) UUID listingId){return service.inbox(actor(jwt),page,size,listingId);}
 @GetMapping("/{id}") public Map<String,Object> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.detail(actor(jwt),id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> start(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Start body){return service.start(actor(jwt),body);}
 @GetMapping("/{id}/messages") public Map<String,Object> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(required=false) @Positive Long before,@RequestParam(defaultValue="30") @Min(1) @Max(50) int size){return service.history(actor(jwt),id,before,size);}
 @PostMapping("/{id}/messages") public Map<String,Object> send(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Send body){return service.send(actor(jwt),id,body);}
 @PutMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Read body){service.read(actor(jwt),id,body.throughId());}
}
