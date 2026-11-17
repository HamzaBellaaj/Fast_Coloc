-- Sprint 1 capabilities only: does not replay any existing migration or alter runtime credentials.
BEGIN;
SET LOCAL lock_timeout='5s';
-- NOT A MIGRATION TO AUTO-APPLY. Optional future onboarding capability, disabled by default.
-- Requires the base migration AND implemented/validated step DTOs, services and Storage workflow.
-- No completed_at grant: final completion rules/atomic validation remain a separate review.

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

GRANT SELECT(user_id,min_budget,max_budget,currency,move_in_date,additional_preferences)
 ON fastcoloc.roommate_preferences TO fastcoloc_backend;
GRANT UPDATE(min_budget,max_budget,move_in_date) ON fastcoloc.roommate_preferences TO fastcoloc_backend;
GRANT SELECT(user_id,locality_id),INSERT(user_id,locality_id),DELETE
 ON fastcoloc.preferred_localities TO fastcoloc_backend;
CREATE POLICY fc_runtime_preferences_update ON fastcoloc.roommate_preferences FOR UPDATE TO fastcoloc_backend
 USING(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 WITH CHECK(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);
CREATE POLICY fc_runtime_preferred_read ON fastcoloc.preferred_localities FOR SELECT TO fastcoloc_backend
 USING(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);
CREATE POLICY fc_runtime_preferred_insert ON fastcoloc.preferred_localities FOR INSERT TO fastcoloc_backend
 WITH CHECK(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);
CREATE POLICY fc_runtime_preferred_delete ON fastcoloc.preferred_localities FOR DELETE TO fastcoloc_backend
 USING(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);

-- Narrow private capability: clients/runtime still cannot directly UPDATE completed_at.
CREATE FUNCTION fastcoloc.complete_onboarding() RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE subject uuid:=nullif(current_setting('fastcoloc.user_id',true),'')::uuid;
BEGIN
 IF subject IS NULL OR NOT EXISTS(SELECT 1 FROM fastcoloc.account_access WHERE user_id=subject AND status='ACTIVE')
 THEN RAISE EXCEPTION 'FC_FORBIDDEN' USING ERRCODE='42501'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(subject::text,0));
 IF (SELECT count(*) FROM fastcoloc.onboarding_steps WHERE user_id=subject AND step_number BETWEEN 1 AND 5)<>5
 OR NOT EXISTS(SELECT 1 FROM fastcoloc.profiles WHERE id=subject AND length(trim(first_name))>0 AND length(trim(last_name))>0 AND age BETWEEN 0 AND 130)
 OR NOT EXISTS(SELECT 1 FROM fastcoloc.profile_addresses a JOIN fastcoloc.localities l ON l.id=a.locality_id JOIN fastcoloc.governorates g ON g.id=l.governorate_id WHERE a.user_id=subject AND a.country_code=g.country_code AND length(trim(a.street))>0 AND length(trim(a.postal_code))>0)
 OR NOT EXISTS(SELECT 1 FROM fastcoloc.personal_details WHERE user_id=subject)
 OR NOT EXISTS(SELECT 1 FROM fastcoloc.contact_details WHERE user_id=subject)
 OR NOT EXISTS(SELECT 1 FROM fastcoloc.profile_photos WHERE user_id=subject)
 THEN RAISE EXCEPTION 'FC_INCOMPLETE' USING ERRCODE='P0001'; END IF;
 UPDATE fastcoloc.profiles SET completed_at=now(),updated_at=now(),version=version+1 WHERE id=subject AND completed_at IS NULL;
 RETURN true;
END $$;
CREATE FUNCTION fastcoloc.invalidate_photo_step() RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE subject uuid:=nullif(current_setting('fastcoloc.user_id',true),'')::uuid;
BEGIN
 IF subject IS NULL OR NOT EXISTS(SELECT 1 FROM fastcoloc.account_access WHERE user_id=subject AND status='ACTIVE')
 THEN RAISE EXCEPTION 'FC_FORBIDDEN' USING ERRCODE='42501'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(subject::text,0));
 DELETE FROM fastcoloc.onboarding_steps WHERE user_id=subject AND step_number=5;
 UPDATE fastcoloc.profiles SET completed_at=NULL,updated_at=now(),version=version+1 WHERE id=subject AND completed_at IS NOT NULL;
 RETURN true;
END $$;
-- Helper is not executable by the runtime; privileged entry points invoke it under their owner.
CREATE FUNCTION fastcoloc.require_admin() RETURNS uuid
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid:=nullif(current_setting('fastcoloc.user_id',true),'')::uuid;
BEGIN
 IF actor IS NULL OR NOT EXISTS(SELECT 1 FROM fastcoloc.account_access WHERE user_id=actor AND app_role='ADMIN' AND status='ACTIVE')
 THEN RAISE EXCEPTION 'FC_FORBIDDEN' USING ERRCODE='42501'; END IF;
 RETURN actor;
END $$;
CREATE FUNCTION fastcoloc.admin_users(p_limit integer,p_offset integer,p_search text,p_status text)
RETURNS TABLE(id uuid,first_name varchar,last_name varchar,app_role text,status text,completed_at timestamptz,created_at timestamptz,total_count bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
BEGIN
 PERFORM fastcoloc.require_admin();
 IF p_limit NOT BETWEEN 1 AND 100 OR p_offset<0 OR length(coalesce(p_search,''))>100 OR (p_status IS NOT NULL AND p_status NOT IN ('ACTIVE','BLOCKED'))
 THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
 RETURN QUERY SELECT p.id,p.first_name,p.last_name,a.app_role,a.status,p.completed_at,p.created_at,count(*) OVER()
 FROM fastcoloc.profiles p JOIN fastcoloc.account_access a ON a.user_id=p.id
 WHERE (p_status IS NULL OR a.status=p_status) AND
 (coalesce(p_search,'')='' OR p.first_name ILIKE '%'||p_search||'%' OR p.last_name ILIKE '%'||p_search||'%')
 ORDER BY p.created_at,p.id LIMIT p_limit OFFSET p_offset;
END $$;
CREATE FUNCTION fastcoloc.admin_change_status(target uuid,new_status text,reason text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid; result jsonb;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtext('fastcoloc.admin.status'));
 actor:=fastcoloc.require_admin();
 IF new_status NOT IN ('ACTIVE','BLOCKED') OR reason IS NULL OR length(trim(reason)) NOT BETWEEN 1 AND 1000
 THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
 IF target=actor AND new_status='BLOCKED' THEN RAISE EXCEPTION 'FC_FORBIDDEN' USING ERRCODE='42501'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(target::text,0));
 IF NOT EXISTS(SELECT 1 FROM fastcoloc.account_access WHERE user_id=target) THEN RAISE EXCEPTION 'FC_NOT_FOUND' USING ERRCODE='P0002'; END IF;
 UPDATE fastcoloc.account_access SET status=new_status,blocked_at=CASE WHEN new_status='BLOCKED' THEN now() ELSE NULL END,
 blocked_reason=CASE WHEN new_status='BLOCKED' THEN trim(reason) ELSE NULL END,updated_at=now() WHERE user_id=target;
 INSERT INTO fastcoloc.account_access_events(subject_id,actor_id,event_type,reason)
 VALUES(target,actor,CASE WHEN new_status='BLOCKED' THEN 'BLOCK' ELSE 'UNBLOCK' END,trim(reason));
 SELECT jsonb_build_object('id',user_id,'role',app_role,'status',status) INTO result FROM fastcoloc.account_access WHERE user_id=target;
 RETURN result;
END $$;
CREATE FUNCTION fastcoloc.admin_contacts(target uuid,mobile text,residential text,availability text,msn text,skype text,website text,reason text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid; result jsonb;
BEGIN
 actor:=fastcoloc.require_admin();
 IF reason IS NULL OR length(trim(reason)) NOT BETWEEN 1 AND 1000 THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(target::text,0));
 IF NOT EXISTS(SELECT 1 FROM fastcoloc.profiles WHERE id=target) THEN RAISE EXCEPTION 'FC_NOT_FOUND' USING ERRCODE='P0002'; END IF;
 UPDATE fastcoloc.contact_details SET mobile_phone=mobile,residential_phone=residential,call_availability=availability,msn_contact=msn,skype_contact=skype,website_url=website WHERE user_id=target;
 INSERT INTO fastcoloc.account_access_events(subject_id,actor_id,event_type,reason) VALUES(target,actor,'CONTACT_UPDATE',trim(reason));
 SELECT to_jsonb(c) INTO result FROM fastcoloc.contact_details c WHERE user_id=target;
 RETURN result;
END $$;
CREATE FUNCTION fastcoloc.admin_delete_attempt(target uuid,reason text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid:=fastcoloc.require_admin();
BEGIN
 IF target=actor THEN RAISE EXCEPTION 'FC_FORBIDDEN' USING ERRCODE='42501'; END IF;
 IF reason IS NULL OR length(trim(reason)) NOT BETWEEN 1 AND 1000 THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
 IF EXISTS(SELECT 1 FROM fastcoloc.profiles WHERE id=target) THEN
  INSERT INTO fastcoloc.account_access_events(subject_id,actor_id,event_type,reason) VALUES(target,actor,'DELETE',trim(reason));
 END IF;
 RETURN true;
END $$;
REVOKE ALL ON FUNCTION fastcoloc.complete_onboarding(),fastcoloc.invalidate_photo_step(),fastcoloc.require_admin(),
 fastcoloc.admin_users(integer,integer,text,text),fastcoloc.admin_change_status(uuid,text,text),
 fastcoloc.admin_contacts(uuid,text,text,text,text,text,text,text),fastcoloc.admin_delete_attempt(uuid,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION fastcoloc.complete_onboarding(),fastcoloc.invalidate_photo_step(),
 fastcoloc.admin_users(integer,integer,text,text),fastcoloc.admin_change_status(uuid,text,text),
 fastcoloc.admin_contacts(uuid,text,text,text,text,text,text,text),fastcoloc.admin_delete_attempt(uuid,text)
 TO fastcoloc_backend;
COMMIT;
