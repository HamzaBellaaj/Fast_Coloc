package tn.fastcoloc.admin;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/users")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class AdminController {
 private final AdminService service;public AdminController(AdminService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt j,@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")int offset,@RequestParam(required=false)String search,@RequestParam(required=false)String status){return service.users(UUID.fromString(j.getSubject()),limit,offset,search,status);}
 @PatchMapping(value="/{id}/status",produces=MediaType.APPLICATION_JSON_VALUE) public String status(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody AdminRequests.Status r){return service.status(UUID.fromString(j.getSubject()),id,r);}
 @PatchMapping(value="/{id}/contacts",produces=MediaType.APPLICATION_JSON_VALUE) public String contacts(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody AdminRequests.Contacts r){return service.contacts(UUID.fromString(j.getSubject()),id,r);}
 @GetMapping(value="/{id}/contacts",produces=MediaType.APPLICATION_JSON_VALUE) public String contacts(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return service.contacts(UUID.fromString(j.getSubject()),id);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody AdminRequests.Deletion r){service.delete(UUID.fromString(j.getSubject()),id,r);}
}
