-- Compatibility entry point for local psql only. Not sent to a SQL API.
-- Canonical reviewed provisioning migration; no automatic hosted execution.
\set ON_ERROR_STOP on
\ir ../supabase/migrations/20261111220503_provision_fastcoloc_backend.sql
