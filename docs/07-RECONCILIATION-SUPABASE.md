# Réconciliation Supabase FastColoc — vérifiée le 11 novembre 2026

Projet exclusif : `nklsbubnyznlfpxpxsrp`. Inspection distante réelle en lecture seule, via MCP `fastcoloc` et via l'outil Supabase `list_tables` avec project_id explicite. Aucune modification distante.

## État constaté

14 tables privées : countries, governorates, localities, profiles, account_access,
profile_addresses, personal_details, contact_details, roommate_preferences,
preferred_localities, profile_photos, onboarding_steps, consent_records, account_access_events.

76 colonnes, 14 clés primaires, 18 clés étrangères, 5 contraintes UNIQUE, 17 CHECK et 24 index.
RLS activée sur chaque table, sans FORCE RLS, sans politique. Propriétaire : postgres.
Les ACL du schéma, des tables et des quatre séquences ne donnent de droits qu'à postgres.
Les vérifications has_schema_privilege/has_table_privilege confirment l'absence de droits
USAGE/CREATE et SELECT/INSERT/UPDATE/DELETE pour anon et authenticated.
Le résultat vide information_schema.table_privileges sous supabase_read_only_user
n'a pas été utilisé seul pour conclure sur les droits.

## Historique réellement lu

| Version | Nom |
|---|---|
| 20261111203735 | sprint1_initial_private_schema |
| 20261111203809 | restrict_rls_event_trigger_function_execution |

Le SQL enregistré de la première migration correspond exactement au brouillon
`backend/src/main/resources/db/drafts/V001__sprint1_proposal.sql`, en ignorant les
commentaires, espaces et enveloppes BEGIN/COMMIT. Types, tailles, nullabilité,
valeurs par défaut, contraintes, relations et index inspectés concordent.
Ce brouillon reste historique et hors exécution automatique. Ne jamais le rejouer
sur la base distante et ne pas le promouvoir dans Flyway.

La seconde migration contient :
```sql
REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM PUBLIC, anon, authenticated;
```

Fonction constatée : propriétaire postgres, SECURITY DEFINER, search_path=pg_catalog,
ACL postgres/service_role uniquement. L'event trigger ensure_rls est actif mais
la fonction n'applique automatiquement la RLS qu'au schéma public. Les futures
tables fastcoloc doivent donc toujours activer explicitement la RLS.
Aucun trigger métier fastcoloc : updated_at et complétion ne sont pas automatiques.

L'historique Supabase reste l'autorité de migration distante. Aucun baseline,
repair, db push, migration ou grant distant n'a été exécuté.

## Implémentation backend locale

Application Java 21 / Spring Boot 3.5.16, 14 entités JPA avec schema explicite,
FK représentées par identifiants scalaires sans cascade JPA vers Auth.
Hibernate valide le schéma ; SQL init, Flyway et Liquibase désactivés.
Voir `backend/README.md` pour les commandes, endpoints et validations.

Le JWKS public FastColoc a été interrogé : clé EC, ES256 publiée.
Le décodeur accepte ES256 uniquement et vérifie issuer, audience, exp/nbf,
iat, sub et session_id UUID, role=authenticated et is_anonymous=false.
Les rôles métier proviennent exclusivement de fastcoloc.account_access.

Après JWT valide, GET Supabase /auth/v1/user vérifie l'UUID, le caractère non
anonyme et email_confirmed_at. Aucun profil avant confirmation réelle.
E-mail et Google suivent le même contrat ; aucun nom, rôle ou statut n'est
importé depuis user_metadata. Les premières connexions Web/mobile sont
sérialisées par verrou transactionnel, sans écraser les profils existants.
La synchronisation crée six lignes (profil, accès, adresse, informations
personnelles, contacts, préférences), sans valider artificiellement l'onboarding.

## Connexion JDBC et droits minimaux

Connexion JDBC distante désormais testée réellement avec les paramètres fournis
par l'utilisateur, sous postgres uniquement pour le diagnostic READ ONLY.
Pooler session aws-1-eu-north-1.pooler.supabase.com:5432, PostgreSQL 17.11.
TLS client vers pooler validé par CA Supabase et sslmode=verify-full.
Le rôle fastcoloc_backend est absent et pg_policies retourne zéro policy fastcoloc.
Le démarrage métier avec rôle dédié reste bloqué. Aucun mot de passe enregistré.
Attention : pg_stat_ssl côté PostgreSQL indique ssl=false pour la connexion
pooler vers PostgreSQL ; la vérification TLS client ne prouve pas un TLS de bout
en bout. Voir 10-JDBC-VERIFICATION.md pour les résultats exacts.

`backend/tools/runtime-role.proposal.sql` référence maintenant la migration locale
`20261111220503_provision_fastcoloc_backend.sql`, non appliquée au distant : rôle
fastcoloc_backend créé NOLOGIN sans secret, NOBYPASSRLS, non
propriétaire et sans rôle hérité. SELECT soumis à RLS et INSERT limité aux seules
colonnes nécessaires à la synchronisation. Aucun droit UPDATE/DELETE, séquence,
DDL ou Auth. Aucune permission attribuée à anon/authenticated.
La proposition ne retire plus CREATE à PUBLIC : elle contrôle son absence et
refuse un état non conforme. Droits de colonnes, BLOCKED, idempotence, extension
onboarding désactivée et rollback sont détaillés dans 11-POSTGRES-PROVISIONING-REVIEW.md.
L'identité est fixée par set_config transaction-local à partir du sub signé ;
elle est effacée au commit/rollback et ne fuit pas entre connexions du pool.

Les opérations d'administration métier futures devront recevoir des capacités DB
spécifiques revues : aucune politique fondée sur un rôle fourni par le client.
Le rôle ADMIN permet ici de vérifier l'autorisation sur GET /api/v1/admin/access,
sans mutation administrative.

Le démarrage refuse un rôle administrateur, propriétaire, BYPASSRLS, membre d'un
autre rôle, ou un schéma dont une table a RLS désactivée. TLS verify-full et CA
de confiance exigés par défaut. Préférer connexion directe ou pooler session.

## Limites fonctionnelles conservées

Les cinq étapes restent identité, géographie, informations personnelles, contacts,
images. Les tables de budget/recherche ne les remplacent pas.
La cohérence pays/localité n'est pas imposée par une contrainte composée.
Les quotas/taille maximale des photos, âge minimum, taxonomies et consentements
restent à décider. Les endpoints des étapes, Storage et administration complète
ne font pas partie de cet incrément de socle backend.

Google Cloud, provider Google, PKCE/callbacks Web/Android, confirmation obligatoire,
SMTP et liens de récupération nécessitent une configuration et une recette
externes. Leur activation distante n'est pas déduite de la présence du JWKS.

## Vérification locale

La suite JUnit/Mockito démarre une vraie application HTTP avec PostgreSQL local
isolé, le schéma historique et le rôle runtime proposé. Elle vérifie les
signatures ES256 via serveur JWKS local, la validation JPA et la RLS réelle.
Supabase Auth est simulé dans ces tests ; son adaptateur HTTP est testé séparément.
Les résultats exécutés et leurs limites sont détaillés dans `09-BACKEND-IMPLEMENTATION.md`.
