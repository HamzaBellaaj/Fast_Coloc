-- Read only the six editable contact fields through the checked administrative capability.
BEGIN;
CREATE FUNCTION fastcoloc.admin_read_contacts(target uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,fastcoloc AS $$
DECLARE result jsonb;
BEGIN
 PERFORM fastcoloc.require_admin();
 IF NOT EXISTS(SELECT 1 FROM fastcoloc.profiles WHERE id=target)
 THEN RAISE EXCEPTION 'FC_NOT_FOUND' USING ERRCODE='P0002'; END IF;
 SELECT jsonb_build_object('mobile_phone',mobile_phone,'residential_phone',residential_phone,
  'call_availability',call_availability,'msn_contact',msn_contact,'skype_contact',skype_contact,'website_url',website_url)
 INTO result FROM fastcoloc.contact_details WHERE user_id=target;
 RETURN coalesce(result,'{}'::jsonb);
END $$;
REVOKE ALL ON FUNCTION fastcoloc.admin_read_contacts(uuid) FROM PUBLIC,anon,authenticated,fastcoloc_backend;
GRANT EXECUTE ON FUNCTION fastcoloc.admin_read_contacts(uuid) TO fastcoloc_backend;
COMMIT;
