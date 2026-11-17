-- US29 only. Additive model, no existing business row updated or deleted.
BEGIN;
ALTER TABLE fastcoloc.listings ADD COLUMN is_hidden boolean NOT NULL DEFAULT false;
CREATE TABLE fastcoloc.listing_moderation_events(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 -- Historical identifiers intentionally survive deletion of the listing/account.
 listing_id uuid NOT NULL, listing_title varchar(160) NOT NULL,
 actor_id uuid NOT NULL, actor_name text NOT NULL,
 action text NOT NULL CHECK(action IN ('HIDE','RESTORE','EDIT','DELETE')),
 reason varchar(1000) NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 1000),
 before_state jsonb NOT NULL, after_state jsonb,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX listing_moderation_history ON fastcoloc.listing_moderation_events(listing_id,created_at DESC,id);
ALTER TABLE fastcoloc.listing_moderation_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fastcoloc.listing_moderation_events FROM PUBLIC,anon,authenticated,fastcoloc_backend;
-- No client/runtime grant on is_hidden; only the checked administrative entry point writes it.
ALTER POLICY listings_read ON fastcoloc.listings USING (
 author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 OR (status='PUBLISHED' AND NOT is_hidden AND expires_at>statement_timestamp())
);
ALTER POLICY listing_favorites_create ON fastcoloc.listing_favorites WITH CHECK (
 user_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=listing_favorites.user_id AND a.status='ACTIVE')
 AND EXISTS(SELECT 1 FROM fastcoloc.listings l WHERE l.id=listing_favorites.listing_id
   AND l.status='PUBLISHED' AND NOT l.is_hidden AND l.expires_at>statement_timestamp())
);
CREATE FUNCTION fastcoloc.guard_hidden_listing() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,fastcoloc AS $$
BEGIN
 IF current_user='fastcoloc_backend' AND
  (NEW.is_hidden IS DISTINCT FROM OLD.is_hidden OR
   OLD.is_hidden AND (NEW.status IS DISTINCT FROM OLD.status OR
     NEW.published_at IS DISTINCT FROM OLD.published_at OR NEW.expires_at IS DISTINCT FROM OLD.expires_at))
 THEN RAISE EXCEPTION 'FC_HIDDEN' USING ERRCODE='42501'; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION fastcoloc.guard_hidden_listing() FROM PUBLIC,anon,authenticated,fastcoloc_backend;
CREATE TRIGGER guard_hidden_listing BEFORE UPDATE ON fastcoloc.listings
 FOR EACH ROW EXECUTE FUNCTION fastcoloc.guard_hidden_listing();

CREATE FUNCTION fastcoloc.admin_listing_detail(target uuid,lock_row boolean DEFAULT false) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE row fastcoloc.listings; result jsonb; owner_id uuid;
BEGIN
 PERFORM fastcoloc.require_admin();
 IF lock_row THEN
  SELECT author_id INTO owner_id FROM fastcoloc.listings WHERE id=target;
  IF NOT FOUND THEN RAISE EXCEPTION 'FC_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text,0));
  SELECT * INTO row FROM fastcoloc.listings WHERE id=target FOR UPDATE;
 ELSE SELECT * INTO row FROM fastcoloc.listings WHERE id=target;
 END IF;
 IF NOT FOUND THEN RAISE EXCEPTION 'FC_NOT_FOUND' USING ERRCODE='P0002'; END IF;
 PERFORM fastcoloc.require_admin();
 SELECT to_jsonb(row)||jsonb_build_object('author_name',concat_ws(' ',a.first_name,a.last_name),
  'locality',p.name,'governorate',g.name,'governorate_id',g.id,
  'display_status',CASE WHEN row.is_hidden THEN 'HIDDEN' WHEN row.status='DRAFT' THEN 'DRAFT'
    WHEN row.expires_at<=statement_timestamp() THEN 'EXPIRED' ELSE 'PUBLISHED' END,
  'photos',coalesce((SELECT jsonb_agg(to_jsonb(ph) ORDER BY ph.display_order) FROM fastcoloc.listing_photos ph WHERE ph.listing_id=target),'[]'::jsonb))
 INTO result FROM fastcoloc.profiles a,fastcoloc.localities p,fastcoloc.governorates g
 WHERE a.id=row.author_id AND p.id=row.locality_id AND g.id=p.governorate_id;
 RETURN result;
END $$;

CREATE FUNCTION fastcoloc.admin_listings(filters jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
#variable_conflict use_variable
DECLARE result jsonb; p integer:=coalesce((filters->>'page')::integer,0);
 size integer:=coalesce((filters->>'size')::integer,20); sort text:=coalesce(filters->>'sort','NEWEST');
 state text:=nullif(filters->>'status',''); kind text:=nullif(filters->>'kind',''); q text:=coalesce(filters->>'q','');
 start_date date:=(filters->>'from')::date; end_date date:=(filters->>'to')::date;
BEGIN
 PERFORM fastcoloc.require_admin();
 IF p NOT BETWEEN 0 AND 1000 OR size NOT BETWEEN 1 AND 50 OR length(q)>160
 OR sort NOT IN ('NEWEST','OLDEST','RENT_ASC','RENT_DESC') OR kind NOT IN ('OFFER','REQUEST')
 OR state NOT IN ('DRAFT','PUBLISHED','EXPIRED','HIDDEN') OR start_date>end_date
 THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
 WITH matching AS (
 SELECT l.id,l.title,l.author_id,concat_ws(' ',a.first_name,a.last_name) AS author_name,l.kind,l.rent,
  l.status,l.is_hidden,l.created_at,l.updated_at,l.published_at,l.expires_at,l.version,
  p.name AS locality,g.name AS governorate,
  CASE WHEN l.is_hidden THEN 'HIDDEN' WHEN l.status='DRAFT' THEN 'DRAFT'
    WHEN l.expires_at<=statement_timestamp() THEN 'EXPIRED' ELSE 'PUBLISHED' END AS display_status
 FROM fastcoloc.listings l JOIN fastcoloc.profiles a ON a.id=l.author_id
 JOIN fastcoloc.localities p ON p.id=l.locality_id JOIN fastcoloc.governorates g ON g.id=p.governorate_id
 WHERE (kind IS NULL OR l.kind=kind)
 AND (q='' OR strpos(lower(l.title||' '||concat_ws(' ',a.first_name,a.last_name)||' '||l.author_id::text||' '||p.name||' '||g.name),lower(q))>0)
 AND (start_date IS NULL OR l.created_at>=start_date::timestamp AT TIME ZONE 'UTC')
 AND (end_date IS NULL OR l.created_at<(end_date+1)::timestamp AT TIME ZONE 'UTC')
 ), filtered AS (SELECT * FROM matching WHERE state IS NULL OR display_status=state), paged AS (
 SELECT * FROM filtered ORDER BY
 CASE WHEN sort='NEWEST' THEN created_at END DESC,
 CASE WHEN sort='OLDEST' THEN created_at END ASC,
 CASE WHEN sort='RENT_ASC' THEN rent END ASC,
 CASE WHEN sort='RENT_DESC' THEN rent END DESC,id LIMIT size OFFSET p*size)
 SELECT jsonb_build_object('items',coalesce((SELECT jsonb_agg(to_jsonb(paged)) FROM paged),'[]'::jsonb),
  'total',(SELECT count(*) FROM filtered),'page',p,'size',size) INTO result;
 RETURN result;
END $$;

CREATE FUNCTION fastcoloc.admin_listing_history(target uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
BEGIN
 PERFORM fastcoloc.require_admin();
 RETURN coalesce((SELECT jsonb_agg(to_jsonb(e) ORDER BY e.created_at DESC,e.id) FROM fastcoloc.listing_moderation_events e WHERE e.listing_id=target),'[]'::jsonb);
END $$;

CREATE FUNCTION fastcoloc.admin_listing_action(target uuid,operation text,payload jsonb,reason text,expected_version bigint) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE actor uuid; old_row fastcoloc.listings; new_state jsonb; actor_label text;
BEGIN
 actor:=fastcoloc.require_admin();
 PERFORM fastcoloc.admin_listing_detail(target,true);
 SELECT * INTO old_row FROM fastcoloc.listings WHERE id=target;
 IF reason IS NULL OR length(trim(reason)) NOT BETWEEN 1 AND 1000 OR operation NOT IN ('HIDE','RESTORE','EDIT','DELETE')
 THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
 IF expected_version IS NULL OR expected_version<>old_row.version THEN RAISE EXCEPTION 'FC_STALE' USING ERRCODE='40001'; END IF;
 IF operation='HIDE' AND old_row.is_hidden OR operation='RESTORE' AND NOT old_row.is_hidden
 THEN RAISE EXCEPTION 'FC_STATE' USING ERRCODE='40001'; END IF;
 SELECT concat_ws(' ',first_name,last_name) INTO actor_label FROM fastcoloc.profiles WHERE id=actor;
 IF operation='DELETE' THEN
  DELETE FROM fastcoloc.listings WHERE id=target; new_state:=NULL;
 ELSIF operation='EDIT' THEN
  IF NOT EXISTS(SELECT 1 FROM fastcoloc.localities p JOIN fastcoloc.governorates g ON g.id=p.governorate_id
    WHERE p.id=(payload->>'localityId')::bigint AND g.country_code='TN')
   THEN RAISE EXCEPTION 'FC_INVALID' USING ERRCODE='22023'; END IF;
  UPDATE fastcoloc.listings SET kind=payload->>'kind',title=trim(payload->>'title'),description=trim(payload->>'description'),
   locality_id=(payload->>'localityId')::bigint,street=trim(payload->>'street'),
   latitude=(payload->>'latitude')::numeric,longitude=(payload->>'longitude')::numeric,rent=(payload->>'rent')::numeric,
   capacity=(payload->>'capacity')::smallint,property_type=trim(payload->>'propertyType'),roommate_type=coalesce(trim(payload->>'roommateType'),''),
   equipment=ARRAY(SELECT jsonb_array_elements_text(payload->'equipment')),
   available_from=(payload->>'availableFrom')::date,available_until=(payload->>'availableUntil')::date,
   publication_days=(payload->>'publicationDays')::integer,version=version+1,updated_at=now() WHERE id=target;
  SELECT to_jsonb(l) INTO new_state FROM fastcoloc.listings l WHERE id=target;
 ELSE
  UPDATE fastcoloc.listings SET is_hidden=(operation='HIDE'),version=version+1,updated_at=now() WHERE id=target;
  SELECT to_jsonb(l) INTO new_state FROM fastcoloc.listings l WHERE id=target;
 END IF;
 INSERT INTO fastcoloc.listing_moderation_events(listing_id,listing_title,actor_id,actor_name,action,reason,before_state,after_state)
 VALUES(target,old_row.title,actor,actor_label,operation,trim(reason),to_jsonb(old_row),new_state);
 RETURN coalesce(new_state,'{}'::jsonb);
END $$;
REVOKE ALL ON FUNCTION fastcoloc.admin_listing_detail(uuid,boolean),fastcoloc.admin_listings(jsonb),
 fastcoloc.admin_listing_history(uuid),fastcoloc.admin_listing_action(uuid,text,jsonb,text,bigint)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION fastcoloc.admin_listing_detail(uuid,boolean),fastcoloc.admin_listings(jsonb),
 fastcoloc.admin_listing_history(uuid),fastcoloc.admin_listing_action(uuid,text,jsonb,text,bigint) TO fastcoloc_backend;
COMMIT;
