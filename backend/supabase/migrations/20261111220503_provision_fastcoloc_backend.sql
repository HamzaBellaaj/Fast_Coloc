-- REVIEW CANDIDATE ONLY. Generated filename by Supabase CLI 3.1.0; NOT applied remotely.
-- Scope: implemented me/sync/admin-access only. No password, client grants or migration replay.
BEGIN;
SET LOCAL lock_timeout='5s';
SELECT pg_advisory_xact_lock(hashtext('fastcoloc.runtime.provision.v1'));

DO $preflight$
DECLARE r record; c record; expected text[] := ARRAY[
 'countries','governorates','localities','profiles','account_access','profile_addresses',
 'personal_details','contact_details','roommate_preferences','preferred_localities',
 'profile_photos','onboarding_steps','consent_records','account_access_events'];
BEGIN
 IF current_database()<>'postgres' THEN RAISE EXCEPTION 'Unexpected database'; END IF;
 IF (SELECT count(*) FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
     WHERE n.nspname='fastcoloc' AND t.relkind IN ('r','p'))<>14
 OR EXISTS(SELECT 1 FROM unnest(expected) t(name) WHERE to_regclass('fastcoloc.'||t.name) IS NULL)
 THEN RAISE EXCEPTION 'Unexpected FastColoc schema; review before provisioning'; END IF;
 IF EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
           WHERE n.nspname='fastcoloc' AND t.relkind IN ('r','p') AND NOT t.relrowsecurity)
 THEN RAISE EXCEPTION 'RLS must already be enabled'; END IF;

 -- Do not change shared PUBLIC ACLs as a side effect. Fail closed if inherited CREATE exists.
 IF EXISTS(SELECT 1 FROM pg_namespace n CROSS JOIN LATERAL
           aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) acl
           WHERE acl.grantee=0 AND acl.privilege_type='CREATE')
 OR EXISTS(SELECT 1 FROM pg_database d CROSS JOIN LATERAL
           aclexplode(coalesce(d.datacl,acldefault('d',d.datdba))) acl
           WHERE d.datname=current_database() AND acl.grantee=0 AND acl.privilege_type='CREATE')
 THEN RAISE EXCEPTION 'PUBLIC CREATE requires a separate reviewed remediation'; END IF;

 FOR r IN SELECT oid,rolname FROM pg_roles WHERE rolname IN ('anon','authenticated') LOOP
  IF has_schema_privilege(r.oid,'fastcoloc','USAGE') OR has_schema_privilege(r.oid,'fastcoloc','CREATE')
  THEN RAISE EXCEPTION 'Unexpected private schema client access: %',r.rolname; END IF;
  FOR c IN SELECT t.oid FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
           WHERE n.nspname='fastcoloc' AND t.relkind IN ('r','p') LOOP
   IF has_any_column_privilege(r.oid,c.oid,'SELECT,INSERT,UPDATE,REFERENCES')
      OR has_table_privilege(r.oid,c.oid,'DELETE,TRUNCATE,TRIGGER')
   THEN RAISE EXCEPTION 'Unexpected private table client access: %',r.rolname; END IF;
  END LOOP;
 END LOOP;

 SELECT * INTO r FROM pg_roles WHERE rolname='fastcoloc_backend';
 IF FOUND THEN
  IF shobj_description(r.oid,'pg_authid') IS DISTINCT FROM 'fastcoloc.runtime.v1'
     OR r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb OR r.rolreplication OR r.rolinherit
     OR EXISTS(SELECT 1 FROM pg_auth_members WHERE member=r.oid)
     OR EXISTS(SELECT 1 FROM pg_auth_members m JOIN pg_roles member ON member.oid=m.member
               WHERE m.roleid=r.oid AND member.rolname<>current_user AND NOT member.rolsuper)
     OR EXISTS(SELECT 1 FROM pg_shdepend d WHERE d.refclassid='pg_authid'::regclass
               AND d.refobjid=r.oid AND (d.deptype='o' OR (d.dbid<>0 AND d.dbid<>(SELECT oid FROM pg_database WHERE datname=current_database()))))
  THEN RAISE EXCEPTION 'Existing role is unowned/unsafe; refusing takeover'; END IF;
 ELSE
  CREATE ROLE fastcoloc_backend NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
      NOINHERIT NOREPLICATION NOBYPASSRLS;
  COMMENT ON ROLE fastcoloc_backend IS 'fastcoloc.runtime.v1';
 END IF;

 SELECT * INTO r FROM pg_roles WHERE rolname='fastcoloc_backend';
 IF has_database_privilege(r.oid,current_database(),'CREATE')
 OR EXISTS(SELECT 1 FROM pg_namespace n WHERE n.nspname !~ '^pg_temp_' AND n.nspname !~ '^pg_toast_temp_'
            AND has_schema_privilege(r.oid,n.oid,'CREATE'))
 OR EXISTS(SELECT 1 FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl) acl WHERE acl.grantee=r.oid)
 OR EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
            CROSS JOIN LATERAL aclexplode(t.relacl) acl WHERE n.nspname<>'fastcoloc' AND acl.grantee=r.oid)
 OR EXISTS(SELECT 1 FROM pg_attribute a JOIN pg_class t ON t.oid=a.attrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace CROSS JOIN LATERAL aclexplode(a.attacl) acl
            WHERE n.nspname<>'fastcoloc' AND acl.grantee=r.oid)
 OR EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
            CROSS JOIN LATERAL aclexplode(p.proacl) acl WHERE n.nspname<>'fastcoloc' AND acl.grantee=r.oid)
 OR EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
            WHERE n.nspname IN ('public','fastcoloc') AND p.prosecdef AND has_function_privilege(r.oid,p.oid,'EXECUTE'))
 THEN RAISE EXCEPTION 'Unexpected role capabilities/default grants/definer entry point; separate review required'; END IF;
END $preflight$;

-- Password and LOGIN activation are a separate secure step. Re-runs preserve both.
ALTER ROLE fastcoloc_backend SET search_path=pg_catalog,fastcoloc;
ALTER ROLE fastcoloc_backend SET row_security=on;
ALTER ROLE fastcoloc_backend SET statement_timeout='15s';
ALTER ROLE fastcoloc_backend SET lock_timeout='5s';
ALTER ROLE fastcoloc_backend SET idle_in_transaction_session_timeout='30s';
REVOKE ALL ON SCHEMA fastcoloc FROM fastcoloc_backend;
REVOKE ALL ON ALL TABLES IN SCHEMA fastcoloc FROM fastcoloc_backend;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA fastcoloc FROM fastcoloc_backend;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA fastcoloc FROM fastcoloc_backend;

-- Table-level REVOKE does not remove old column grants: clear them explicitly.
DO $reset$
DECLARE t record; columns text; p record;
BEGIN
 FOR t IN SELECT c.oid,c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
          WHERE n.nspname='fastcoloc' AND c.relkind IN ('r','p') LOOP
  SELECT string_agg(quote_ident(attname),',' ORDER BY attnum) INTO columns
    FROM pg_attribute WHERE attrelid=t.oid AND attnum>0 AND NOT attisdropped;
  EXECUTE format('REVOKE ALL PRIVILEGES (%s) ON fastcoloc.%I FROM fastcoloc_backend',columns,t.relname);
  FOR p IN SELECT polname FROM pg_policy WHERE polrelid=t.oid AND left(polname,11)='fc_runtime_' LOOP
   EXECUTE format('DROP POLICY %I ON fastcoloc.%I',p.polname,t.relname);
  END LOOP;
 END LOOP;
END $reset$;

GRANT CONNECT ON DATABASE postgres TO fastcoloc_backend;
GRANT USAGE ON SCHEMA fastcoloc TO fastcoloc_backend;
GRANT SELECT(id,first_name,last_name,gender,age,completed_at,version,created_at,updated_at)
 ON fastcoloc.profiles TO fastcoloc_backend; -- explicit JPA projection, no future-column grant
GRANT SELECT(user_id,app_role,status) ON fastcoloc.account_access TO fastcoloc_backend;
GRANT SELECT(user_id,step_number) ON fastcoloc.onboarding_steps TO fastcoloc_backend;
GRANT SELECT(user_id) ON fastcoloc.profile_addresses,fastcoloc.personal_details,
    fastcoloc.contact_details,fastcoloc.roommate_preferences TO fastcoloc_backend;
GRANT INSERT(id,first_name,last_name,gender,age) ON fastcoloc.profiles TO fastcoloc_backend;
GRANT INSERT(user_id) ON fastcoloc.account_access,fastcoloc.profile_addresses,
    fastcoloc.personal_details,fastcoloc.contact_details,fastcoloc.roommate_preferences TO fastcoloc_backend;

-- Access diagnostics must see the user's BLOCKED row to return a precise 403.
CREATE POLICY fc_runtime_boundary ON fastcoloc.account_access AS RESTRICTIVE
 FOR ALL TO fastcoloc_backend USING(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid) WITH CHECK(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);
CREATE POLICY fc_runtime_read ON fastcoloc.account_access
 FOR SELECT TO fastcoloc_backend USING(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);
CREATE POLICY fc_runtime_insert ON fastcoloc.account_access
 FOR INSERT TO fastcoloc_backend WITH CHECK(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid
 AND app_role='USER' AND status='ACTIVE' AND blocked_at IS NULL AND blocked_reason IS NULL);

-- Profile bootstrap is allowed before account_access exists; a BLOCKED row denies it.
CREATE POLICY fc_runtime_boundary ON fastcoloc.profiles AS RESTRICTIVE
 FOR ALL TO fastcoloc_backend
 USING(id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND NOT EXISTS(
 SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status<>'ACTIVE'))
 WITH CHECK(id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND NOT EXISTS(
 SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status<>'ACTIVE'));
CREATE POLICY fc_runtime_read ON fastcoloc.profiles
 FOR SELECT TO fastcoloc_backend USING(id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid);
CREATE POLICY fc_runtime_insert ON fastcoloc.profiles
 FOR INSERT TO fastcoloc_backend WITH CHECK(id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND completed_at IS NULL AND version=0);

DO $policies$
DECLARE t text; boundary text := $predicate$user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND EXISTS (SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status='ACTIVE')$predicate$;
BEGIN
 -- Restrictive boundaries also constrain any additional permissive policy.
 FOREACH t IN ARRAY ARRAY['profile_addresses','personal_details','contact_details',
    'roommate_preferences','preferred_localities','profile_photos','onboarding_steps','consent_records'] LOOP
  EXECUTE format('CREATE POLICY fc_runtime_boundary ON fastcoloc.%I AS RESTRICTIVE FOR ALL TO fastcoloc_backend USING(%s) WITH CHECK(%s)',t,boundary,boundary);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['profile_addresses','personal_details','contact_details','roommate_preferences'] LOOP
  EXECUTE format('CREATE POLICY fc_runtime_read ON fastcoloc.%I FOR SELECT TO fastcoloc_backend USING(%s)',t,boundary);
  EXECUTE format('CREATE POLICY fc_runtime_insert ON fastcoloc.%I FOR INSERT TO fastcoloc_backend WITH CHECK(%s)',t,boundary);
 END LOOP;
 EXECUTE format('CREATE POLICY fc_runtime_read ON fastcoloc.onboarding_steps FOR SELECT TO fastcoloc_backend USING(%s)',boundary);
END $policies$;
-- No grants to Auth, clients, sequences, audit log, photos, consent, search or geography.
-- No SECURITY DEFINER function. No ADMIN bypass. The GUC is trusted server context, not a JWT.
COMMIT;
