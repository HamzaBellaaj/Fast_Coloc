package tn.fastcoloc.profiles;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "supabaseBearer")
public class UserBlockController {
    private final UserBlockService blocks;

    public UserBlockController(UserBlockService blocks) { this.blocks = blocks; }

    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        blocks.setBlock(actor(jwt), id, true);
    }

    @DeleteMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        blocks.setBlock(actor(jwt), id, false);
    }

    @GetMapping("/blocked")
    public List<UserBlockService.BlockedUser> list(@AuthenticationPrincipal Jwt jwt) {
        return blocks.list(actor(jwt));
    }

    @GetMapping("/{id}/block-status")
    public UserBlockService.BlockStatus status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return blocks.status(actor(jwt), id);
    }

    @GetMapping("/{id}/public-profile")
    public UserBlockService.MemberProfile profile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return blocks.profile(actor(jwt), id);
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
