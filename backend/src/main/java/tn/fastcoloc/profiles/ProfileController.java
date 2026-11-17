package tn.fastcoloc.profiles;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
@RequestMapping("/api/v1/users")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service=service; }
    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) { return service.me(UUID.fromString(jwt.getSubject())); }
    @PostMapping("/sync")
    public ProfileResponse sync(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SyncRequest request) {
        return service.sync(UUID.fromString(jwt.getSubject()),request);
    }
}
