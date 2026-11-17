package tn.fastcoloc.api;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
public class HealthController {
    /** Liveness only: startup performs JDBC/schema/runtime-role validation. */
    @GetMapping("/api/v1/health") public Map<String,String> health() { return Map.of("status","UP","application","fastcoloc-backend"); }
    /** Read-only endpoint to verify DB-backed ADMIN authorization. */
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
    @GetMapping("/api/v1/admin/access") public Map<String,String> admin(Authentication auth) { return Map.of("role","ADMIN"); }
}
