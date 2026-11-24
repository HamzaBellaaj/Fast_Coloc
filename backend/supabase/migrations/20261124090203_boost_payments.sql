BEGIN;

ALTER TABLE fastcoloc.listings ADD COLUMN IF NOT EXISTS golden_until timestamptz;
GRANT SELECT (golden_until), UPDATE (golden_until) ON fastcoloc.listings TO fastcoloc_backend;

CREATE TABLE fastcoloc.boost_payments (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL,
    listing_id uuid NOT NULL,
    months integer NOT NULL CHECK (months IN (1,3,6)),
    provider text NOT NULL CHECK (provider = 'PAYPAL'),
    provider_ref text NOT NULL,
    approval_url text NOT NULL,
    amount numeric(10,2) NOT NULL CHECK (amount > 0),
    currency text NOT NULL CHECK (currency = 'USD'),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','FAILED','CANCELLED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_ref)
);
CREATE INDEX boost_payments_open ON fastcoloc.boost_payments
    (actor_id, listing_id, months, provider, expires_at DESC) WHERE status='PENDING';

ALTER TABLE fastcoloc.boost_payments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fastcoloc.boost_payments FROM PUBLIC, anon, authenticated, fastcoloc_backend;
GRANT SELECT, INSERT, UPDATE (status, updated_at) ON fastcoloc.boost_payments TO fastcoloc_backend;
CREATE POLICY boost_payments_runtime ON fastcoloc.boost_payments
    FOR ALL TO fastcoloc_backend USING (true) WITH CHECK (true);

COMMIT;
