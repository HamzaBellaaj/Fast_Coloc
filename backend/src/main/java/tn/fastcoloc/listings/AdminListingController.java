package tn.fastcoloc.listings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/listings")
@SecurityRequirement(name="supabaseBearer")
@io.swagger.v3.oas.annotations.tags.Tag(name="US29 — Modération des annonces (ADMIN actif)")
public class AdminListingController {
 private final AdminListingService service;
 public AdminListingController(AdminListingService service){this.service=service;}
 public record Moderation(@NotBlank @Size(max=1000) String reason,@NotNull @PositiveOrZero Long version){}
 public record Edit(@NotNull @Valid ListingWrite listing,@NotBlank @Size(max=1000) String reason){}
 public record Filters(@Size(max=160) String q,@Pattern(regexp="|OFFER|REQUEST") String kind,
  @Pattern(regexp="|DRAFT|PUBLISHED|EXPIRED|HIDDEN") String status,LocalDate from,LocalDate to,
  @Pattern(regexp="NEWEST|OLDEST|RENT_ASC|RENT_DESC") String sort,@Min(0) @Max(1000) Integer page,@Min(1) @Max(50) Integer size){
  Map<String,Object> values(){var m=new HashMap<String,Object>();m.put("q",q==null?"":q.trim());m.put("kind",kind);m.put("status",status);
   m.put("from",from);m.put("to",to);m.put("sort",sort==null?"NEWEST":sort);m.put("page",page==null?0:page);m.put("size",size==null?20:size);return m;}
 }
 private UUID actor(Jwt jwt){return UUID.fromString(jwt.getSubject());}
 @GetMapping public Map<String,Object> list(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute Filters filters){return service.list(actor(jwt),filters.values());}
 @GetMapping("/{id}") public Map<String,Object> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.detail(actor(jwt),id);}
 @GetMapping("/{id}/history") public List<Map<String,Object>> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.history(actor(jwt),id);}
 @PutMapping("/{id}") public Map<String,Object> edit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Edit body){return service.edit(actor(jwt),id,body.listing(),body.reason());}
 @PostMapping("/{id}/hide") public Map<String,Object> hide(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Moderation body){return service.moderate(actor(jwt),id,"HIDE",body.reason(),body.version());}
 @PostMapping("/{id}/restore") public Map<String,Object> restore(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Moderation body){return service.moderate(actor(jwt),id,"RESTORE",body.reason(),body.version());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Moderation body){service.moderate(actor(jwt),id,"DELETE",body.reason(),body.version());}
}
