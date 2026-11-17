package tn.fastcoloc.onboarding;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.fastcoloc.security.*;

@RestController @RequestMapping("/api/v1/geography")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class GeographyController {
 private final JdbcTemplate jdbc;private final DatabaseIdentity identity;private final AccountAuthorizationService access;
 public GeographyController(JdbcTemplate jdbc,DatabaseIdentity identity,AccountAuthorizationService access){this.jdbc=jdbc;this.identity=identity;this.access=access;}
 @GetMapping("/countries") @Transactional(readOnly=true) public List<Map<String,Object>> countries(@AuthenticationPrincipal Jwt j){bind(j);return jdbc.queryForList("select code,name from fastcoloc.countries order by name");}
 @GetMapping("/governorates") @Transactional(readOnly=true) public List<Map<String,Object>> governorates(@AuthenticationPrincipal Jwt j,@RequestParam(defaultValue="TN") String countryCode){bind(j);return jdbc.queryForList("select id,country_code,name from fastcoloc.governorates where country_code=? order by name",countryCode);}
 @GetMapping("/localities") @Transactional(readOnly=true) public List<Map<String,Object>> localities(@AuthenticationPrincipal Jwt j,@RequestParam long governorateId){bind(j);return jdbc.queryForList("select id,governorate_id,name from fastcoloc.localities where governorate_id=? order by name",governorateId);}
 private void bind(Jwt j){var id=UUID.fromString(j.getSubject());identity.bind(id);access.role(id);}
}
