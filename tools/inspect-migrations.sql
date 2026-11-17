-- Metadata/history only. Preserve the actual versions, names and SQL statements.
-- Review exported SQL for credentials before adding it to Git.
SELECT jsonb_pretty(coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.version),'[]'))
FROM supabase_migrations.schema_migrations m;
