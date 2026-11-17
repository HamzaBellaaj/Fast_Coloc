-- NOT A MIGRATION TO AUTO-APPLY. Optional future onboarding capability, disabled by default.
-- Requires the base migration AND implemented/validated step DTOs, services and Storage workflow.
-- No completed_at grant: final completion rules/atomic validation remain a separate review.
BEGIN;
SET LOCAL lock_timeout='5s';
DO $preflight$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles r WHERE r.rolname='fastcoloc_backend'
    AND shobj_description(r.oid,'pg_authid')='fastcoloc.runtime.v1'
    AND NOT r.rolsuper AND NOT r.rolbypassrls)
 THEN RAISE EXCEPTION 'Provision the reviewed base role first'; END IF;
END $preflight$;
GRANT SELECT(user_id,country_code,locality_id,street,postal_code) ON fastcoloc.profile_addresses TO fastcoloc_backend;
GRANT SELECT(user_id,main_activities,hobbies,nationality_code,spoken_languages,religion,smoking,cleanliness,biography)
 ON fastcoloc.personal_details TO fastcoloc_backend;
GRANT SELECT(user_id,mobile_phone,residential_phone,call_availability,msn_contact,skype_contact,website_url)
 ON fastcoloc.contact_details TO fastcoloc_backend;
GRANT SELECT(id,user_id,bucket,object_path,caption,mime_type,byte_size,display_order,is_primary,created_at)
 ON fastcoloc.profile_photos TO fastcoloc_backend;
GRANT SELECT(user_id,step_number,validated_at) ON fastcoloc.onboarding_steps TO fastcoloc_backend;
GRANT SELECT(code,name) ON fastcoloc.countries TO fastcoloc_backend;
GRANT SELECT(id,country_code,name) ON fastcoloc.governorates TO fastcoloc_backend;
GRANT SELECT(id,governorate_id,name) ON fastcoloc.localities TO fastcoloc_backend;
GRANT UPDATE(first_name,last_name,gender,age,updated_at,version) ON fastcoloc.profiles TO fastcoloc_backend;
GRANT UPDATE(country_code,locality_id,street,postal_code) ON fastcoloc.profile_addresses TO fastcoloc_backend;
GRANT UPDATE(main_activities,hobbies,nationality_code,spoken_languages,religion,smoking,cleanliness,biography)
 ON fastcoloc.personal_details TO fastcoloc_backend;
GRANT UPDATE(mobile_phone,residential_phone,call_availability,msn_contact,skype_contact,website_url)
 ON fastcoloc.contact_details TO fastcoloc_backend;
GRANT INSERT(id,user_id,bucket,object_path,caption,mime_type,byte_size,display_order,is_primary)
 ON fastcoloc.profile_photos TO fastcoloc_backend;
GRANT UPDATE(caption,display_order,is_primary) ON fastcoloc.profile_photos TO fastcoloc_backend;
GRANT DELETE ON fastcoloc.profile_photos TO fastcoloc_backend;
GRANT INSERT(user_id,step_number) ON fastcoloc.onboarding_steps TO fastcoloc_backend;
GRANT UPDATE(validated_at) ON fastcoloc.onboarding_steps TO fastcoloc_backend;
GRANT DELETE ON fastcoloc.onboarding_steps TO fastcoloc_backend;

DO $policies$
DECLARE t text; predicate text := $predicate$user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND EXISTS (SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status='ACTIVE')$predicate$;
BEGIN
 FOREACH t IN ARRAY ARRAY['profiles','profile_addresses','personal_details','contact_details'] LOOP
  EXECUTE format('DROP POLICY IF EXISTS fc_runtime_step_update ON fastcoloc.%I',t);
  IF t='profiles' THEN
   EXECUTE format('CREATE POLICY fc_runtime_step_update ON fastcoloc.profiles FOR UPDATE TO fastcoloc_backend USING(id=%s AND %s) WITH CHECK(id=%s AND %s)',
     $s$nullif(current_setting('fastcoloc.user_id',true),'')::uuid$s$,$a$EXISTS (SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status='ACTIVE')$a$,$s$nullif(current_setting('fastcoloc.user_id',true),'')::uuid$s$,$a$EXISTS (SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status='ACTIVE')$a$);
  ELSE
   EXECUTE format('CREATE POLICY fc_runtime_step_update ON fastcoloc.%I FOR UPDATE TO fastcoloc_backend USING(%s) WITH CHECK(%s)',t,predicate,predicate);
  END IF;
 END LOOP;
 FOREACH t IN ARRAY ARRAY['profile_photos','onboarding_steps'] LOOP
  EXECUTE format('DROP POLICY IF EXISTS fc_runtime_step_read ON fastcoloc.%I',t);
  EXECUTE format('DROP POLICY IF EXISTS fc_runtime_step_insert ON fastcoloc.%I',t);
  EXECUTE format('DROP POLICY IF EXISTS fc_runtime_step_update ON fastcoloc.%I',t);
  EXECUTE format('DROP POLICY IF EXISTS fc_runtime_step_delete ON fastcoloc.%I',t);
  EXECUTE format('CREATE POLICY fc_runtime_step_read ON fastcoloc.%I FOR SELECT TO fastcoloc_backend USING(%s)',t,predicate);
  EXECUTE format('CREATE POLICY fc_runtime_step_insert ON fastcoloc.%I FOR INSERT TO fastcoloc_backend WITH CHECK(%s)',t,predicate);
  EXECUTE format('CREATE POLICY fc_runtime_step_update ON fastcoloc.%I FOR UPDATE TO fastcoloc_backend USING(%s) WITH CHECK(%s)',t,predicate,predicate);
  EXECUTE format('CREATE POLICY fc_runtime_step_delete ON fastcoloc.%I FOR DELETE TO fastcoloc_backend USING(%s)',t,predicate);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['countries','governorates','localities'] LOOP
  EXECUTE format('DROP POLICY IF EXISTS fc_runtime_step_read ON fastcoloc.%I',t);
  -- Shared reference data only, read-only, and an ACTIVE caller is still required.
  EXECUTE format('CREATE POLICY fc_runtime_step_read ON fastcoloc.%I FOR SELECT TO fastcoloc_backend USING(%s)',t,$a$EXISTS (SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status='ACTIVE')$a$);
 END LOOP;
END $policies$;
-- No UPDATE user_id/id, account role/status, completed_at, audit, consent or preferences.
-- No sequence privileges; profile_photos uses UUID and onboarding_steps a composite PK.
COMMIT;
