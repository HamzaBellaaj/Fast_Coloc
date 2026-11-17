package tn.fastcoloc.security;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
/** Only called after signature verification. User metadata is never an authority. */
public final class SupabaseAccessTokenValidator implements OAuth2TokenValidator<Jwt> {
 public OAuth2TokenValidatorResult validate(Jwt jwt) {
  try {
   boolean valid=uuid(jwt.getSubject()) && uuid(jwt.getClaimAsString("session_id"))
    && "authenticated".equals(jwt.getClaimAsString("role")) && jwt.getAudience().contains("authenticated")
    && Boolean.FALSE.equals(jwt.getClaim("is_anonymous"))
    && jwt.getExpiresAt()!=null && jwt.getIssuedAt()!=null
    && jwt.getIssuedAt().isBefore(jwt.getExpiresAt()) && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60));
   if(valid) return OAuth2TokenValidatorResult.success();
  } catch(RuntimeException ignored) { /* malformed claims fail closed */ }
  return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid access token claims",null));
 }
 private static boolean uuid(String s) { try { return s!=null && UUID.fromString(s).toString().equalsIgnoreCase(s); } catch(IllegalArgumentException e) { return false; } }
}
