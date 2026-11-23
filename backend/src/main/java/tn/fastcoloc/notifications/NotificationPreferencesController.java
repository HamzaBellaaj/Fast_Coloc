package tn.fastcoloc.notifications;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.fastcoloc.onboarding.OnboardingService;
@RestController
@RequestMapping("/api/v1/notification-preferences")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class NotificationPreferencesController {
 private final Preferences preferences;
 public NotificationPreferencesController(Preferences preferences){this.preferences=preferences;}
 public record Patch(@NotNull Boolean newMessageEmail){}
 @GetMapping public Map<String,Object> get(@AuthenticationPrincipal Jwt jwt){return preferences.get(UUID.fromString(jwt.getSubject()));}
 @PatchMapping public Map<String,Object> update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Patch body){return preferences.save(UUID.fromString(jwt.getSubject()),body.newMessageEmail());}
 @Service public static class Preferences {
  private final JdbcTemplate jdbc;private final OnboardingService accounts;private final NotificationGateway gateway;private final String mode;
  public Preferences(JdbcTemplate jdbc,OnboardingService accounts,NotificationGateway gateway,@org.springframework.beans.factory.annotation.Value("${fastcoloc.notifications.mode:external}")String mode){this.jdbc=jdbc;this.accounts=accounts;this.gateway=gateway;this.mode=mode;}
  @Transactional public Map<String,Object> get(UUID actor){accounts.bindAndLock(actor);Boolean enabled=jdbc.queryForObject("select coalesce((select new_message_email from fastcoloc.notification_preferences where user_id=?),true)",Boolean.class,actor);return Map.of("newMessageEmail",enabled,"deliveryAvailable",gateway.configured(),"deliveryMode",gateway.configured()?mode:"disabled");}
  @Transactional public Map<String,Object> save(UUID actor,boolean enabled){accounts.bindAndLock(actor);jdbc.update("insert into fastcoloc.notification_preferences(user_id,new_message_email) values(?,?) on conflict(user_id) do update set new_message_email=excluded.new_message_email,updated_at=now()",actor,enabled);return Map.of("newMessageEmail",enabled,"deliveryAvailable",gateway.configured(),"deliveryMode",gateway.configured()?mode:"disabled");}
 }
}
