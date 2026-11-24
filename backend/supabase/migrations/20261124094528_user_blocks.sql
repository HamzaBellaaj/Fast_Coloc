BEGIN;

CREATE TABLE fastcoloc.user_blocks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id uuid NOT NULL REFERENCES fastcoloc.profiles (id) ON DELETE CASCADE,
    blocked_id uuid NOT NULL REFERENCES fastcoloc.profiles (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX user_blocks_blocked_id_idx ON fastcoloc.user_blocks (blocked_id);

ALTER TABLE fastcoloc.user_blocks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fastcoloc.user_blocks FROM PUBLIC, anon, authenticated, fastcoloc_backend;
GRANT SELECT, DELETE ON fastcoloc.user_blocks TO fastcoloc_backend;
GRANT INSERT (blocker_id, blocked_id) ON fastcoloc.user_blocks TO fastcoloc_backend;
CREATE POLICY user_blocks_own ON fastcoloc.user_blocks
    FOR ALL TO fastcoloc_backend
    USING (blocker_id = (SELECT nullif(current_setting('fastcoloc.user_id', true), '')::uuid))
    WITH CHECK (blocker_id = (SELECT nullif(current_setting('fastcoloc.user_id', true), '')::uuid));

-- Expose only the existence of a target and a minimal display name where there is a
-- public listing or an outgoing block. Private profile columns stay behind owner RLS.
CREATE FUNCTION fastcoloc.personal_block_member(target uuid)
RETURNS TABLE (display_name text, listed boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $$
    WITH actor AS (
        SELECT a.user_id FROM fastcoloc.account_access a
        WHERE a.user_id = nullif(current_setting('fastcoloc.user_id', true), '')::uuid
          AND a.status = 'ACTIVE'
    ), member AS (
        SELECT p.first_name, p.last_name, EXISTS (
            SELECT 1 FROM fastcoloc.listings l WHERE l.author_id = target
                AND l.status = 'PUBLISHED' AND NOT l.is_hidden AND l.expires_at > statement_timestamp()
        ) OR EXISTS (
            SELECT 1 FROM fastcoloc.conversations c
            WHERE (c.owner_id = actor.user_id AND c.guest_id = target)
               OR (c.guest_id = actor.user_id AND c.owner_id = target)
        ) AS listed, EXISTS (
            SELECT 1 FROM fastcoloc.user_blocks b
            WHERE b.blocker_id = actor.user_id AND b.blocked_id = target
        ) AS owned
        FROM actor CROSS JOIN fastcoloc.profiles p WHERE p.id = target
    )
    SELECT CASE WHEN listed OR owned THEN
        coalesce(nullif(concat_ws(' ', nullif(btrim(first_name), ''),
            nullif(left(btrim(last_name), 1), '') || '.'), ''), 'Membre FastColoc')
        END, listed
    FROM member
$$;

-- Messaging checks need both directions, without exposing incoming block rows.
CREATE FUNCTION fastcoloc.personal_exchange_blocked(target uuid)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $$
    SELECT EXISTS (
        SELECT 1 FROM fastcoloc.account_access a JOIN fastcoloc.user_blocks b
            ON (b.blocker_id = a.user_id AND b.blocked_id = target)
            OR (b.blocked_id = a.user_id AND b.blocker_id = target)
        WHERE a.user_id = nullif(current_setting('fastcoloc.user_id', true), '')::uuid
          AND a.status = 'ACTIVE'
    )
$$;

REVOKE ALL ON FUNCTION fastcoloc.personal_block_member(uuid), fastcoloc.personal_exchange_blocked(uuid)
    FROM PUBLIC, anon, authenticated, fastcoloc_backend;
GRANT EXECUTE ON FUNCTION fastcoloc.personal_block_member(uuid), fastcoloc.personal_exchange_blocked(uuid)
    TO fastcoloc_backend;

CREATE FUNCTION fastcoloc.reject_blocked_exchange()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    owner uuid;
    guest uuid;
BEGIN
    IF TG_TABLE_NAME = 'conversations' THEN
        owner := NEW.owner_id;
        guest := NEW.guest_id;
    ELSE
        SELECT c.owner_id, c.guest_id INTO owner, guest
        FROM fastcoloc.conversations c WHERE c.id = NEW.conversation_id;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended(
        least(owner::text, guest::text) || ':' || greatest(owner::text, guest::text), 1));
    IF EXISTS (
        SELECT 1 FROM fastcoloc.user_blocks
        WHERE (blocker_id = owner AND blocked_id = guest)
           OR (blocker_id = guest AND blocked_id = owner)
    ) THEN
        RAISE EXCEPTION 'FC_MESSAGE_RECIPIENT_UNAVAILABLE' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION fastcoloc.reject_blocked_exchange() FROM PUBLIC, anon, authenticated, fastcoloc_backend;
CREATE TRIGGER reject_blocked_conversation BEFORE INSERT ON fastcoloc.conversations
    FOR EACH ROW EXECUTE FUNCTION fastcoloc.reject_blocked_exchange();
CREATE TRIGGER reject_blocked_message BEFORE INSERT ON fastcoloc.messages
    FOR EACH ROW EXECUTE FUNCTION fastcoloc.reject_blocked_exchange();

COMMIT;
