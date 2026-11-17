-- REQUEST has a locality, not necessarily a physical home. Existing values are preserved.
BEGIN;
ALTER TABLE fastcoloc.listings ALTER COLUMN latitude DROP NOT NULL, ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE fastcoloc.listings DROP CONSTRAINT listings_street_check;
ALTER TABLE fastcoloc.listings ADD CONSTRAINT listings_offer_location_check CHECK (kind <> 'OFFER' OR (length(trim(street))>0 AND latitude IS NOT NULL AND longitude IS NOT NULL));
ALTER TABLE fastcoloc.listings ADD CONSTRAINT listings_coordinate_pair_check CHECK ((latitude IS NULL) = (longitude IS NULL));
COMMIT;
