package tn.fastcoloc.media;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.fastcoloc.onboarding.StepRequests;

@RestController @RequestMapping("/api/v1/profile-photos")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class PhotoController {
 private final PhotoService service;public PhotoController(PhotoService service){this.service=service;}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> upload(@AuthenticationPrincipal Jwt j,@RequestPart("file")MultipartFile file,@RequestParam(required=false)String caption){return service.upload(UUID.fromString(j.getSubject()),file,caption);}
 @PatchMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void update(@AuthenticationPrincipal Jwt j,@PathVariable UUID id,@Valid @RequestBody StepRequests.Caption r){service.update(UUID.fromString(j.getSubject()),id,r);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){service.delete(UUID.fromString(j.getSubject()),id);}
 @GetMapping("/{id}/signed-url") public Map<String,Object> signed(@AuthenticationPrincipal Jwt j,@PathVariable UUID id){return service.signedUrl(UUID.fromString(j.getSubject()),id);}
}
