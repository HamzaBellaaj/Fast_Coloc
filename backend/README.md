# Backend FastColoc — Sprints 1 et 2

Sprint 2 : annonces offres/demandes, brouillons, photos, publication/renouvellement,
recherche paginée, positions cartographiques et favoris. La suite compte maintenant **57 tests**.
Voir [le contrat et la recette Sprint 2](../docs/17-SPRINT2-ANNONCES-RECHERCHE.md).
La migration approuvée `20261116215125_sprint2_listings.sql` est déjà appliquée ;
ne rejouer aucun historique SQL. Le rôle runtime et les données Sprint 1 sont conservés.

Java 21 / Spring Boot 3.5.16. Backend connecté au projet Supabase
`nklsbubnyznlfpxpxsrp`, schéma privé `fastcoloc`, sous le rôle applicatif
`fastcoloc_backend`. Le bilan initial est conservé dans le rapport 12 ; l’intégration
Android et sa recette sont décrites dans `../docs/14-SPRINT1-ANDROID-INTEGRATION.md`.
Aucune modification d’Immovia.

## Extension native Sprint 1

Les routes signup, forgot-password, resend-confirmation et Google acceptent le
paramètre optionnel `client=android`. Sans paramètre, le callback Web est inchangé.
Le callback mobile est fixé côté serveur à `fastcoloc://auth/callback` ; aucune URL
arbitraire n’est acceptée. Google Android exige aussi un state base64url de 43 caractères.
L’échange reste `/api/v1/auth/exchange` avec code et verifier PKCE.

Cette URL doit être autorisée dans Supabase Auth, en conservant les redirections
Web. Les tests de non-régression, du callback mobile et d’encodage des liens e-mail
portent la suite à 50 tests.

## Démarrer

Depuis ce dossier :
```powershell
mvn.cmd -B -ntp verify
.\tools\start-backend.ps1
```

Pour lancer sans fenêtre et conserver les logs :
```powershell
.\tools\start-backend.ps1 -Background
.\tools\stop-backend.ps1
```

Le script lance une copie du JAR dans target/run pour éviter que Windows verrouille
l'artefact Maven pendant une compilation suivante. stop-backend vérifie le chemin
du JAR avant de stopper le PID enregistré ; il ne stoppe aucun autre backend.

- API : http://localhost:8001/api/v1/health
- Swagger : http://localhost:8001/swagger-ui/index.html
- OpenAPI : http://localhost:8001/v3/api-docs

Swagger permet d'appeler /auth/login, puis de coller l'access_token dans Authorize.
Les routes privées déclarent le schéma Bearer ; les routes Auth publiques restent
accessibles sans jeton. Désactiver la documentation en production si souhaité avec
FASTCOLOC_API_DOCS_ENABLED=false.

## Configuration locale

### Lancement depuis VS Code

Si le dossier ouvert est la racine `FastColoc`, sélectionner **FastColoc — Backend
Spring Boot** dans **Exécuter et déboguer**, puis utiliser **F5** (ou **Ctrl+F5** sans
débogage). La configuration `.vscode/launch.json` fixe le répertoire de travail à
`backend`, nécessaire pour trouver `.env.local` et le certificat relatif sous `config/`.

Un lancement Java générique depuis la racine ne trouve pas ce fichier : l’erreur
`Driver org.postgresql.Driver claims to not accept jdbcUrl, ${FASTCOLOC_JDBC_URL}`
signale alors une configuration non chargée, pas un problème de dialecte Hibernate.
Ne pas copier les secrets dans `application.properties` ou `launch.json`.

Si le serveur lancé en arrière-plan occupe déjà le port 8001, l’arrêter avec
`.\tools\stop-backend.ps1` avant de démarrer une session VS Code.

`.env.local` existe, est ignoré par Git et est chargé explicitement par Spring
comme fichier properties. Ne pas l'écraser avec .env.example : celui-ci décrit
uniquement les variables. Aucun secret n'est nécessaire dans le code ou les arguments Java.

Variables applicatives : FASTCOLOC_JDBC_URL, FASTCOLOC_DB_USERNAME,
FASTCOLOC_DB_PASSWORD, FASTCOLOC_SUPABASE_PUBLISHABLE_KEY,
FASTCOLOC_SUPABASE_SECRET_KEY, FASTCOLOC_AUTH_CALLBACK_URL et
FASTCOLOC_FRONTEND_ORIGIN. SERVER_PORT vaut 8001 par défaut.

Le Session Pooler attend fastcoloc_backend.nklsbubnyznlfpxpxsrp comme login ; la
session PostgreSQL doit retourner current_user=fastcoloc_backend.
TLS utilise sslmode=verify-full et config/supabase-prod-ca.crt. Les mots de passe
sont passés séparément au pilote, pas dans l'URL. Le garde de démarrage refuse les
rôles élevés, propriétaires ou hérités et les tables métier sans RLS.
Hibernate valide le schéma ; SQL init, Flyway et Liquibase restent désactivés.

La clé serveur est utilisée uniquement par le backend pour Storage et les
opérations Auth administratives. Ne jamais la mettre dans Angular, Android ou
Postman. Les variables FASTCOLOC_DB_ADMIN_PASSWORD, FASTCOLOC_TEST_EMAIL et
FASTCOLOC_TEST_EMAIL_PASSWORD sont réservées aux outils opérateur/tests ;
Spring n'utilise jamais le compte postgres comme datasource.

## Parcours e-mail et Google

1. POST /api/v1/auth/signup avec email et password (12..72 caractères, maximum
   72 octets UTF-8). Réponse générique 202, aucun profil métier avant confirmation.
2. Cliquer le lien reçu, puis POST /api/v1/auth/login.
3. Utiliser access_token comme Bearer, POST /api/v1/users/sync avec {}.
4. Enregistrer les cinq étapes ; GET /api/v1/users/me/details permet de reprendre
   les champs et validatedSteps après reconnexion ou retour arrière.
5. POST /api/v1/onboarding/complete vérifie l'ensemble dans une transaction.

Supabase reste responsable des mots de passe et sessions. /auth/refresh renouvelle
la session ; /auth/logout accepte scope=local, global ou others. Le client doit
supprimer ses jetons à la déconnexion et sérialiser les refresh concurrents.
La révocation des refresh tokens est testée ; un access token JWT peut rester
cryptographiquement valide jusqu'à expiration. Le blocage métier est relu sans cache.

Mot de passe oublié : POST /auth/forgot-password ; après possession du lien,
POST /auth/verify avec tokenHash/type=recovery ou échange PKCE, puis PUT
/auth/password avec le Bearer de récupération. /auth/resend-confirmation permet
de renvoyer un lien, sous les limites Supabase et le throttle local.

Google ne demande aucun mot de passe local : générer un verifier PKCE dans le
client et son challenge S256, appeler GET /auth/google?codeChallenge=..., ouvrir
authorizationUrl dans le navigateur, puis POST /auth/exchange avec code/codeVerifier.
Le verifier reste lié au navigateur ayant lancé le flux. Sync et les cinq étapes
sont ensuite identiques. Au contrôle final, Google est activé dans Supabase : l'API
fournit l'URL OAuth et la redirection 302 vers accounts.google.com est vérifiée.
La connexion interactive et le consentement Google n'ont pas été exécutés.
Si le provider est désactivé, l'API retourne GOOGLE_NOT_CONFIGURED.

## API réalisée

Toutes les routes ci-dessous commencent par /api/v1.

| Groupe | Routes |
|---|---|
| Auth publiques | GET /auth/config, /auth/google ; POST /auth/signup, /auth/login, /auth/refresh, /auth/exchange, /auth/verify, /auth/forgot-password, /auth/resend-confirmation |
| Auth avec JWT | GET /auth/session ; POST /auth/logout ; PUT /auth/password |
| Profil | POST /users/sync ; GET /users/me, /users/me/details ; PUT /users/me, /users/me/contacts, /users/me/preferences |
| Inscription | PUT /onboarding/steps/1 à /5 ; POST /onboarding/complete |
| Géographie | GET /geography/countries, /geography/governorates?countryCode=TN, /geography/localities?governorateId=... |
| Photos | POST /profile-photos en multipart file/caption ; PATCH et DELETE /profile-photos/{id} ; GET /profile-photos/{id}/signed-url |
| ADMIN | GET /admin/access, /admin/users?limit=20&offset=0&search=...&status=... ; PATCH /admin/users/{id}/status, /contacts ; DELETE /admin/users/{id} avec reason |

Contrats exacts et exemples : Swagger et tools/build-postman.cjs.
Les DTO refusent les champs inconnus, UUID propriétaires, rôle, statut et
completedAt injectés. Le serveur dérive l'identité du sub vérifié.

Étapes : identité ; pays/gouvernorat/localité/adresse ; activités, loisirs,
nationalité, langues, religion facultative, tabac nullable, propreté, description ;
contacts ; photos déjà envoyées avec leurs descriptions. NULL pour un choix
personnel reste distinct de FALSE. Les préférences de budget/recherche ne
remplacent aucune de ces étapes.

Les photos sont privées : quota technique 5 photos, maximum 5 MiB et 20 millions
de pixels, JPEG/PNG/WebP décodables. Le serveur vérifie le fichier, construit le
chemin utilisateur/UUID et délivre des URLs signées de 60 secondes.
Une URL déjà émise reste utilisable jusqu'à son expiration après blocage.
Supprimer une photo invalide l'étape 5 et la complétion ; il faut la revalider.

Les référentiels incluent 252 pays/territoires, 24 gouvernorats tunisiens et
284 localités peuplées tunisiennes, issus de GeoNames (CC BY 4.0).
Voir ../docs/GEOGRAPHY-SOURCE.json. Ce n'est pas un annuaire mondial de localités
ni un référentiel officiel des codes postaux. Prévoir l'attribution GeoNames
dans les futurs clients utilisant ces données.

## Droits et administration

USER/ADMIN et ACTIVE/BLOCKED proviennent de account_access, jamais user_metadata.
Les clients ne reçoivent aucun accès SQL au schéma privé. Les accès ordinaires
restent filtrés par UUID et statut via RLS. Le détenteur du secret JDBC peut fixer
le contexte UUID : ce secret reste une frontière de confiance du serveur.

Les mutations ADMIN utilisent des fonctions privées à capacité limitée :
vérification d'un ADMIN actif, cible explicite, motif, audit et verrous cohérents.
Aucun UPDATE(app_role,status) général n'est accordé au rôle JDBC.
Le compte confirmé fourni a été promu ADMIN initial avec accord explicite et audit.

La suppression bloque d'abord le compte et valide cette transaction, nettoie son
préfixe Storage, puis utilise l'API Auth officielle ; la FK existante supprime
les données métier. Une panne externe laisse le compte bloqué pour reprise.
Ce n'est pas une transaction distribuée. Les uploads compensent un rollback SQL ;
une compensation Storage échouée nécessite une reprise opérateur.

## Tests réellement exécutés

- mvn.cmd -B -ntp verify : 46 tests locaux, aucun échec/erreur/ignoré.
- tools/RemoteSprint1E2E.java : 53 contrôles réels contre Supabase, comptes Auth
  temporaires et objets nettoyés en finally, audit conservé.
- tools/run-postman-smoke.ps1 : 10 requêtes et 11 assertions réelles réussies.
- Confirmation reçue et cliquée par l'utilisateur ; connexion et sync du compte
  confirmé réussies. Demande de récupération réelle acceptée (202).

Les tests locaux utilisent PostgreSQL 14.20 isolé et un simulateur Auth/Storage ;
les tests distants utilisent PostgreSQL 17.11, Auth/JWKS et Storage réels.
La récupération distante complète utilise un lien réel généré par l'API Admin
pour un compte temporaire ; la réception du mail de récupération n'est pas déclarée vérifiée.

Preuves générées ignorées par Git : target/surefire-reports,
target/sprint1-verify.log, target/remote-e2e-report.json, target/postman-smoke.log.
Le dossier Smoke utilise le compte confirmé localement configuré, sans placer
la clé serveur dans Postman. Les autres dossiers de la collection sont des
scénarios manuels avec leurs variables de cible et leurs fixtures.

## Migrations et opérations externes restantes

Les trois migrations initiales n'ont pas été rejouées. Une seule migration
nécessaire a été ajoutée : sprint1_backend_capabilities, version distante
20261112052450, pour les étapes, préférences, complétion contrôlée et capacités ADMIN.
Le fichier local de préparation est 20261112050615_sprint1_backend_capabilities.sql.
Le dossier local historique n'est pas un miroir complet des versions distantes :
ne pas lancer db push/reset/repair et ne pas réappliquer les fichiers initiaux.
Spring ne joue aucune migration au démarrage.

Google est maintenant activé ; il reste à effectuer la recette navigateur avec
consentement et échange PKCE. Si les redirections sont adaptées, le callback
Google Cloud doit être https://nklsbubnyznlfpxpxsrp.supabase.co/auth/v1/callback.
Vérifier Site URL et l'allowlist pour http://localhost:4200/auth/callback.
Les templates FastColoc confirmation/recovery sont dans config/email-templates.

tools/configure-auth.cjs peut appliquer URLs/templates et Google avec un
FASTCOLOC_MANAGEMENT_ACCESS_TOKEN autorisé, distinct de la clé serveur Auth.
Ce token n'est pas disponible ; ces modifications de configuration n'ont pas
été exécutées. Le mail de confirmation actuel Supabase fonctionne, mais les
templates personnalisés préparés ne sont pas déclarés installés.
Un SMTP dédié et la protection contre les mots de passe compromis restent à
configurer dans le dashboard selon le plan et les besoins de production.

Rapport complet : ../docs/12-SPRINT1-OPERATIONNEL.md.
