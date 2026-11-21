package tn.fastcoloc.recommendations;

import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recommendations")
@SecurityRequirement(name="supabaseBearer")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }
    @PostMapping("/ai")
    public RecommendationResponse recommend(@AuthenticationPrincipal Jwt jwt) {
        return service.recommend(UUID.fromString(jwt.getSubject()));
    }
}
