# Backend Sprint 1 — implémentation et résultats réels

Vérification finale : 11 novembre 2026, fuseau Africa/Lagos.
Ce rapport conserve les résultats des 24 tests initiaux. La revue de provisionnement
et les nouveaux tests sont décrits dans 11-POSTGRES-PROVISIONING-REVIEW.md.
Périmètre réalisé : socle backend demandé (12 points de la demande), pas encore
la totalité des écrans et fonctionnalités métier de l'ensemble du Sprint 1.

## Livrable et endpoints

Application Java 21, Spring Boot 3.5.16 compilée et empaquetée :
`backend/target/fastcoloc-backend-0.1.0-SNAPSHOT.jar`.

| Méthode | Route | Comportement |
|---|---|---|
| GET | /api/v1/health | Liveness publique |
| POST | /api/v1/users/sync | Provisionnement idempotent, identité confirmée, transaction |
| GET | /api/v1/users/me | Identité/profil propre, rôle/statut serveur, progression |
| GET | /api/v1/admin/access | Vérification du rôle ADMIN, sans mutation |

Le serveur HTTP a réellement démarré sur port éphémère pendant les tests.
Aucun serveur durable n'est laissé sur 8001 : le démarrage avec Supabase réel
reste conditionné à la configuration JDBC et au rôle dédié.

Architecture d'authentification reprise d'Immovia en lecture seule : Nimbus/JWKS,
Resource Server stateless, issuer/dates/audience/claims, CORS et erreurs JSON.
Ajouts FastColoc : Auth /user pour confirmation réelle, rôle DB et blocage à chaque
requête, contexte SQL transactionnel, synchronisation concurrente et RLS.
Aucun compte, clé privée, mot de passe ou secret Immovia utilisé.

## Tests exécutés

Commande finale, depuis backend : `mvn.cmd -B -ntp verify`.
Résultat : **BUILD SUCCESS — 24 tests, 0 échec, 0 erreur, 0 ignoré**.

| Classe | Tests | Résultat |
|---|---:|---|
| BackendIntegrationTest | 17 | Réussite |
| RuntimeDatabaseGuardTest | 3 | Réussite |
| SupabaseAuthIdentityVerifierTest | 4 | Réussite |

Preuves brutes locales : `backend/target/surefire-reports/*.txt`, rapports XML,
et `backend/target/verify-run.log` (fichiers générés ignorés par Git).

Les tests d'intégration utilisent un vrai PostgreSQL 14.20 isolé, sans Docker.
Le schéma Auth est simulé par une table UUID minimale ; le schéma métier local
est construit depuis le brouillon identique à la migration initiale inspectée.
La proposition runtime est appliquée seulement à cette base jetable, jamais au distant.
Les 14 mappings JPA passent ddl-auto=validate sous fastcoloc_backend.
Les lectures JPA couvrent aussi text[], JSONB, char(3) et identifiants composés.

Cas réellement vérifiés :

- HTTP public et 401 sans Bearer ; signatures ES256 via JWKS HTTP local.
- Refus des signatures fausses, HS256, JWT non signés, issuer/audience invalides,
  dates expirées/futures, UUID/claims invalides et utilisateurs anonymes.
- Création de six lignes liées ; relecture JPA ; synchronisation répétée sans
  écrasement des noms, sans réactivation et sans promotion implicite.
- Quatre premières synchronisations concurrentes : un seul agrégat complet.
- Refus d'un compte BLOCKED avec JWT toujours valide, en lecture comme en sync.
- ADMIN issu de la table ; user_metadata.role=ADMIN n'accorde aucun privilège.
- Aucun accès au profil d'autrui ; aucune identité sélectionnable dans le DTO.
- Refus de champs inconnus, rôle/statut/completedAt injectés, âge et nom invalides.
- Aucun profil avant confirmation ; panne Auth : refus fermé et aucune écriture.
- Rollback sur FK Auth absente et sur erreur tardive après plusieurs INSERT.
- Isolation RLS réelle entre UUID ; contexte de connexion effacé après transaction.
- Refus UPDATE/promotion, INSERT hors identité et accès anon/authenticated.
- Refus de CREATE TABLE dans public et fastcoloc pour le rôle runtime.
- Refus CORS pour origine inconnue ; contrôles du rôle JDBC et du TLS au démarrage.
- Adaptateur HTTP Auth testé avec réponses e-mail/Google, identité différente,
  compte anonyme, confirmation falsifiée dans metadata, erreurs 401 et 503.

Supabase Auth est remplacé par Mockito dans la suite HTTP/PostgreSQL ; l'adaptateur
Auth est testé séparément avec un serveur HTTP local. Ces tests ne constituent
pas une recette Google Cloud, SMTP, de lien e-mail ou de session Supabase réelle.
Ils ne prouvent pas non plus le fonctionnement métier sous rôle runtime distant.
Un diagnostic JDBC distant en READ ONLY a ensuite réussi sur PostgreSQL 17.11,
avec vérification TLS client/pooler ; voir 10-JDBC-VERIFICATION.md.

## Fichiers créés ou modifiés

Sous `backend/` :

- `pom.xml` : build Java 21, dépendances Spring/JPA/PostgreSQL et tests.
- `src/main/resources/application.properties` : configuration active, validation
  seule, aucun SQL init/migration, secrets par environnement.
- `.env.example` : noms attendus, TLS, rôle et clé publique FastColoc.
- `config/application-supabase.prepared.properties` : modèle historique signalé.
- `src/main/java/tn/fastcoloc/FastColocApplication.java` : application.
- `entity/` : Country, Governorate, Locality, Profile, AccountAccess,
  ProfileAddress, PersonalDetails, ContactDetails, RoommatePreferences,
  PreferredLocality, ProfilePhoto, OnboardingStep, ConsentRecord, AccountAccessEvent.
  Ces fichiers se trouvent sous `src/main/java/tn/fastcoloc/`.
- `security/` : SecurityConfig, SupabaseAccessTokenValidator,
  AuthIdentityVerifier, SupabaseAuthIdentityVerifier, AccountAccessFilter,
  AccountAuthorizationService, DatabaseIdentity, RuntimeDatabaseGuard.
- `profiles/` : ProfileController, ProfileService, ProfileRepository,
  SyncRequest, ProfileResponse.
- `api/` : HealthController, ApiError, ApiException, ApiExceptionHandler.
- `src/test/java/tn/fastcoloc/` : les trois classes du tableau de tests.
- `tools/runtime-role.proposal.sql` : rôle/policies/grants proposés et testés localement.
- `postman/FastColoc-Sprint1.postman_collection.json` et
  `postman/FastColoc-local.postman_environment.example.json` : collection et environnement.
- `README.md` : démarrage, contrats, configuration, stratégie RLS et limites.

Documents actualisés : `07-RECONCILIATION-SUPABASE.md`, `08-MCP-CONNECTION.md`
et présent rapport. Le brouillon SQL historique et les sources originales PDF/Word
ne sont pas modifiés. Aucun fichier Immovia modifié. Aucun dépôt Git n'est présent
à la racine ou dans FastColoc : aucun commit n'est créé.

## Blocages et configuration externe

1. **JDBC distant confirmé pour le diagnostic seulement** : le compte postgres
   fourni a été utilisé en READ ONLY et sans persistance du mot de passe.
   Le pooler session, le certificat CA et sslmode=verify-full fonctionnent côté
   client. Le rôle fastcoloc_backend est absent ; son mot de passe reste à
   provisionner. Ne pas configurer postgres comme runtime.
2. **Droits distants à préparer** : le schéma inspecté ne dispose pas des policies
   et grants runtime proposés. `tools/runtime-role.proposal.sql` est le résultat
   concret à revoir avant toute application distante autorisée. Mot de passe à
   provisionner séparément ; aucune modification distante effectuée ici.
   La version corrigée contrôle l'absence de CREATE pour PUBLIC sans modifier
   cette ACL partagée. Elle crée le rôle NOLOGIN, sans secret : voir document 11.
3. La **clé publishable publique FastColoc a été récupérée via MCP** et figure dans
   .env.example et l'environnement Postman. Ce n'est pas une clé service_role.
   La renseigner dans l'environnement du processus Spring ; .env n'est pas chargé
   automatiquement. Aucune clé serveur Supabase nécessaire pour ces endpoints.
4. **Google/e-mail** : activation provider Google, clients Cloud, callbacks exacts,
   PKCE Web/Android, confirmation obligatoire, SMTP, expéditeur et liens à configurer
   et tester avec les vrais clients. Aucun de ces réglages n'a été modifié ici.
5. **Postman** : JSON préparés et validés, collection distante non exécutée faute
   de serveur connecté et de comptes/tokens de recette. Mots de passe et tokens
   restent uniquement dans l'environnement local de l'utilisateur.

## Suite métier

Les étapes d'édition onboarding, validation finale atomique selon règles métier,
upload/URLs Storage, consentements et administration CRUD restent à implémenter
dans leurs incréments dédiés. Les quotas photos, âge minimum et taxonomies restent
à décider. Le rôle runtime actuel est volontairement limité au socle livré et ne
peut pas modifier des comptes ni des profils par UPDATE/DELETE.

Le contrôle session_id vérifie le format du claim, pas auth.sessions. Une
déconnexion Supabase ne garantit pas la révocation immédiate de tous les JWT ;
le blocage métier, lui, est relu sans cache à chaque requête autorisée.

Références de contrat consultées :
- https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://supabase.com/docs/guides/auth/jwt-fields
- https://supabase.com/docs/guides/database/postgres/row-level-security
