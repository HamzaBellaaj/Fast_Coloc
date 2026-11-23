-- Additive outbox; apply once after private_messaging. Never replay history.
BEGIN;
CREATE TABLE fastcoloc.notification_preferences(user_id uuid PRIMARY KEY REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,new_message_email boolean NOT NULL DEFAULT true,updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE fastcoloc.notification_events(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),recipient_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 conversation_id uuid NOT NULL REFERENCES fastcoloc.conversations(id) ON DELETE CASCADE,message_id bigint NOT NULL REFERENCES fastcoloc.messages(id) ON DELETE CASCADE,
 dedup_key text NOT NULL UNIQUE,status text NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','PROCESSING','SENT','FAILED','SKIPPED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 6),next_attempt_at timestamptz NOT NULL DEFAULT now()+interval '2 minutes',
 lease_token uuid,lease_until timestamptz,last_error_code varchar(40),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK((status='PROCESSING')=(lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX notification_due ON fastcoloc.notification_events(next_attempt_at) WHERE status IN('PENDING','PROCESSING');
ALTER TABLE fastcoloc.notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE fastcoloc.notification_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fastcoloc.notification_preferences,fastcoloc.notification_events FROM PUBLIC,anon,authenticated,fastcoloc_backend;
GRANT SELECT,INSERT ON fastcoloc.notification_preferences TO fastcoloc_backend;
GRANT UPDATE(new_message_email,updated_at) ON fastcoloc.notification_preferences TO fastcoloc_backend;
CREATE POLICY notification_preferences_self ON fastcoloc.notification_preferences TO fastcoloc_backend
 USING(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=notification_preferences.user_id AND a.status='ACTIVE'))
 WITH CHECK(user_id=nullif(current_setting('fastcoloc.user_id',true),'')::uuid AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=notification_preferences.user_id AND a.status='ACTIVE'));
CREATE FUNCTION fastcoloc.queue_message_notification() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE c fastcoloc.conversations;recipient uuid;
BEGIN
 SELECT * INTO c FROM fastcoloc.conversations WHERE id=NEW.conversation_id;
 recipient:=CASE WHEN NEW.sender_id=c.owner_id THEN c.guest_id ELSE c.owner_id END;
 IF NOT coalesce((SELECT new_message_email FROM fastcoloc.notification_preferences WHERE user_id=recipient),true) THEN RETURN NEW;END IF;
 INSERT INTO fastcoloc.notification_events(recipient_id,conversation_id,message_id,dedup_key)
 VALUES(recipient,c.id,NEW.id,c.id::text||':'||recipient::text||':'||floor(extract(epoch FROM NEW.created_at)/300)::text)
 ON CONFLICT(dedup_key) DO UPDATE SET message_id=excluded.message_id,updated_at=now() WHERE notification_events.status IN('PENDING','PROCESSING');
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION fastcoloc.queue_message_notification() FROM PUBLIC,anon,authenticated,fastcoloc_backend;
CREATE TRIGGER new_message_notification AFTER INSERT ON fastcoloc.messages FOR EACH ROW EXECUTE FUNCTION fastcoloc.queue_message_notification();
CREATE FUNCTION fastcoloc.notification_eligible(eid uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
 SELECT EXISTS(SELECT 1 FROM fastcoloc.notification_events e JOIN fastcoloc.conversations c ON c.id=e.conversation_id JOIN fastcoloc.messages m ON m.id=e.message_id
 JOIN fastcoloc.account_access recipient ON recipient.user_id=e.recipient_id AND recipient.status='ACTIVE' JOIN fastcoloc.account_access sender ON sender.user_id=m.sender_id AND sender.status='ACTIVE'
 JOIN auth.users u ON u.id=e.recipient_id WHERE e.id=eid AND coalesce(u.email,'')<>'' AND coalesce((SELECT new_message_email FROM fastcoloc.notification_preferences WHERE user_id=e.recipient_id),true)
 AND e.message_id>CASE WHEN e.recipient_id=c.owner_id THEN c.owner_read_id ELSE c.guest_read_id END)
$$;
CREATE FUNCTION fastcoloc.notification_claim(batch_size integer) RETURNS TABLE(event_id uuid,email text,conversation uuid,lease uuid) LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE e fastcoloc.notification_events;token uuid;
BEGIN
 UPDATE fastcoloc.notification_events SET status='FAILED',lease_token=NULL,lease_until=NULL,last_error_code='ATTEMPTS_EXHAUSTED',updated_at=now() WHERE status='PROCESSING' AND lease_until<now() AND attempts>=6;
 FOR e IN SELECT * FROM fastcoloc.notification_events WHERE (status='PENDING' OR (status='PROCESSING' AND lease_until<now())) AND next_attempt_at<=now() AND attempts<6
 ORDER BY next_attempt_at,id LIMIT least(greatest(batch_size,1),10) FOR UPDATE SKIP LOCKED LOOP
  IF NOT fastcoloc.notification_eligible(e.id) THEN
   UPDATE fastcoloc.notification_events SET status='SKIPPED',lease_token=NULL,lease_until=NULL,last_error_code='READ_OR_DISABLED',updated_at=now() WHERE id=e.id;
  ELSE
   token:=gen_random_uuid();UPDATE fastcoloc.notification_events SET status='PROCESSING',attempts=attempts+1,lease_token=token,lease_until=now()+interval '5 minutes',updated_at=now() WHERE id=e.id;
   RETURN QUERY SELECT e.id,u.email::text,e.conversation_id,token FROM auth.users u WHERE u.id=e.recipient_id;
  END IF;
 END LOOP;
END $$;
CREATE FUNCTION fastcoloc.notification_ready(eid uuid,token uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
 SELECT EXISTS(SELECT 1 FROM fastcoloc.notification_events WHERE id=eid AND lease_token=token AND status='PROCESSING' AND lease_until>now()) AND fastcoloc.notification_eligible(eid)
$$;
CREATE FUNCTION fastcoloc.notification_finish(eid uuid,token uuid,outcome text,error_code text) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
BEGIN
 IF outcome NOT IN('SENT','SKIPPED','RETRY') OR (error_code IS NOT NULL AND error_code !~ '^[A-Z_]{1,40}$') THEN RAISE EXCEPTION 'Invalid delivery outcome' USING ERRCODE='22023';END IF;
 UPDATE fastcoloc.notification_events SET status=CASE WHEN outcome='RETRY' THEN CASE WHEN attempts>=6 THEN 'FAILED' ELSE 'PENDING' END ELSE outcome END,
 next_attempt_at=CASE WHEN outcome='RETRY' THEN now()+make_interval(secs=>least(3600,30*power(2,attempts-1))::integer) ELSE next_attempt_at END,
 lease_token=NULL,lease_until=NULL,last_error_code=error_code,updated_at=now() WHERE id=eid AND lease_token=token AND status='PROCESSING';
END $$;
REVOKE ALL ON FUNCTION fastcoloc.notification_eligible(uuid),fastcoloc.notification_claim(integer),fastcoloc.notification_ready(uuid,uuid),fastcoloc.notification_finish(uuid,uuid,text,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION fastcoloc.notification_claim(integer),fastcoloc.notification_ready(uuid,uuid),fastcoloc.notification_finish(uuid,uuid,text,text) TO fastcoloc_backend;
COMMIT;
