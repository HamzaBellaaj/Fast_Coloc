package tn.fastcoloc.onboarding;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.fastcoloc.profiles.ProfileDataService;

@RestController @RequestMapping("/api/v1")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class OnboardingController {
 private final OnboardingService service;private final ProfileDataService data;
 public OnboardingController(OnboardingService service,ProfileDataService data){this.service=service;this.data=data;}
 @GetMapping("/users/me/details") public Map<String,Object> details(@AuthenticationPrincipal Jwt j){return data.details(UUID.fromString(j.getSubject()));}
 @PutMapping({"/onboarding/steps/1","/users/me"}) public Map<String,Object> identity(@AuthenticationPrincipal Jwt j,@Valid @RequestBody StepRequests.Identity r){return service.identity(UUID.fromString(j.getSubject()),r);}
 @PutMapping("/onboarding/steps/2") public Map<String,Object> geography(@AuthenticationPrincipal Jwt j,@Valid @RequestBody StepRequests.Geography r){return service.geography(UUID.fromString(j.getSubject()),r);}
 @PutMapping("/onboarding/steps/3") public Map<String,Object> personal(@AuthenticationPrincipal Jwt j,@Valid @RequestBody StepRequests.Personal r){return service.personal(UUID.fromString(j.getSubject()),r);}
 @PutMapping({"/onboarding/steps/4","/users/me/contacts"}) public Map<String,Object> contacts(@AuthenticationPrincipal Jwt j,@Valid @RequestBody StepRequests.Contacts r){return service.contacts(UUID.fromString(j.getSubject()),r);}
 @PutMapping("/onboarding/steps/5") public Map<String,Object> photos(@AuthenticationPrincipal Jwt j,@Valid @RequestBody StepRequests.Photos r){return service.photos(UUID.fromString(j.getSubject()),r);}
 @PutMapping("/users/me/preferences") public Map<String,Object> preferences(@AuthenticationPrincipal Jwt j,@Valid @RequestBody StepRequests.Preferences r){return service.preferences(UUID.fromString(j.getSubject()),r);}
 @PostMapping("/onboarding/complete") public Map<String,Object> complete(@AuthenticationPrincipal Jwt j){return service.complete(UUID.fromString(j.getSubject()));}
}
