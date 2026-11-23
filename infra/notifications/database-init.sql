-- Dedicated local n8n database only; never run against the application database.
CREATE TABLE IF NOT EXISTS public.notification_deliveries (
 event_id uuid PRIMARY KEY,
 status text NOT NULL CHECK(status IN ('PROCESSING','SENT','FAILED')),
 lease_token uuid NOT NULL DEFAULT gen_random_uuid(),
 lease_until timestamptz,
 updated_at timestamptz NOT NULL DEFAULT now()
);
