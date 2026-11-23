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

## US25 — Estimation du loyer

`POST /api/v1/ai/rent-estimate` réutilise le Bearer et les rôles USER/ADMIN existants.
Envoyer un multipart avec `logement` (partie `application/json` : `localisation`,
`type`, `street`, `description`, `capacity`, `equipment`) et 1 à 10 parties `photo`
(JPEG, PNG ou WebP, 5 MiB maximum chacune). Le loyer actuel ne fait pas partie du DTO.
La description réutilise les caractéristiques saisies, notamment superficie et
mobilier lorsqu’ils sont renseignés dans le texte du formulaire existant.

Spring transmet `message` et `photo` au workflow existant, puis valide la réponse.
Pour plusieurs images, `RentEstimatePhotos` compose une planche numérotée incluant
toutes les vues, sans recadrage. Le workflow lit toujours seulement `file0` : cette
planche lui donne toutes les photos en une seule analyse, sans changer n8n ni
multiplier les appels IA/Tavily. Les fichiers originaux restent ceux de l’annonce.
La requête entrante accepte 51 MiB au total ; chaque photo reste limitée à 5 MiB.
Aucune estimation n’est enregistrée en base.
`FASTCOLOC_RENT_WEBHOOK_URL` configure l’URL ; la valeur par défaut utilise
`http://localhost:32768/webhook/49bec6bd-ad64-4cee-ae90-d0567511a05d`, port Windows
confirmé accessible (conteneur n8n : `32768 -> 5678`).
`FASTCOLOC_RENT_TIMEOUT_SECONDS` vaut 180 par défaut ; les clients attendent
200 secondes. Un dépassement retourne `504 ESTIMATE_TIMEOUT`, sans prix de secours.

Vérification ciblée : `mvn.cmd -B -ntp -Dtest=RentEstimateTest package`.
Un appel réel, facultatif et unique : ajouter `-Drent.live=true`.
Vérification du 21 novembre 2026 : appel réel réussi en environ 26 secondes,
réponse conservée dans `target/rent-estimate-live.json`. Deux corrections côté
Spring étaient nécessaires : HTTP/1.1 explicite (pas d’upgrade h2c) et partie
`message` sans Content-Type. Sinon n8n interprétait le texte comme `file0` et
la photo comme `file1`, puis échouait dans Analyze image. La photo est désormais
le seul fichier binaire (`file0`) et le champ HTTP reste `photo`.
Aucun conteneur ni workflow n’a été modifié. Le scénario multiphoto a également
réussi sur le workflow réel (environ 37 secondes). Le test du contrat vérifie que
deux vues de couleurs différentes figurent toutes deux dans l’image transmise.

## US26 — Pour toi ✨

`POST /api/v1/recommendations/ai`, Bearer USER/ADMIN, sans identifiant utilisateur
fourni par le client. Le JWT détermine le compte. `RecommendationData` prépare un
instantané dans une transaction courte, puis `RecommendationGateway` appelle n8n
en HTTP/1.1 hors transaction. Aucune table ni politique RLS n’a été ajoutée.

Préférences réelles utilisées : budgets min/max, date d’emménagement et localités
préférées. Les annonces favorites parmi les candidates sont signalées à l’IA.
Aucun swipe n’existe dans l’implémentation actuelle. Aucun mobilier ou équipement
préféré n’est inventé. Le payload exclut contacts, biographie, adresse privée,
photos de profil, informations démographiques et jetons. Les contacts éventuels
dans les titres/équipements d’annonces sont masqués avant l’envoi.

Au maximum 30 offres publiées, non masquées, non expirées et appartenant à un autre
auteur sont candidates. Les IDs reçus sont validés et dédupliqués ; le serveur
conserve l’ordre reçu, limite à 10 et recharge les données réelles via le service
d’annonces. Une annonce devenue inaccessible pendant le calcul est écartée.
Sans candidate : liste vide immédiate, aucun appel n8n. En cas d’erreur IA : erreur
HTTP avec message et nouvelle tentative explicite, sans classement de secours.

Configuration : `FASTCOLOC_RECOMMENDATIONS_WEBHOOK_URL` (défaut :
`http://localhost:32768/webhook/fastcoloc-recommendations`) et
`FASTCOLOC_RECOMMENDATIONS_TIMEOUT_SECONDS` (180). Les clients attendent 200 s.
Réponse : `{success, profileIncomplete, recommendations:[{listing,score,raison}]}`.

Vérifications : `mvn.cmd -B -ntp -Dtest=RecommendationServiceTest package` et
`node tools/test-recommendations.cjs` (un seul appel IA réel, compte temporaire
supprimé en fin de recette). Le 21 novembre 2026 : 15 candidates réelles,
3 recommandations valides en 45 secondes, ordre reçu conservé, accès anonyme 401.
Preuves sans identifiants de connexion : `target/recommendations-live-report.json`
et `target/recommendations-n8n-evidence.json` (exécution n8n 520).

## Messagerie privée REST — Ayoub

La migration additive `supabase/migrations/20261123193000_private_messaging.sql` a été appliquée une seule fois au projet configuré. Ne pas rejouer les migrations historiques. Conversations et messages ont RLS activée ; le rôle runtime lit seulement les discussions dont il est participant et écrit par des fonctions SQL à capacité limitée. Aucun accès général ADMIN aux discussions privées.

- `GET /api/v1/conversations?page=0&size=20` : boîte de réception et compteurs non lus. Le filtre facultatif `listingId` retrouve la discussion du visiteur pour une annonce, indépendamment de la pagination.
- `POST /api/v1/conversations` avec `{listingId, content, clientId}` : premier message et création/réutilisation atomique. Aucun brouillon vide.
- `GET /api/v1/conversations/{id}` : contexte et participant.
- `GET /api/v1/conversations/{id}/messages?before=ID&size=30` : historique par curseur ; réponses en ordre chronologique, `hasMore` et `nextBefore`.
- `POST /api/v1/conversations/{id}/messages` avec `{content, clientId}` : texte de 1 à 4000 caractères, 30 nouveaux messages/minute par expéditeur.
- `PUT /api/v1/conversations/{id}/read` avec `{throughId}` : curseur de lecture monotone, limité à un message de cette discussion.

Le client génère un UUID `clientId` et le réutilise lors d'une reprise du même envoi : le serveur ne crée pas de doublon. Le même UUID ne peut pas porter un texte différent. Pas d'auto-contact ; comptes actifs et profils terminés requis pour envoyer. Une annonce doit être publiée, visible et non expirée pour un nouveau contact. Les discussions existantes restent accessibles après expiration/masquage/suppression de l'annonce ; leur contexte minimal est conservé. La suppression d'un compte supprime ses conversations par cascade. Les suspensions administratives sont contrôlées ; le blocage personnel sera intégré avec le futur module de blocage.

Validation : PostgreSQL isolé avec rôle non propriétaire et vrais JWT/HTTP ; tests d'accès tiers/ADMIN, RLS, pagination, non-lus, reprise idempotente, débit et suppression d'annonce. Vérification distante effectuée sur trois comptes temporaires supprimés ensuite. Aucun message envoyé aux utilisateurs existants. Notifications, pièces jointes, WebSockets et accusés de lecture visibles par l'autre participant ne font pas partie de cette livraison.

## Notifications métier — workflow local n8n

Premier événement livré : **nouveau message privé non lu**, depuis Angular ou Android.
La migration additive `supabase/migrations/20261123202000_message_notifications.sql` a été appliquée seule ; ne pas rejouer les migrations historiques.
L'insertion du message crée un événement durable dans la même transaction. Le worker appelle n8n séparément : une panne SMTP/webhook ne supprime pas le message.

### Environnement installé sur ce PC

- n8n : http://localhost:32768 ; workflow **FastColoc — Business notifications (local inbox)**.
- Boîte de test Mailpit : http://localhost:8025. Les e-mails sont capturés localement ; aucune livraison vers une messagerie externe n'est configurée.
- Identifiants du propriétaire n8n : variables N8N_OWNER_EMAIL / N8N_OWNER_PASSWORD dans `infra/notifications/.env.local`, ignoré par Git.
- Secrets de webhook, chiffrement et base n8n générés localement ; aucun secret Supabase n'est copié dans n8n.
- Ce nouvel environnement contient le workflow de notification uniquement ; il n'installe pas les workflows IA d'estimation/recommandations.

Pour reproduire le setup, avec Docker Desktop démarré, depuis la racine :

~~~powershell
node infra/notifications/setup.cjs
node infra/notifications/connect-backend.cjs
node infra/notifications/test-workflow.cjs
~~~

Le setup préserve les volumes et un workflow déjà installé afin de conserver les modifications faites dans l'éditeur n8n. Le script connect-backend refuse de remplacer un autre endpoint déjà configuré. Redémarrer le backend après connexion.
Pour arrêter les services, sans supprimer leurs données :

~~~powershell
docker compose --env-file infra/notifications/.env.local -f infra/notifications/compose.yaml stop
~~~

### Comportement et contrat

- GET/PATCH /api/v1/notification-preferences ; PATCH accepte {"newMessageEmail":false}. Identité dérivée du JWT, préférences protégées par RLS. Le profil Web et les paramètres Android partagent ces valeurs.
- Rappel après environ deux minutes ; regroupement par conversation/destinataire/tranche de cinq minutes. Un message lu, une préférence désactivée ou un compte suspendu annule les événements encore en attente.
- Le worker utilise des baux, un backoff exponentiel et six tentatives maximum. Un événement épuisé reste FAILED pour diagnostic. Pas de destinataire, contenu de message ou secret dans les logs de livraison.
- POST vers /webhook/fastcoloc-notifications avec Authorization: Bearer (secret local) et Idempotency-Key. Corps limité à eventId, type=NEW_MESSAGE, to et conversationId. Le destinataire est relu côté serveur dans Auth ; le client ne fournit pas d'adresse e-mail.
- Succès uniquement après réponse {"eventId":"...","status":"sent"} ou "duplicate". Le workflow répond après acceptation SMTP et enregistrement dans son ledger PostgreSQL. Une simple réponse "queued" n'est pas considérée comme une livraison.
- Le ledger supprime les rejeux d'événements déjà terminés et sérialise les traitements simultanés. SMTP ne garantit pas une livraison exactement une fois si le serveur accepte le mail et que le processus meurt avant l'enregistrement du résultat ; une API fournisseur idempotente sera préférable pour ce cas en production.
- Transport Java vers n8n en HTTP/1.1, comme les intégrations n8n existantes. Pas de redirection automatique du secret ; HTTPS requis hors loopback.

Le profil indique explicitement le mode local. Pour tester à la main, envoyer un message à un autre compte puis laisser la discussion non lue environ deux minutes et consulter Mailpit. Pour passer à un vrai fournisseur, configurer une identité SMTP validée dans n8n, adapter l'adresse expéditrice et les liens, activer HTTPS et changer FASTCOLOC_NOTIFICATION_MODE en external. Ne jamais stocker les identifiants dans workflow.json.

Les e-mails Auth existants sont inchangés. Alertes de nouvelles annonces, SMS et push ne sont pas activés par ce premier workflow. Références : [n8n Webhook](https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.webhook/), [n8n Send Email](https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.sendemail/), [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).

Test SMTP de rejet/reprise, réservé à la boîte locale : `node infra/notifications/test-workflow.cjs --smtp-retry`. Le test configure temporairement Mailpit pour rejeter SMTP puis restaure les paramètres dans un bloc finally. Les contrôles de chaos sont désactivés en pratique (probabilités 0) hors de ce test.

### Migration vers n8n Cloud / Gmail (branche Ayoub)

Le brouillon **FastColoc - Private message emails (Gmail Cloud)** utilise Gmail OAuth2 et un ledger distinct dans le schema `fastcoloc_notifications`. La migration additive `20261123215000_cloud_notification_ledger.sql` a ete appliquee seule. Le role `fastcoloc_mailer` peut uniquement lire/inserer/modifier ce ledger ; il ne peut pas lire les messages, profils ou utilisateurs Auth. Aucun mot de passe administrateur Supabase ne doit etre copie dans n8n.

Fichiers de connexion ignores par Git :
- `infra/notifications/.env.cloud.local` : URL MCP, jeton MCP, secret du webhook Cloud, mot de passe du role mailer et ID du workflow.
- `infra/notifications/.env.cloud.credentials.json` : champs a copier dans les credentials n8n **FastColoc Cloud webhook authentication** (Header Auth) et **FastColoc Cloud notification ledger** (Postgres, SSL Require).
- Creer separement **FastColoc Gmail** (Gmail OAuth2) avec **Sign in with Google** dans n8n Cloud. Ne pas partager les mots de passe ou jetons dans le chat.

Depuis la racine du depot :
~~~powershell
node infra/notifications/setup-cloud.cjs status
node infra/notifications/setup-cloud.cjs prepare
node infra/notifications/setup-cloud.cjs provision-ledger
node infra/notifications/setup-cloud.cjs bind
~~~

`prepare` valide le workflow avec le SDK n8n avant creation, sauvegarde un brouillon inactif dans le projet personnel et preserve un brouillon deja enregistre. `provision-ledger` exige Java 21, le pilote JDBC Maven deja installe et le mot de passe administrateur local ; il teste ensuite le login restreint et les refus d'acces aux donnees privees. `bind` associe uniquement des credentials reellement presents dans le projet. Aucun de ces scripts ne publie le workflow ou ne remplace la configuration du backend.

Le template reproductible est `infra/notifications/workflow.cloud.json`. Il reste en texte simple en attendant le travail de design. Le noeud **Email configuration** contient l'URL de l'application ; sa valeur est issue de `FASTCOLOC_NOTIFICATION_APP_URL` lors de la creation. Une URL localhost convient aux tests sur ce PC ; les autres utilisateurs auront besoin d'une URL Web accessible publiquement.

Contrat Cloud : POST `/webhook/fastcoloc-message-emails`, avec le secret Header Auth propre au Cloud. Le succes Gmail est enregistre avec l'ID du message fournisseur avant la reponse `sent`. Les reprises deja terminees donnent `duplicate`; les baux en cours et erreurs fournisseur donnent une erreur permettant la reprise. Gmail ne garantit pas l'exactement-une-fois si le processus s'interrompt entre l'acceptation par Google et l'enregistrement du ledger.

**Etat de migration :** les trois credentials Cloud sont connectes et le workflow est publie. Un test reel a ete accepte par Gmail et enregistre SENT avec un ID fournisseur. Le webhook de production a passe les controles sans authentification, payload invalide et rejeu du meme evenement sans nouvel envoi. Le backend utilise maintenant ce webhook HTTPS avec `FASTCOLOC_NOTIFICATION_MODE=external` et a ete redemarre. Ne jamais utiliser l'URL MCP comme URL de livraison.

Commandes de verification :
~~~powershell
# Aucun nouvel e-mail : rejoue le dernier test deja envoye.
node infra/notifications/test-cloud.cjs
# Un nouvel e-mail reel au destinataire configure explicitement dans .env.cloud.local.
node infra/notifications/test-cloud.cjs --send-test
# Connexion au Cloud uniquement apres un smoke test reussi.
node infra/notifications/connect-backend.cjs --cloud
~~~

Le test technique utilise un UUID de discussion de test sans conversation dans l'application. Pour verifier un lien fonctionnel, envoyer un vrai message entre deux comptes de test FastColoc puis laisser le destinataire sans lire la discussion environ deux minutes. Gmail accepte les messages, mais le placement dans la boite de reception doit etre confirme dans le compte destinataire.

References : [Gmail OAuth2 gere par n8n Cloud](https://docs.n8n.io/integrations/builtin/credentials/google/oauth-single-service/), [envoi Gmail](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.gmail/message-operations/).
