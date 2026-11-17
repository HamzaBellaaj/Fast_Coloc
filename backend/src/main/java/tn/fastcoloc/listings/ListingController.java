package tn.fastcoloc.listings;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/listings")
@io.swagger.v3.oas.annotations.tags.Tag(name="Sprint 2 — Annonces")
public class ListingController {
 private final ListingService service;public ListingController(ListingService service){this.service=service;}
 private UUID actor(Jwt jwt){return jwt==null?null:UUID.fromString(jwt.getSubject());}
 @GetMapping public Map<String,Object> search(@AuthenticationPrincipal Jwt jwt,@ModelAttribute ListingQuery query){return service.search(actor(jwt),query,"catalog");}
 @GetMapping("/areas") public List<Map<String,Object>> areas(){return service.areas();}
 @SecurityRequirement(name="supabaseBearer")
 @GetMapping("/map") public Map<String,Object> map(@AuthenticationPrincipal Jwt jwt,@ModelAttribute ListingQuery query){return service.search(actor(jwt),query,"map");}
 @SecurityRequirement(name="supabaseBearer")
 @GetMapping("/mine") public Map<String,Object> mine(@AuthenticationPrincipal Jwt jwt,@ModelAttribute ListingQuery query){return service.search(actor(jwt),query,"mine");}
 @SecurityRequirement(name="supabaseBearer")
 @GetMapping("/favorites") public Map<String,Object> favorites(@AuthenticationPrincipal Jwt jwt,@ModelAttribute ListingQuery query){return service.search(actor(jwt),query,"favorites");}
 @SecurityRequirement(name="supabaseBearer")
 @GetMapping("/{id}") public Map<String,Object> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.detail(actor(jwt),id);}
 @SecurityRequirement(name="supabaseBearer")
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ListingWrite body){return service.create(actor(jwt),body);}
 @SecurityRequirement(name="supabaseBearer")
 @PutMapping("/{id}") public Map<String,Object> update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ListingWrite body){return service.update(actor(jwt),id,body);}
 @SecurityRequirement(name="supabaseBearer")
 @PostMapping("/{id}/publish") public Map<String,Object> publish(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.publish(actor(jwt),id,false);}
 @SecurityRequirement(name="supabaseBearer")
 @PostMapping("/{id}/renew") public Map<String,Object> renew(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.publish(actor(jwt),id,true);}
 @SecurityRequirement(name="supabaseBearer")
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){service.delete(actor(jwt),id);}
 @SecurityRequirement(name="supabaseBearer")
 @PutMapping("/{id}/favorite") @ResponseStatus(HttpStatus.NO_CONTENT) public void favorite(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){service.favorite(actor(jwt),id,true);}
 @SecurityRequirement(name="supabaseBearer")
 @DeleteMapping("/{id}/favorite") @ResponseStatus(HttpStatus.NO_CONTENT) public void unfavorite(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){service.favorite(actor(jwt),id,false);}
 @SecurityRequirement(name="supabaseBearer")
 @PostMapping(value="/{id}/photos",consumes="multipart/form-data") public Map<String,Object> photo(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestPart MultipartFile file,@RequestParam(defaultValue="") String caption){return service.upload(actor(jwt),id,file,caption);}
 @SecurityRequirement(name="supabaseBearer")
 @DeleteMapping("/{id}/photos/{photo}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deletePhoto(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID photo){service.deletePhoto(actor(jwt),id,photo);}
}
