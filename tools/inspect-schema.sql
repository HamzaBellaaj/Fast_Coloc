-- Metadata only; no user rows and no DDL. Run in a READ ONLY transaction.
SELECT jsonb_pretty(jsonb_build_object(
 'database', current_database(), 'role', current_user, 'captured_at', now(),
 'tables', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY t.name),'[]') FROM (
   SELECT c.relname AS name, c.relkind, c.relrowsecurity AS rls,
     c.relforcerowsecurity AS force_rls, pg_get_userbyid(c.relowner) AS owner
   FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='fastcoloc' AND c.relkind IN ('r','p','v','m')) t),
 'columns', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY t.table_name,t.ordinal_position),'[]')
   FROM (SELECT table_name,column_name,ordinal_position,data_type,udt_schema,udt_name,
     is_nullable,column_default,is_identity,identity_generation,is_generated,generation_expression
     FROM information_schema.columns WHERE table_schema='fastcoloc') t),
 'constraints', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT c.conrelid::regclass::text AS table_name,c.conname,c.contype,
     pg_get_constraintdef(c.oid,true) AS definition
   FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
   WHERE n.nspname='fastcoloc') t),
 'indexes', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='fastcoloc') t),
 'policies', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT tablename,policyname,permissive,roles,cmd,qual,with_check
   FROM pg_policies WHERE schemaname='fastcoloc') t),
 'grants', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT grantee,table_name,privilege_type,is_grantable
   FROM information_schema.table_privileges WHERE table_schema='fastcoloc') t),
 'schema_acl', (SELECT nspacl::text FROM pg_namespace WHERE nspname='fastcoloc'),
 'default_acl', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT pg_get_userbyid(d.defaclrole) AS role,n.nspname,d.defaclobjtype,d.defaclacl::text
   FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
   WHERE n.nspname='fastcoloc' OR d.defaclnamespace=0) t),
 'functions', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT n.nspname,p.proname,pg_get_function_identity_arguments(p.oid) AS arguments,
     p.prosecdef,pg_get_userbyid(p.proowner) AS owner,p.proacl::text,p.proconfig,
     pg_get_functiondef(p.oid) AS definition
   FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
   WHERE p.prokind IN ('f','p') AND (n.nspname='fastcoloc' OR p.oid IN
     (SELECT evtfoid FROM pg_event_trigger))) t),
 'event_triggers', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT evtname,evtevent,evtenabled,evtfoid::regprocedure::text AS function,evttags
   FROM pg_event_trigger) t),
 'row_triggers', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT c.relname,pg_get_triggerdef(t.oid,true) AS definition
   FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
   JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='fastcoloc' AND NOT t.tgisinternal) t),
 'enums', (SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]') FROM (
   SELECT ty.typname,e.enumlabel,e.enumsortorder FROM pg_type ty
   JOIN pg_namespace n ON n.oid=ty.typnamespace JOIN pg_enum e ON e.enumtypid=ty.oid
   WHERE n.nspname='fastcoloc') t)
)) AS snapshot;
