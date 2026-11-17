-- QUARANTINE ROLLBACK ONLY; review and run separately, never automatically.
-- Stops future logins and removes capabilities/policies. Keeps the role and ALL business data.
-- Drain/stop application pools first; NOLOGIN does not terminate existing sessions.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('fastcoloc.runtime.provision.v1'));
DO $rollback$
DECLARE r record; t record; p record; columns text;
BEGIN
 SELECT * INTO r FROM pg_roles WHERE rolname='fastcoloc_backend';
 IF NOT FOUND THEN RETURN; END IF;
 IF shobj_description(r.oid,'pg_authid') IS DISTINCT FROM 'fastcoloc.runtime.v1'
 THEN RAISE EXCEPTION 'Refusing to quarantine an unmanaged role'; END IF;
 ALTER ROLE fastcoloc_backend NOLOGIN;
 REVOKE ALL ON DATABASE postgres FROM fastcoloc_backend;
 REVOKE ALL ON SCHEMA fastcoloc FROM fastcoloc_backend;
 REVOKE ALL ON ALL TABLES IN SCHEMA fastcoloc FROM fastcoloc_backend;
 REVOKE ALL ON ALL SEQUENCES IN SCHEMA fastcoloc FROM fastcoloc_backend;
 REVOKE ALL ON ALL FUNCTIONS IN SCHEMA fastcoloc FROM fastcoloc_backend;
 FOR t IN SELECT c.oid,c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
          WHERE n.nspname='fastcoloc' AND c.relkind IN ('r','p') LOOP
  SELECT string_agg(quote_ident(attname),',' ORDER BY attnum) INTO columns
    FROM pg_attribute WHERE attrelid=t.oid AND attnum>0 AND NOT attisdropped;
  EXECUTE format('REVOKE ALL PRIVILEGES (%s) ON fastcoloc.%I FROM fastcoloc_backend',columns,t.relname);
  FOR p IN SELECT polname FROM pg_policy WHERE polrelid=t.oid AND left(polname,11)='fc_runtime_' LOOP
   EXECUTE format('DROP POLICY %I ON fastcoloc.%I',p.polname,t.relname);
  END LOOP;
 END LOOP;
END $rollback$;
-- Do not DROP OWNED, DROP ROLE CASCADE, remove tables, replay initial migrations or restore PUBLIC CREATE.
COMMIT;
