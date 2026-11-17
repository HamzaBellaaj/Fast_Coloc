-- Sprint 2, additive only. Apply once, separately from historical migrations.
-- No grants to anon/authenticated. Existing Sprint 1 policies/data are preserved.
BEGIN;
-- Public catalogue joins only non-personal geographical reference data.
-- Existing authenticated onboarding policies remain in place.
CREATE POLICY listings_geography_read ON fastcoloc.governorates FOR SELECT TO fastcoloc_backend USING(true);
CREATE POLICY listings_geography_read ON fastcoloc.localities FOR SELECT TO fastcoloc_backend USING(true);
CREATE TABLE fastcoloc.listings (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 author_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN ('OFFER','REQUEST')),
 title varchar(160) NOT NULL CHECK(length(trim(title))>=5),
 description varchar(10000) NOT NULL CHECK(length(trim(description))>=20),
 locality_id bigint NOT NULL REFERENCES fastcoloc.localities(id),
 street varchar(250) NOT NULL CHECK(length(trim(street))>0),
 latitude numeric(9,6) NOT NULL CHECK(latitude BETWEEN 30 AND 38),
 longitude numeric(9,6) NOT NULL CHECK(longitude BETWEEN 7 AND 12),
 rent numeric(12,3) NOT NULL CHECK(rent BETWEEN 0 AND 999999999.999),
 capacity smallint NOT NULL CHECK(capacity BETWEEN 1 AND 50),
 property_type varchar(80) NOT NULL CHECK(length(trim(property_type))>0),
 roommate_type varchar(80) NOT NULL DEFAULT '',
 equipment text[] NOT NULL DEFAULT '{}' CHECK(cardinality(equipment)<=30 AND array_position(equipment,NULL) IS NULL),
 available_from date NOT NULL, available_until date CHECK(available_until>=available_from),
 publication_days integer NOT NULL CHECK(publication_days BETWEEN 1 AND 365),
 status text NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PUBLISHED')),
 published_at timestamptz, expires_at timestamptz,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK((status='DRAFT' AND published_at IS NULL AND expires_at IS NULL) OR
       (status='PUBLISHED' AND published_at IS NOT NULL AND expires_at>published_at))
);
CREATE INDEX listings_author ON fastcoloc.listings(author_id,updated_at DESC,id);
CREATE INDEX listings_locality ON fastcoloc.listings(locality_id);
CREATE INDEX listings_newest ON fastcoloc.listings(published_at DESC,id) WHERE status='PUBLISHED';
CREATE INDEX listings_kind_rent ON fastcoloc.listings(kind,rent,id) WHERE status='PUBLISHED';
CREATE INDEX listings_coordinates ON fastcoloc.listings(latitude,longitude) WHERE status='PUBLISHED';
CREATE INDEX listings_equipment ON fastcoloc.listings USING gin(equipment);

CREATE TABLE fastcoloc.listing_photos (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 listing_id uuid NOT NULL REFERENCES fastcoloc.listings(id) ON DELETE CASCADE,
 object_path text NOT NULL UNIQUE,
 mime_type text NOT NULL CHECK(mime_type IN ('image/jpeg','image/png','image/webp')),
 byte_size bigint NOT NULL CHECK(byte_size BETWEEN 1 AND 5242880),
 caption varchar(500) NOT NULL DEFAULT '',
 display_order integer NOT NULL CHECK(display_order>=0),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(listing_id,display_order)
);
CREATE TABLE fastcoloc.listing_favorites (
 user_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 listing_id uuid NOT NULL REFERENCES fastcoloc.listings(id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,listing_id)
);
CREATE INDEX listing_favorites_listing ON fastcoloc.listing_favorites(listing_id);

ALTER TABLE fastcoloc.listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE fastcoloc.listing_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE fastcoloc.listing_favorites ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON fastcoloc.listings,fastcoloc.listing_photos,fastcoloc.listing_favorites FROM PUBLIC,anon,authenticated;
GRANT SELECT,DELETE ON fastcoloc.listings TO fastcoloc_backend;
GRANT INSERT(id,author_id,kind,title,description,locality_id,street,latitude,longitude,rent,capacity,property_type,roommate_type,equipment,available_from,available_until,publication_days)
 ON fastcoloc.listings TO fastcoloc_backend;
GRANT UPDATE(kind,title,description,locality_id,street,latitude,longitude,rent,capacity,property_type,roommate_type,equipment,available_from,available_until,publication_days,status,published_at,expires_at,updated_at,version)
 ON fastcoloc.listings TO fastcoloc_backend;
GRANT SELECT,INSERT,DELETE ON fastcoloc.listing_photos,fastcoloc.listing_favorites TO fastcoloc_backend;

CREATE POLICY listings_read ON fastcoloc.listings FOR SELECT TO fastcoloc_backend USING (
 author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 OR (status='PUBLISHED' AND expires_at>statement_timestamp())
);
CREATE POLICY listings_create ON fastcoloc.listings FOR INSERT TO fastcoloc_backend WITH CHECK (
 author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND EXISTS(SELECT 1 FROM fastcoloc.profiles p JOIN fastcoloc.account_access a ON a.user_id=p.id
 WHERE p.id=author_id AND p.completed_at IS NOT NULL AND a.status='ACTIVE')
);
CREATE POLICY listings_edit ON fastcoloc.listings FOR UPDATE TO fastcoloc_backend
 USING(author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=author_id AND a.status='ACTIVE'))
 WITH CHECK(author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=author_id AND a.status='ACTIVE'));
CREATE POLICY listings_delete ON fastcoloc.listings FOR DELETE TO fastcoloc_backend USING(
 author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=author_id AND a.status='ACTIVE'));

CREATE POLICY listing_photos_read ON fastcoloc.listing_photos FOR SELECT TO fastcoloc_backend USING(
 EXISTS(SELECT 1 FROM fastcoloc.listings l WHERE l.id=listing_id));
CREATE POLICY listing_photos_create ON fastcoloc.listing_photos FOR INSERT TO fastcoloc_backend WITH CHECK(
 EXISTS(SELECT 1 FROM fastcoloc.listings l JOIN fastcoloc.account_access a ON a.user_id=l.author_id
 WHERE l.id=listing_id AND l.author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND a.status='ACTIVE' AND split_part(object_path,'/',1)=l.author_id::text AND split_part(object_path,'/',2)=l.id::text));
CREATE POLICY listing_photos_delete ON fastcoloc.listing_photos FOR DELETE TO fastcoloc_backend USING(
 EXISTS(SELECT 1 FROM fastcoloc.listings l JOIN fastcoloc.account_access a ON a.user_id=l.author_id
 WHERE l.id=listing_id AND l.author_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid) AND a.status='ACTIVE'));
CREATE POLICY listing_favorites_read ON fastcoloc.listing_favorites FOR SELECT TO fastcoloc_backend USING(
 user_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid));
CREATE POLICY listing_favorites_create ON fastcoloc.listing_favorites FOR INSERT TO fastcoloc_backend WITH CHECK(
 user_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid)
 AND EXISTS(SELECT 1 FROM fastcoloc.account_access a WHERE a.user_id=listing_favorites.user_id AND a.status='ACTIVE')
 AND EXISTS(SELECT 1 FROM fastcoloc.listings l WHERE l.id=listing_id AND l.status='PUBLISHED' AND l.expires_at>statement_timestamp()));
CREATE POLICY listing_favorites_delete ON fastcoloc.listing_favorites FOR DELETE TO fastcoloc_backend USING(
 user_id=(SELECT nullif(current_setting('fastcoloc.user_id',true),'')::uuid));
COMMIT;
