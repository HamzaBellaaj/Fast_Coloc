-- Separate delivery ledger for n8n Cloud. Apply once; no application data is copied.
BEGIN;
CREATE ROLE fastcoloc_mailer NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE SCHEMA fastcoloc_notifications;
REVOKE ALL ON SCHEMA fastcoloc_notifications FROM PUBLIC,anon,authenticated,fastcoloc_backend;
GRANT USAGE ON SCHEMA fastcoloc_notifications TO fastcoloc_mailer;
CREATE TABLE fastcoloc_notifications.notification_deliveries (
 event_id uuid PRIMARY KEY,
 status text NOT NULL CHECK(status IN ('PROCESSING','SENT','FAILED')),
 lease_token uuid NOT NULL DEFAULT gen_random_uuid(),
 lease_until timestamptz,
 provider_message_id text,
 updated_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON fastcoloc_notifications.notification_deliveries FROM PUBLIC,anon,authenticated,fastcoloc_backend;
GRANT SELECT,INSERT,UPDATE ON fastcoloc_notifications.notification_deliveries TO fastcoloc_mailer;
ALTER TABLE fastcoloc_notifications.notification_deliveries ENABLE ROW LEVEL SECURITY;
CREATE POLICY mailer_ledger ON fastcoloc_notifications.notification_deliveries TO fastcoloc_mailer USING(true) WITH CHECK(true);
ALTER ROLE fastcoloc_mailer SET statement_timeout='10s';
ALTER ROLE fastcoloc_mailer SET idle_in_transaction_session_timeout='15s';
COMMIT;
