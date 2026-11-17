-- FastColoc Sprint 1 / BROUILLON NON EXECUTE, NON TESTE PostgreSQL.
-- Voir docs/06-MODELE-DONNEES.md. Supabase auth.users/roles requis.
-- Hors db/migration volontairement; compte runtime/policies à préparer séparément.
BEGIN;
CREATE SCHEMA fastcoloc;
CREATE TABLE fastcoloc.countries(code char(2) PRIMARY KEY, name text NOT NULL);
CREATE TABLE fastcoloc.governorates(
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 country_code char(2) NOT NULL REFERENCES fastcoloc.countries(code),
 name text NOT NULL, UNIQUE(country_code,name));
CREATE TABLE fastcoloc.localities(
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 governorate_id bigint NOT NULL REFERENCES fastcoloc.governorates(id),
 name text NOT NULL, UNIQUE(governorate_id,name));
CREATE TABLE fastcoloc.profiles(
 id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
 first_name varchar(100), last_name varchar(100), gender varchar(80),
 age smallint CHECK(age BETWEEN 0 AND 130),
 completed_at timestamptz, version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE fastcoloc.account_access(
 user_id uuid PRIMARY KEY REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 app_role text NOT NULL DEFAULT 'USER' CHECK(app_role IN ('USER','ADMIN')),
 status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','BLOCKED')),
 blocked_at timestamptz, blocked_reason varchar(1000), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK((status='ACTIVE' AND blocked_at IS NULL) OR (status='BLOCKED' AND blocked_at IS NOT NULL)));
CREATE TABLE fastcoloc.profile_addresses(
 user_id uuid PRIMARY KEY REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 country_code char(2) REFERENCES fastcoloc.countries(code),
 locality_id bigint REFERENCES fastcoloc.localities(id), street varchar(250), postal_code varchar(20));
-- Cohérence pays/localité à vérifier par le service ou une contrainte supplémentaire après revue.
CREATE TABLE fastcoloc.personal_details(
 user_id uuid PRIMARY KEY REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 main_activities text[] NOT NULL DEFAULT '{}', hobbies text[] NOT NULL DEFAULT '{}',
 nationality_code char(2) REFERENCES fastcoloc.countries(code),
 spoken_languages text[] NOT NULL DEFAULT '{}', religion varchar(120),
 smoking boolean, cleanliness varchar(120), biography varchar(2000));
-- NULL = choix neutre/non renseigné. Taxonomies et limites finales à valider.
CREATE TABLE fastcoloc.contact_details(
 user_id uuid PRIMARY KEY REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 mobile_phone varchar(30), residential_phone varchar(30), call_availability varchar(250),
 msn_contact varchar(250), skype_contact varchar(250), website_url varchar(500));
CREATE TABLE fastcoloc.roommate_preferences(
 user_id uuid PRIMARY KEY REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 min_budget numeric(12,3) CHECK(min_budget>=0), max_budget numeric(12,3) CHECK(max_budget>=0),
 currency char(3) NOT NULL DEFAULT 'TND' CHECK(currency='TND'), move_in_date date,
 additional_preferences jsonb NOT NULL DEFAULT '{}'::jsonb,
 CHECK(min_budget IS NULL OR max_budget IS NULL OR min_budget<=max_budget),
 CHECK(jsonb_typeof(additional_preferences)='object'));
CREATE TABLE fastcoloc.preferred_localities(
 user_id uuid REFERENCES fastcoloc.roommate_preferences(user_id) ON DELETE CASCADE,
 locality_id bigint REFERENCES fastcoloc.localities(id), PRIMARY KEY(user_id,locality_id));
CREATE TABLE fastcoloc.profile_photos(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 bucket text NOT NULL DEFAULT 'profile-photos' CHECK(bucket='profile-photos'),
 object_path text NOT NULL UNIQUE, caption varchar(500),
 mime_type text NOT NULL CHECK(mime_type IN ('image/jpeg','image/png','image/webp')),
 byte_size bigint NOT NULL CHECK(byte_size>0), display_order integer NOT NULL CHECK(display_order>=0),
 is_primary boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,display_order), CHECK(split_part(object_path,'/',1)=user_id::text));
-- Quota et taille max à décider avant upload. Pas de bucket créé ici.
CREATE UNIQUE INDEX one_primary_photo ON fastcoloc.profile_photos(user_id) WHERE is_primary;
CREATE TABLE fastcoloc.onboarding_steps(
 user_id uuid REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 step_number smallint CHECK(step_number BETWEEN 1 AND 5),
 validated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,step_number));
CREATE TABLE fastcoloc.consent_records(
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES fastcoloc.profiles(id) ON DELETE CASCADE,
 consent_code varchar(100) NOT NULL, policy_version varchar(100) NOT NULL,
 accepted_at timestamptz NOT NULL DEFAULT now(), UNIQUE(user_id,consent_code,policy_version));
CREATE TABLE fastcoloc.account_access_events(
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject_id uuid REFERENCES fastcoloc.profiles(id) ON DELETE SET NULL,
 actor_id uuid REFERENCES fastcoloc.profiles(id) ON DELETE SET NULL,
 event_type text NOT NULL CHECK(event_type IN ('BLOCK','UNBLOCK','ROLE_CHANGE','DELETE','CONTACT_UPDATE')),
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX address_locality ON fastcoloc.profile_addresses(locality_id);
CREATE INDEX locality_governorate ON fastcoloc.localities(governorate_id);
CREATE INDEX access_status ON fastcoloc.account_access(status);
CREATE INDEX event_subject ON fastcoloc.account_access_events(subject_id,created_at);
-- Pas d'accès direct client aux données privées; Spring doit vérifier sub/rôles/blocage.
REVOKE ALL ON SCHEMA fastcoloc FROM PUBLIC,anon,authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA fastcoloc FROM PUBLIC,anon,authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA fastcoloc FROM PUBLIC,anon,authenticated;
DO $$
DECLARE item record;
BEGIN
 FOR item IN SELECT tablename FROM pg_tables WHERE schemaname='fastcoloc' LOOP
  EXECUTE format('ALTER TABLE fastcoloc.%I ENABLE ROW LEVEL SECURITY',item.tablename);
 END LOOP;
END $$;
-- Ajouter et tester explicitement le rôle JDBC runtime et ses GRANT/POLICY avant usage.
-- Aucune promotion ADMIN depuis user_metadata. Aucun trigger Auth ni secret copié.
COMMIT;