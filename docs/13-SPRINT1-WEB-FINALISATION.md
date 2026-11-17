# FastColoc Sprint 1 Web — livraison du 12 novembre 2026

## Résultat et périmètre

Application Angular créée dans `frontend/`, initialement limité à un README. Elle
utilise les API Spring Boot existantes, connectées à Auth/PostgreSQL/Storage FastColoc.
Aucun accès direct aux tables privées depuis le navigateur, aucune donnée simulée
dans les parcours livrés, aucune application Android et aucun module des sprints futurs.
Les documents historiques 01 à 12 sont conservés.

Le backend n’a nécessité aucune modification de code, de contrat, de permission ou
de migration. Aucun reset, repair, push de migrations ou changement du compte ADMIN
initial. Les opérations distantes de recette concernent uniquement des comptes
Auth temporaires identifiés et leurs photos ; le journal administratif est conservé.

## Fonctionnalités et routes

| Route | Fonctionnement livré |
|---|---|
| `/` | Landing Social Pop, CTA réels, modules futurs explicitement annoncés comme à venir |
| `/login` | E-mail/mot de passe, affichage du mot de passe, Google, erreurs françaises, redirection selon progression |
| `/register/1` | Identité et création du compte e-mail ; sans mot de passe pour une session Google |
| `/register/2` | Pays, gouvernorat, localité dépendante, rue, code postal |
| `/register/3` | Activités, loisirs, langues, nationalité, habitudes, religion facultative, biographie |
| `/register/4` | Coordonnées et disponibilités facultatives, formats contrôlés |
| `/register/5` | Aperçu et upload réel de photos privées, descriptions, suppression, finalisation |
| `/verify-email` | Explication de la confirmation, renvoi réel avec délai d’interface et protections serveur |
| `/auth/callback` | Retour confirmation/récupération/OAuth, nettoyage des paramètres sensibles de l’URL |
| `/forgot-password` | Demande réelle, retour neutre ne révélant pas l’existence d’un compte |
| `/reset-password` | Accessible après contexte de récupération validé, confirmation et sauvegarde du mot de passe |
| `/profile` | Identité, adresse, informations personnelles, contacts, préférences, photos privées et progression réels |
| `/admin` | Autorisation serveur, filtres et pagination, contacts, blocage/déblocage, suppression avec confirmation et motif |
| autres | Page introuvable et retour à l’accueil |

Le design reprend les compositions, variables, cartes, panneaux, illustrations CSS,
logo et palette des **12 maquettes Web** de `maquette/FastColoc_Maquettes_Sprint1/web/`.
Les annonces fictives et statistiques inventées des maquettes ne sont pas livrées.
Les polices Baloo 2 et DM Sans sont locales, en WOFF2, avec licences OFL conservées.
Poids total du dossier polices/licences : environ 250 Ko, contre 1,9 Mo initialement.

Composants standalone, routes lazy, formulaires réactifs, DTO TypeScript stricts,
services API/session, interceptor, guards et composants partagés. Les changements
d’état Auth utilisent des signals ; la configuration Zone Angular prend en charge
les formulaires et continuations asynchrones. Source formatée avec Prettier.

## API intégrées

Préfixe commun : `/api/v1`. Sources : OpenAPI réellement lu depuis
`http://localhost:8001/v3/api-docs`, contrôleurs, `StepRequests`, `AuthRequests`,
`AdminRequests`, `ProfileResponse` et `ProfileDataService`. Le contrat JSON de détail
comporte des champs SQL en snake_case, contrairement aux requêtes d’étapes en camelCase.

| Groupe | Appels du client |
|---|---|
| Auth | `POST /auth/signup`, `/login`, `/refresh`, `/exchange`, `/verify`, `/forgot-password`, `/resend-confirmation`, `/logout` ; `GET /auth/session`, `/google` ; `PUT /auth/password` |
| Profil | `POST /users/sync`, `GET /users/me/details`, `GET /users/me` après sauvegarde préconfirmation, `PUT /users/me/preferences` |
| Inscription | `PUT /onboarding/steps/1` à `/5`, `POST /onboarding/complete` |
| Géographie | `GET /geography/countries`, `/governorates?countryCode=...`, `/localities?governorateId=...` |
| Photos | `POST /profile-photos` multipart, `PATCH`/`DELETE /profile-photos/{id}`, `GET /profile-photos/{id}/signed-url` |
| ADMIN | `GET /admin/access`, `/admin/users?limit=20&offset=...&search=...&status=...`, `PATCH /admin/users/{id}/status`, `/contacts`, `DELETE /admin/users/{id}` avec motif |

Les mutations d’identité, adresse, informations personnelles et contacts du profil
réutilisent les routes d’étapes validées. Les requêtes ont un délai maximal de 45 s.
Le proxy de développement `/api` pointe sur Spring à `localhost:8001`. CORS existant
conservé, sans origine universelle ou assouplissement RLS.

## Inscription en cinq étapes et confirmation

1. Sans session, l’étape 1 valide identité et mot de passe confirmé, puis appelle
   signup. La réponse générique 202 signifie « demande acceptée », pas « e-mail reçu ».
2. Les informations d’identité préconfirmation restent uniquement en mémoire et sont
   associées à l’e-mail saisi. Elles ne peuvent pas écraser le profil d’un autre
   compte lors d’une connexion ultérieure. Aucun mot de passe n’est sauvegardé.
3. La confirmation Supabase reste obligatoire. Les étapes métier et référentiels
   protégés deviennent accessibles après authentification et synchronisation du profil.
4. Chaque étape est sauvegardée sur le serveur avant de passer à la suivante. Le
   retour arrière garde les saisies en mémoire ; un rechargement restaure les données
   enregistrées via `/users/me/details` et ses `validatedSteps`.
5. Les informations personnelles sensibles restent facultatives ; tabac non renseigné
   est distinct de `false`. Les coordonnées facultatives ne sont pas rendues obligatoires
   artificiellement. Seules les règles réellement imposées par les DTO sont utilisées.
6. Les photos sont contrôlées côté client puis décodées et vérifiées par le serveur :
   JPEG/PNG/WebP, 5 MiB, cinq photos maximum. L’aperçu local est libéré après usage.
7. La finalisation appelle d’abord l’étape 5 avec les UUID des photos propres, puis
   la complétion transactionnelle serveur. La suppression d’une photo invalide l’étape
   5 et la complétion : il faut les revalider.

Un compte créé, une adresse confirmée, cinq étapes validées et un `completedAt`
sont des états distincts. Avant fermeture, les champs non enregistrés ne constituent
pas un brouillon persistant. Après fermeture, la reprise concerne les données déjà
sauvegardées ; les champs préconfirmation peuvent devoir être ressaisis.

Le référentiel contient de nombreux pays mais seulement des gouvernorats/localités
tunisiens. Le client signale cette limite ; il n’invente pas de listes pour valider
une adresse hors du référentiel. Attribution GeoNames CC BY 4.0 affichée.

## Sessions, callbacks et sécurité

- Un seul gestionnaire de session, avec les routes Auth du backend, sans SDK parallèle.
- Access/refresh tokens dans `sessionStorage`, limités à l’onglet. Pas de mot de passe
  dans le stockage, les brouillons, les logs ou les configurations frontend.
- Vérification au démarrage par Auth, synchronisation du profil confirmé, rafraîchissement
  préventif avant expiration, refresh concurrents sérialisés, un seul retry sur 401.
- Une expiration/refus du refresh purge la session et renvoie à la connexion. BLOCKED
  est imposé par le serveur ; le client purge également ses données de session.
- Logout réel via Auth, purge locale même si le réseau échoue, message explicite si
  la révocation distante n’a pas pu être confirmée. Un JWT d’accès n’est pas déclaré
  instantanément révoqué : sa validité cryptographique peut durer jusqu’à expiration.
- Bearer ajouté uniquement à l’API configurée, pas aux liens externes ni aux photos
  signées. Les URL privées sont renouvelées toutes les 45 s pour une durée serveur de 60 s.
- Google : verifier aléatoire et challenge S256, état applicatif aléatoire dans
  `redirect_to`, comparaison au callback avant échange. Le state fournisseur reste
  géré par Supabase. Le verifier est conservé dans l’onglet d’origine.
- Confirmation/récupération e-mail : lien Supabase classique (fragment Auth après
  vérification), ou `token_hash` vérifié par `/auth/verify`. L’absence de PKCE imposé
  pour les e-mails permet l’ouverture sur un autre appareil. Les anciennes réponses
  PKCE avec verifier local restent comprises par le callback.
- Les tokens reçus sont contrôlés par `/auth/session`. Le contexte recovery n’est
  établi qu’après vérification du lien/session. Le reset direct sans contexte est refusé.
  Ce contexte d’interface est volontairement éphémère : recharger la page reset exige
  un nouveau lien ; le serveur reste l’autorité sur le changement de mot de passe.
- Query/hash sont retirés de l’URL avant les appels suivants ; politique referrer
  `no-referrer`. Pas de JWT imprimé par le client. Rôle ADMIN issu de l’API, jamais
  d’un claim éditable par l’utilisateur.
- Dialogues ADMIN natifs accessibles au clavier, motif obligatoire et confirmation
  explicite. Les actions blocage/suppression de son propre compte sont désactivées.
- La liste ADMIN n’expose ni e-mail ni contacts actuels. Le dialogue de correction
  préremplit les six contacts via `GET /api/v1/admin/users/{id}/contacts`, réservé à
  un ADMIN actif. Le chargement doit réussir avant toute modification ou sauvegarde.
- Labels, erreurs françaises, focus, menu mobile, lien d’évitement, reduced-motion,
  loaders, états vides et notifications. Identifiants DOM contrôlés par Playwright.

Le stockage accessible à JavaScript n’offre pas une protection contre une compromission
XSS du navigateur. Un BFF avec cookies HttpOnly nécessiterait un autre contrat ; aucun
second système de session n’a été introduit dans ce sprint.

## Tests et preuves

| Vérification | Résultat obtenu |
|---|---|
| `mvn.cmd -B -ntp verify` | **46 tests, 0 échec, 0 erreur, 0 ignoré ; BUILD SUCCESS** |
| Démarrage du JAR produit | Stop/start par scripts identifiés, backend réel UP sur 8001 |
| `npm.cmd test` | **14 tests unitaires**, validations UTF-8, listes, mots de passe, sessions, refresh, guards, state, préservation des profils existants |
| `npm.cmd run typecheck` | TypeScript strict réussi |
| `npm.cmd run build` | Build production réussi, sans avertissement ; environ 340 Ko initiaux avant compression |
| Playwright contre les services réels | **6 scénarios réussis**, y compris le lien Supabase réel, sa redirection vers Angular et le callback de récupération |
| Responsive | Largeurs 390, 768 et 1440 ; landing, inscription, profil, administration, labels et IDs |
| Scan ciblé secrets | **59 fichiers source/build**, quatre valeurs sensibles configurées recherchées, aucun résultat |
| `npm audit --omit=dev` | **0 vulnérabilité** des dépendances d’exécution |
| Audit avec outils de développement | 16 alertes modérées transitives de la CLI ; pas de downgrade majeur forcé |

Contrôle SQL final en lecture seule via l’intégration FastColoc : **0 compte Auth
portant le marqueur de recette Web restant, 1 compte ADMIN actif**. Le backend a été
laissé actif après la recette ; le serveur Angular de vérification est arrêté en
fin d’intervention et se relance avec `npm.cmd start`.

Les scénarios Playwright couvrent navigation/validation avant confirmation, connexion
e-mail réelle, cinq sauvegardes d’étapes, reprise, complétion, upload/lecture privés,
modification d’identité et préférences, USER refusé côté route et API ADMIN, refresh
réel, refresh invalide, lien invalide, récupération réelle et nouveau mot de passe,
logout, ADMIN autorisé, annulation de confirmation, correction des contacts, blocage
et refus d’accès, déblocage, suppression de la seule fixture, Google jusqu’au fournisseur.

La dernière suite Playwright intégrale dure environ **2 min 08 s**, avec six succès,
aucun scénario ignoré, inattendu ou déclaré flaky. Le contrôle supplémentaire de
préservation d’une identité déjà validée a ensuite été validé par le test unitaire
dédié, puis par une nouvelle compilation et le contrôle TypeScript strict.

Les tests unitaires emploient les doubles HTTP Angular pour isoler les contrats de
session ; les parcours utilisateurs livrés et la suite Playwright n’emploient pas de mocks.
La fixture Playwright est créée par l’API Auth Admin avec un e-mail de test, déjà
confirmée pour automatiser les étapes privées. Cette préparation n’est pas présentée
comme un clic humain dans une boîte mail. Les fixtures et photos sont nettoyées même
en cas d’échec. Les traces réseau et vidéos sont désactivées.

Preuves locales ignorées :

- `backend/target/web-integration-verify.log`, `surefire-reports/` ;
- `backend/target/server.stdout.log`, `server.stderr.log` ;
- `frontend/test-results/results.json`, captures dans `test-results/screenshots/` ;
- le bundle `frontend/dist/fastcoloc-web/browser/`.

Les 53 contrôles distants et Newman de la mission backend historique ne sont pas
déclarés réexécutés. La nouvelle preuve distante est la recette navigateur ci-dessus.

## Problèmes corrigés pendant l’intégration

1. Frontend absent : création du projet Angular, contrats et parcours réels.
2. Labels de champs inopérants à cause d’identifiants DOM dupliqués sur les composants
   hôtes : remplacement par un paramètre `fieldId` distinct et contrôle navigateur.
3. Rendu des continuations asynchrones : déclaration de Zone dans les polyfills du
   builder Angular, avec configuration de détection cohérente ; serveur redémarré.
4. Brouillon préconfirmation : association explicite à l’e-mail et refus d’écraser une
   identité déjà validée, pour préserver les profils existants et les changements de compte.
5. Expiration en cours d’action : redirection également sur refus de refresh, sans boucle.
6. Course dans le test de photo : attente de la fin réelle du téléchargement/décodage.
7. Ressources des maquettes : polices hébergées localement, compression WOFF2, faux
   liens et données de démonstration remplacés par des parcours ou états honnêtes.

## Vérifications humaines et configuration distante restantes

**Google** : ouvrir `/login`, cliquer « Continuer avec Google », choisir un compte,
accepter le consentement, vérifier le retour `/auth/callback`, puis la reprise des
étapes ou le profil. Tester un compte existant et un nouveau compte Google. Ne pas
créer de mot de passe local artificiel. Activation provider et redirection réelle vers
`accounts.google.com` vérifiées ; consentement et callback interactifs non attestés.

**E-mail humain** : l’envoi/réception/confirmation du compte initial ont été vérifiés
pendant la mission backend. Ils n’ont pas été redemandés ici. La nouvelle UI signup
et son retour dans une vraie boîte mail restent à recetter avec une adresse contrôlée,
en suivant `/register/1` → `/verify-email` → confirmation → connexion → cinq étapes.
La récupération automatique utilise un lien Auth réel généré pour la fixture, sans
attester sa réception dans une boîte mail.

**Templates/URLs Auth** : aucun Management API token disponible, aucune installation
distante déclarée. Procédure manuelle, uniquement dans le projet FastColoc
`nklsbubnyznlfpxpxsrp` :

1. Authentication → URL Configuration : vérifier Site URL `http://localhost:4200`
   et Redirect URLs pour `http://localhost:4200/auth/callback`, ainsi que le callback
   contenant le paramètre `state` utilisé par Google. Le callback côté backend est
   déjà configuré vers cette route.
2. Authentication → Email Templates → Confirm signup : comparer avec
   `backend/config/email-templates/confirmation.html`. Si le template FastColoc n’est
   pas déjà installé, copier ce fichier en conservant `{{ .ConfirmationURL }}`.
3. Même opération pour Reset Password avec `backend/config/email-templates/recovery.html`.
   Préserver les personnalisations déjà installées ; enregistrer puis vérifier un lien réel.
4. Le script `backend/tools/configure-auth.cjs` est une alternative opérateur si un
   Management API token est fourni localement. Ne pas confondre ce token avec la clé Auth serveur.

SMTP dédié et protection contre les mots de passe compromis restent des sujets de
configuration production. Aucun changement de sécurité distant n’a été appliqué ici.

Références officielles consultées :
[PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow),
[flux e-mail implicite](https://supabase.com/docs/guides/auth/sessions/implicit-flow),
[redirections](https://supabase.com/docs/guides/auth/redirect-urls),
[compatibilité Angular](https://angular.dev/reference/versions).

## Commandes et URLs

Depuis la racine FastColoc, premier terminal :

```powershell
cd backend
mvn.cmd -B -ntp verify
.\tools\start-backend.ps1 -Background
```

Si le backend est déjà actif, ne pas lancer une seconde instance. Pour un redémarrage
contrôlé : `.\tools\stop-backend.ps1` puis `.\tools\start-backend.ps1 -Background`.

Second terminal, depuis la racine :

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

- Web : **http://localhost:4200**
- Connexion : **http://localhost:4200/login**
- Inscription : **http://localhost:4200/register/1**
- Profil : **http://localhost:4200/profile**
- Administration : **http://localhost:4200/admin**
- API : **http://localhost:8001/api/v1/health**
- Swagger : **http://localhost:8001/swagger-ui/index.html**

Le build production requiert un hébergement SPA avec fallback vers `index.html` et
un proxy `/api` vers Spring ; `ng serve` ne constitue pas le déploiement production.
Aucun déploiement public n’a été effectué. Aucun changement d’Immovia ou d’Android.

### Correctif du dialogue des contacts — 17 novembre 2026

En-tête et boutons toujours visibles, fermeture explicite, défilement limité au contenu,
arrière-plan non défilable et champs sur deux colonnes (une sur petit écran). Le message
technique sur l’API a été remplacé par une aide adaptée au formulaire prérempli.

Migration autorisée `20261117000621_admin_contact_read.sql`, appliquée à FastColoc sous
la version distante `20261117001002` : une fonction privée de lecture de six champs,
contrôle `require_admin()`, exécution backend uniquement ; aucune table, politique RLS,
donnée réelle ni permission client modifiée. Empreinte des contacts identique avant
migration et après nettoyage du test.

Vérifications : compilation/package backend et build Angular réussis ; un test JUnit ciblé
`adminContactsPrefillRequiresAdminAndPreservesOtherValues` réussi ; un parcours Playwright
`e2e/admin-contacts.spec.ts` réussi (préremplissage réel, panne de chargement et reprise,
sauvegarde du seul mobile corrigé avec autres contacts préservés, fermeture et affichage
1440/390 px). Une seule fixture utilisateur, nettoyée ; aucune correction sur un compte réel.
