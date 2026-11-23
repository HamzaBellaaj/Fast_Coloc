-- Apply this new migration once; never replay historical migrations.
BEGIN;
CREATE TABLE fastcoloc.conversations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 listing_id uuid REFERENCES fastcoloc.listings(id) ON DELETE SET NULL,
 listing_reference uuid NOT NULL,
 owner_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 guest_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 owner_name text NOT NULL, guest_name text NOT NULL,
 listing_title text NOT NULL, listing_kind text NOT NULL,
 locality text NOT NULL, rent numeric(12,3) NOT NULL,
 owner_read_id bigint NOT NULL DEFAULT 0, guest_read_id bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(owner_id<>guest_id), UNIQUE(listing_reference,guest_id)
);
CREATE INDEX conversations_owner ON fastcoloc.conversations(owner_id,updated_at DESC,id);
CREATE INDEX conversations_guest ON fastcoloc.conversations(guest_id,updated_at DESC,id);
CREATE TABLE fastcoloc.messages (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 conversation_id uuid NOT NULL REFERENCES fastcoloc.conversations(id) ON DELETE CASCADE,
 sender_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 client_id uuid NOT NULL,
 content text NOT NULL CHECK(char_length(btrim(content)) BETWEEN 1 AND 4000),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(conversation_id,sender_id,client_id)
);
CREATE INDEX messages_history ON fastcoloc.messages(conversation_id,id DESC);
CREATE INDEX messages_rate ON fastcoloc.messages(sender_id,created_at DESC);
ALTER TABLE fastcoloc.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE fastcoloc.messages ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fastcoloc.conversations,fastcoloc.messages FROM PUBLIC,anon,authenticated,fastcoloc_backend;
GRANT SELECT ON fastcoloc.conversations,fastcoloc.messages TO fastcoloc_backend;
CREATE POLICY conversation_participants ON fastcoloc.conversations FOR SELECT TO fastcoloc_backend USING (
 nullif(current_setting('fastcoloc.user_id',true),'')::uuid IN (owner_id,guest_id)
 AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND a.status='ACTIVE')
);
CREATE POLICY message_participants ON fastcoloc.messages FOR SELECT TO fastcoloc_backend USING (
 EXISTS(SELECT 1 FROM fastcoloc.conversations c WHERE c.id=conversation_id)
);
-- Narrow write functions; no admin bypass of private conversations.
CREATE FUNCTION fastcoloc.messaging_actor() RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid:=nullif(current_setting('fastcoloc.user_id',true),'')::uuid;
BEGIN
 IF actor IS NULL OR NOT EXISTS(SELECT 1 FROM fastcoloc.profiles p JOIN fastcoloc.account_access a ON a.user_id=p.id WHERE p.id=actor AND a.status='ACTIVE' AND p.completed_at IS NOT NULL) THEN
  RAISE EXCEPTION 'FC_MESSAGE_ACCESS' USING ERRCODE='42501';
 END IF;
 RETURN actor;
END $$;
CREATE FUNCTION fastcoloc.messaging_send(cid uuid,body text,request_id uuid) RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid:=fastcoloc.messaging_actor(); c fastcoloc.conversations; existing fastcoloc.messages; message_id bigint;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(actor::text,0));
 SELECT * INTO c FROM fastcoloc.conversations WHERE id=cid FOR UPDATE;
 IF NOT FOUND OR actor NOT IN(c.owner_id,c.guest_id) THEN RAISE EXCEPTION 'FC_MESSAGE_MISSING' USING ERRCODE='P0002'; END IF;
 IF NOT EXISTS(SELECT 1 FROM fastcoloc.account_access WHERE user_id=CASE WHEN actor=c.owner_id THEN c.guest_id ELSE c.owner_id END AND status='ACTIVE') THEN
  RAISE EXCEPTION 'FC_MESSAGE_RECIPIENT_UNAVAILABLE' USING ERRCODE='42501';
 END IF;
 SELECT * INTO existing FROM fastcoloc.messages WHERE conversation_id=cid AND sender_id=actor AND client_id=request_id;
 IF FOUND THEN
  IF existing.content<>btrim(body) THEN RAISE EXCEPTION 'FC_MESSAGE_REQUEST_REUSED' USING ERRCODE='22023'; END IF;
  RETURN existing.id;
 END IF;
 IF body IS NULL OR char_length(btrim(body)) NOT BETWEEN 1 AND 4000 OR request_id IS NULL THEN RAISE EXCEPTION 'FC_MESSAGE_INVALID' USING ERRCODE='22023'; END IF;
 IF (SELECT count(*) FROM fastcoloc.messages WHERE sender_id=actor AND created_at>clock_timestamp()-interval '1 minute')>=30 THEN
  RAISE EXCEPTION 'FC_MESSAGE_RATE_LIMIT' USING ERRCODE='P0001';
 END IF;
 INSERT INTO fastcoloc.messages(conversation_id,sender_id,client_id,content) VALUES(cid,actor,request_id,btrim(body)) RETURNING id INTO message_id;
 UPDATE fastcoloc.conversations SET updated_at=clock_timestamp() WHERE id=cid;
 RETURN message_id;
END $$;
CREATE FUNCTION fastcoloc.messaging_start(lid uuid,body text,request_id uuid) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid:=fastcoloc.messaging_actor(); l fastcoloc.listings; cid uuid;
BEGIN
 SELECT id INTO cid FROM fastcoloc.conversations WHERE listing_reference=lid AND guest_id=actor;
 IF cid IS NULL THEN
  SELECT * INTO l FROM fastcoloc.listings WHERE id=lid FOR SHARE;
  IF NOT FOUND OR l.status<>'PUBLISHED' OR l.is_hidden OR l.expires_at<=statement_timestamp() THEN RAISE EXCEPTION 'FC_MESSAGE_LISTING_UNAVAILABLE' USING ERRCODE='P0002'; END IF;
  IF l.author_id=actor THEN RAISE EXCEPTION 'FC_MESSAGE_SELF_CONTACT' USING ERRCODE='22023'; END IF;
  IF NOT EXISTS(SELECT 1 FROM fastcoloc.account_access WHERE user_id=l.author_id AND status='ACTIVE') THEN RAISE EXCEPTION 'FC_MESSAGE_RECIPIENT_UNAVAILABLE' USING ERRCODE='42501'; END IF;
  INSERT INTO fastcoloc.conversations(listing_id,listing_reference,owner_id,guest_id,owner_name,guest_name,listing_title,listing_kind,locality,rent)
   SELECT l.id,l.id,l.author_id,actor,coalesce(nullif(o.first_name,''),'Utilisateur'),coalesce(nullif(g.first_name,''),'Utilisateur'),l.title,l.kind,p.name,l.rent
   FROM fastcoloc.profiles o CROSS JOIN fastcoloc.profiles g CROSS JOIN fastcoloc.localities p WHERE o.id=l.author_id AND g.id=actor AND p.id=l.locality_id
   ON CONFLICT(listing_reference,guest_id) DO UPDATE SET listing_reference=excluded.listing_reference RETURNING id INTO cid;
 END IF;
 PERFORM fastcoloc.messaging_send(cid,body,request_id);
 RETURN cid;
END $$;
CREATE FUNCTION fastcoloc.messaging_read(cid uuid,through_id bigint) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid:=fastcoloc.messaging_actor(); c fastcoloc.conversations;
BEGIN
 SELECT * INTO c FROM fastcoloc.conversations WHERE id=cid FOR UPDATE;
 IF NOT FOUND OR actor NOT IN(c.owner_id,c.guest_id) THEN RAISE EXCEPTION 'FC_MESSAGE_MISSING' USING ERRCODE='P0002'; END IF;
 IF NOT EXISTS(SELECT 1 FROM fastcoloc.messages WHERE conversation_id=cid AND id=through_id) THEN RAISE EXCEPTION 'FC_MESSAGE_INVALID_CURSOR' USING ERRCODE='22023'; END IF;
 UPDATE fastcoloc.conversations SET owner_read_id=CASE WHEN actor=owner_id THEN greatest(owner_read_id,through_id) ELSE owner_read_id END,
 guest_read_id=CASE WHEN actor=guest_id THEN greatest(guest_read_id,through_id) ELSE guest_read_id END WHERE id=cid;
END $$;
REVOKE ALL ON FUNCTION fastcoloc.messaging_actor(),fastcoloc.messaging_start(uuid,text,uuid),fastcoloc.messaging_send(uuid,text,uuid),fastcoloc.messaging_read(uuid,bigint) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION fastcoloc.messaging_start(uuid,text,uuid),fastcoloc.messaging_send(uuid,text,uuid),fastcoloc.messaging_read(uuid,bigint) TO fastcoloc_backend;
COMMIT;
