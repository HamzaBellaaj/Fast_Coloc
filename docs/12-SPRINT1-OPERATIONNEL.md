# FastColoc Sprint 1 — résultat opérationnel du 12 novembre 2026

Périmètre demandé : backend Java 21 / Spring Boot 3.5.16, Supabase Auth,
PostgreSQL privé, profils, inscription en cinq étapes, photos et administration.
Aucun fichier Immovia modifié, aucune application graphique Angular ou Android développée.

## Fonctionnement obtenu

Le backend démarre contre PostgreSQL Supabase 17.11 avec le login du Session
Pooler fastcoloc_backend.nklsbubnyznlfpxpxsrp ; current_user est bien
fastcoloc_backend, sans SUPERUSER, BYPASSRLS, propriété des tables ou héritage.
TLS client vers pooler utilise verify-full et la CA Supabase. Le garde au
démarrage et la validation Hibernate des 14 mappings passent réellement.

L'API tourne localement sur http://localhost:8001 ; Swagger est accessible à
http://localhost:8001/swagger-ui/index.html et le contrat à /v3/api-docs.
Le démarrage charge backend/.env.local, ignoré par Git, sans secret dans les sources.
Le mot de passe PostgreSQL n'est pas incorporé dans l'URL JDBC.

Les routes sont listées dans backend/README.md et dans la collection Postman.
Le workflow complet est implémenté : signup, confirmation, login, refresh, logout,
récupération/vérification/réinitialisation, sync idempotent, profil détaillé et
modification, cinq étapes sauvegardées, complétion atomique, préférences, photos
privées, liste ADMIN, correction des contacts, blocage/déblocage et suppression.

L'inscription Google possède les routes PKCE authorize/exchange et rejoint le
même parcours de cinq étapes, sans mot de passe local. Le provider distant,
initialement désactivé, est activé au contrôle final : l'API fournit l'URL et
Supabase redirige réellement en 302 vers accounts.google.com. Une connexion
interactive avec consentement Google n'a pas été exécutée et n'est pas déclarée validée.

## Modifications distantes réellement effectuées

Les trois migrations déjà appliquées ont été conservées sans réexécution :

| Version distante | Migration |
|---|---|
| 20261111203735 | sprint1_initial_private_schema |
| 20261111203809 | restrict_rls_event_trigger_function_execution |
| 20261112045303 | provision_fastcoloc_backend |
| 20261112052450 | sprint1_backend_capabilities — nouvelle migration de cette mission |

Cette dernière migration est justifiée par les fonctionnalités nouvelles : droits
de colonnes sur les étapes et préférences, politiques propres/ACTIVE, complétion
contrôlée et opérations ADMIN ciblées. Elle ne crée aucune table supplémentaire
et n'altère pas les identifiants du rôle applicatif. Les 14 tables sont conservées.
Le fichier de préparation local porte la version CLI 20261112050615 ; les versions
distantes sont celles du tableau. L'historique local incomplet ne doit pas être
envoyé par db push/repair/reset. L'application ne lance aucune migration.

Les fonctions SECURITY DEFINER ajoutées sont dans fastcoloc privé, avec search_path
fixé, EXECUTE retiré à PUBLIC/anon/authenticated et attribué aux seules capacités
nécessaires du backend. Elles vérifient le sujet, son statut et, pour l'admin,
son rôle en base. Le helper require_admin n'est pas exécutable par le runtime.
Le runtime n'obtient ni UPDATE général de account_access ni promotion libre.

Le bucket profile-photos a été créé via l'API Storage : privé, limite 5 MiB,
MIME JPEG/PNG/WebP. Aucun droit client direct au schéma privé, aucune désactivation
RLS et aucune mutation SQL directe de storage.objects.

Les tables géographiques auparavant vides ont été initialisées par un seed de
données idempotent : 252 pays/territoires, 24 gouvernorats et 284 localités peuplées
tunisiennes. Source GeoNames, licence CC BY 4.0, sans identifiants générés codés
en dur. Provenance, dates, URLs et SHA-256 : GEOGRAPHY-SOURCE.json ; générateur
backend/tools/prepare-geography.cjs et seed backend/tools/geography.seed.sql.
Référence : https://www.geonames.org/export/ et
https://download.geonames.org/export/dump/readme.txt.

## Vérifications locales

Commande exigée : mvn.cmd -B -ntp verify.
**Résultat : BUILD SUCCESS, 46 tests, aucun échec, erreur ou test ignoré.**

| Suite | Tests | Infrastructure |
|---|---:|---|
| BackendIntegrationTest | 28 | PostgreSQL local, rôle minimal, JWT ES256/JWKS HTTP local |
| Sprint1IntegrationTest | 10 | PostgreSQL local avec capacités Sprint 1, simulateurs Auth/Storage |
| RuntimeDatabaseGuardTest | 3 | JUnit/Mockito |
| SupabaseAuthIdentityVerifierTest | 4 | Adaptateur Auth avec serveur HTTP local |
| ConfigurationContractTest | 1 | Contrat de configuration empaquetée, sans divulgation de valeurs |

La suite couvre validation des claims/signatures, RLS, absence de droits clients,
séquences interdites, profils concurrents, rollback tardif, rôle/membership/grants
inattendus, cinq étapes, reprise des données, complétion/invalidation, préférences,
photo non décodable, quota, patch de description, droits ADMIN et audit, comptes
bloqués, suppression avec cascade Auth simulée, paramètres invalides et OpenAPI Bearer.
Les tests locaux n'envoient aucun e-mail et ne sont pas assimilés aux preuves distantes.

La première compilation finale avait rencontré le verrou Windows du JAR en cours
d'exécution. Les scripts start-backend/stop-backend lancent désormais une copie
du JAR dans target/run et vérifient l'identité du processus avant de l'arrêter.
La compilation exigée a ensuite été relancée et vérifiée complètement.
Un remplacement des propriétés spring.datasource.* par des valeurs FASTCOLOC_*
dans le fichier source a également été détecté au redémarrage final. Les références
externes ont été restaurées et le mot de passe retiré du fichier source.
ConfigurationContractTest empêche les tests à configuration DB injectée de masquer
de nouveau une telle erreur de démarrage ou l'empaquetage d'identifiants locaux.

## Vérifications réelles Supabase

backend/tools/RemoteSprint1E2E.java a réussi **53 contrôles** en passant par l'API
Spring réelle, Auth/JWKS, PostgreSQL et Storage distants :

- Connexion de deux comptes temporaires créés via l'API Auth Admin, jamais par
  fabrication SQL de auth.users. Comptes marqués par un identifiant de test.
- JWT réels, sync répétée, profil propre, refus sans jeton et profil non synchronisé.
- JDBC runtime sans contexte : aucun profil ; contexte propre : une ligne ;
  tentative de promotion SQL : refus 42501.
- Référentiels réels, sauvegarde des cinq étapes, refus de complétion prématurée,
  completion timestamp persistant et préférence/budget invalide refusé.
- Upload PNG réel, URL signée téléchargeable, accès public non signé refusé,
  autre propriétaire refusé et suppression privée réussie.
- USER refusé sur ADMIN ; promotion temporaire par l'opérateur uniquement pour
  la fixture ; liste, blocage, refus lecture/sync/SQL BLOCKED, correction des
  contacts, déblocage et suppression Auth/Storage/DB.
- Refresh, récupération via un vrai lien généré pour la fixture par Auth Admin,
  vérification du hash, réinitialisation, ancien mot de passe refusé, connexion
  avec le nouveau mot de passe, déconnexion et refresh révoqué.

Les deux comptes temporaires et leurs objets sont nettoyés en finally. Les
événements d'audit sont conservés ; leurs références supprimées suivent SET NULL.
Le profil de l'utilisateur réel n'a pas reçu de données d'inscription fictives.
Les essais temporaires utilisent l'administration seulement pour leurs fixtures,
jamais comme connexion Spring.

## Vérification humaine e-mail et compte ADMIN initial

Une inscription réelle a été envoyée à l'adresse contrôlée fournie par l'utilisateur.
Réponse 202. L'utilisateur a confirmé avoir cliqué le lien ; l'API Auth et une
connexion réelle ont ensuite confirmé email_confirmed_at. Sync a réussi.

L'utilisateur a explicitement approuvé ce compte comme premier ADMIN. L'outil
BootstrapAdmin.java a vérifié l'identité confirmée/non anonyme, le profil actif
et l'absence d'autre ADMIN, puis effectué et journalisé la promotion par le canal
opérateur. Aucun rôle n'a été tiré de user_metadata. Le mot de passe de test est
uniquement dans backend/.env.local ; il n'est pas reproduit dans ce rapport.

Une demande réelle de récupération vers cette adresse a été acceptée en 202.
La réception de ce second mail n'a pas été confirmée par l'utilisateur. Le reset
complet distant a été vérifié séparément sur une fixture par un lien Auth réel.

## Postman réellement exécuté

La collection complète et l'environnement exemple sont dans backend/postman.
Le dossier Smoke a été exécuté par Newman 6.2.1 : **10 requêtes, 11 assertions,
aucun échec**, contre le backend connecté au Supabase réel et le compte confirmé.
Cela couvre notamment login, confirmation, sync, profil détaillé, accès ADMIN,
liste des utilisateurs et OpenAPI. Les autres dossiers sont les scénarios manuels
complets avec leurs variables de cible ; ils ne sont pas présentés comme un run
Newman intégral. Le workflow complet est couvert par les 53 contrôles précédents.

Le fichier d'environnement Newman sensible est créé uniquement dans target et
supprimé en finally ; aucun export contenant les tokens n'est écrit. Les logs
serveur et Newman ont été contrôlés : aucune valeur des secrets configurés ni
motif de JWT détecté. Le scan des sources non ignorées ne retrouve aucun secret
fourni. Ce contrôle ciblé ne remplace pas un audit universel des secrets.

Preuves locales générées :

- backend/target/surefire-reports et sprint1-verify.log ;
- backend/target/remote-e2e-report.json (labels et compteurs, sans tokens) ;
- backend/target/postman-smoke.log ;
- backend/target/server.stdout.log et server.stderr.log.

## Actions externes et limites explicitement restantes

1. **Google** : le contrôle final retourne googleEnabled=true et l'initiation
   OAuth a été testée : HTTP 302 vers accounts.google.com. Les identifiants Google
   n'ont pas été lus ni copiés par l'agent. Il reste à tester la connexion
   interactive et le consentement dans le navigateur, avec le verifier PKCE
   conservé par le client. La redirection Google Cloud doit être
   https://nklsbubnyznlfpxpxsrp.supabase.co/auth/v1/callback.
   Référence : https://supabase.com/docs/guides/auth/social-sign-in/auth-google.
2. **URLs et templates Auth personnalisés** : fichiers FastColoc prêts dans
   backend/config/email-templates ; script backend/tools/configure-auth.cjs prêt
   pour Site URL, allowlist et templates, ainsi que Google si ses deux identifiants
   sont fournis. Il nécessite un Management API token autorisé, distinct de la clé
   serveur Auth, qui n'est pas disponible dans cette mission. Aucune installation
   de templates ou modification de redirections n'est prétendue. Le mail Supabase
   existant fonctionne réellement. Callback client préparé :
   http://localhost:4200/auth/callback ; aucun client graphique n'est construit ici.
   Référence : https://supabase.com/docs/reference/api/v1-update-auth-service-config.
3. **Production** : SMTP dédié et limites d'envoi à configurer selon le besoin.
   Le linter signale la protection contre les mots de passe compromis désactivée :
   https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection.
   Le seul autre signal est l'absence de policy sur account_access_events, voulue :
   le runtime ne lit pas le journal et les capacités ADMIN y écrivent après contrôle.
   https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy.

Le système ne promet pas une transaction distribuée Auth/Storage/PostgreSQL.
La suppression bloque d'abord puis nettoie et peut être reprise ; un upload
compense un rollback, et une compensation en panne demande une reprise opérateur.
Les URLs signées restent valides 60 secondes après émission. La déconnexion
révoque les refresh tokens mais ne promet pas de révoquer instantanément tous
les access tokens JWT. Le statut BLOCKED est relu à chaque requête métier.

Le contexte SQL est défini par le serveur depuis le sub validé. Un détenteur du
secret JDBC peut fixer un autre contexte : RLS ne protège pas d'une compromission
totale de ce serveur. Aucun droit direct n'a été ouvert aux futurs clients.
Enfin, le diagnostic précédent distingue TLS Java vers pooler (vérifié) de la
liaison pooler vers PostgreSQL signalée ssl=false par pg_stat_ssl ; aucun TLS de
bout en bout n'est affirmé ou modifié pendant cette mission.

## Fichiers principaux livrés

- Modules backend/auth, onboarding, media, admin et enrichissement profiles/api/security
  sous backend/src/main/java/tn/fastcoloc.
- DTO fermés, validation, logs sans payload sensible, OpenAPI/Swagger et filtre Auth.
- Migration Sprint 1 et seed géographique sourcé ; aucun nouveau schéma métier/table.
- Tests locaux, RemoteSprint1E2E.java, RuntimeConnectionProbe.java et BootstrapAdmin.java.
- Scripts de démarrage/arrêt, de génération et d'exécution Postman, et configure-auth.cjs.
- backend/.env.example sans secrets, backend/.env.local ignoré, CA publique et templates.
- backend/README.md, GEOGRAPHY-SOURCE.json et présent bilan.

Les documents 07 à 11 décrivent les étapes antérieures et leurs limites d'alors.
Le présent rapport et le README backend donnent l'état final, notamment le rôle
runtime maintenant opérationnel et la quatrième migration effectivement appliquée.
