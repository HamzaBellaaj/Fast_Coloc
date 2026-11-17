package tn.fastcoloc.onboarding;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.profiles.*;
import tn.fastcoloc.security.*;

@Service
public class OnboardingService {
 private final JdbcTemplate jdbc;private final DatabaseIdentity identity;private final AccountAuthorizationService access;private final ProfileDataService data;
 public OnboardingService(JdbcTemplate jdbc,DatabaseIdentity identity,AccountAuthorizationService access,ProfileDataService data){this.jdbc=jdbc;this.identity=identity;this.access=access;this.data=data;}
 public void bindAndLock(UUID id){
  identity.bind(id);access.role(id);
  jdbc.execute(c->{var s=c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))");s.setString(1,id.toString());return s;},(org.springframework.jdbc.core.PreparedStatementCallback<Void>)s->{s.execute();return null;});
  access.role(id);
 }
 @Transactional public Map<String,Object> identity(UUID id,StepRequests.Identity r){
  bindAndLock(id);jdbc.update("update fastcoloc.profiles set first_name=?,last_name=?,gender=?,age=?,version=version+1,updated_at=now() where id=?",r.firstName().trim(),r.lastName().trim(),r.gender(),r.age(),id);mark(id,1);return data.details(id);
 }
 @Transactional public Map<String,Object> geography(UUID id,StepRequests.Geography r){
  bindAndLock(id);
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.localities l join fastcoloc.governorates g on g.id=l.governorate_id where l.id=? and g.id=? and g.country_code=?)",Boolean.class,r.localityId(),r.governorateId(),r.countryCode())))throw invalid("Pays, gouvernorat et localité incohérents.");
  jdbc.update("update fastcoloc.profile_addresses set country_code=?,locality_id=?,street=?,postal_code=? where user_id=?",r.countryCode(),r.localityId(),r.street().trim(),r.postalCode().trim(),id);mark(id,2);return data.details(id);
 }
 @Transactional public Map<String,Object> personal(UUID id,StepRequests.Personal r){
  bindAndLock(id);
  if(r.nationalityCode()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.countries where code=?)",Boolean.class,r.nationalityCode())))throw invalid("Nationalité inconnue.");
  jdbc.update(c->{var s=c.prepareStatement("update fastcoloc.personal_details set main_activities=?,hobbies=?,nationality_code=?,spoken_languages=?,religion=?,smoking=?,cleanliness=?,biography=? where user_id=?");
   s.setArray(1,c.createArrayOf("text",r.mainActivities().toArray()));s.setArray(2,c.createArrayOf("text",r.hobbies().toArray()));s.setString(3,r.nationalityCode());s.setArray(4,c.createArrayOf("text",r.spokenLanguages().toArray()));s.setString(5,r.religion());s.setObject(6,r.smoking());s.setString(7,r.cleanliness());s.setString(8,r.biography());s.setObject(9,id);return s;});mark(id,3);return data.details(id);
 }
 @Transactional public Map<String,Object> contacts(UUID id,StepRequests.Contacts r){
  bindAndLock(id);jdbc.update("update fastcoloc.contact_details set mobile_phone=?,residential_phone=?,call_availability=?,msn_contact=?,skype_contact=?,website_url=? where user_id=?",r.mobilePhone(),r.residentialPhone(),r.callAvailability(),r.msnContact(),r.skypeContact(),r.websiteUrl(),id);mark(id,4);return data.details(id);
 }
 @Transactional public Map<String,Object> photos(UUID id,StepRequests.Photos r){
  bindAndLock(id);
  if(new HashSet<>(r.photoIds()).size()!=r.photoIds().size())throw invalid("Photos en double.");
  for(UUID photo:r.photoIds())if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.profile_photos where id=? and user_id=?)",Boolean.class,photo,id)))throw invalid("Photo absente ou non autorisée.");
  mark(id,5);return data.details(id);
 }
 @Transactional public Map<String,Object> preferences(UUID id,StepRequests.Preferences r){
  bindAndLock(id);if(r.minBudget()!=null&&r.maxBudget()!=null&&r.minBudget().compareTo(r.maxBudget())>0)throw invalid("Budget minimum supérieur au maximum.");
  for(Long locality:r.localityIds())if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.localities where id=?)",Boolean.class,locality)))throw invalid("Localité inconnue.");
  jdbc.update("update fastcoloc.roommate_preferences set min_budget=?,max_budget=?,move_in_date=? where user_id=?",r.minBudget(),r.maxBudget(),r.moveInDate(),id);
  jdbc.update("delete from fastcoloc.preferred_localities where user_id=?",id);
  for(Long locality:new LinkedHashSet<>(r.localityIds()))jdbc.update("insert into fastcoloc.preferred_localities(user_id,locality_id) values(?,?)",id,locality);
  return data.details(id);
 }
 @Transactional public Map<String,Object> complete(UUID id){bindAndLock(id);jdbc.queryForObject("select fastcoloc.complete_onboarding()",Boolean.class);return data.details(id);}
 private void mark(UUID id,int step){jdbc.update("insert into fastcoloc.onboarding_steps(user_id,step_number) values(?,?) on conflict(user_id,step_number) do update set validated_at=now()",id,(short)step);}
 private ApiException invalid(String m){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STEP",m);}
}
