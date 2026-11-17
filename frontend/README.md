# FastColoc Web — Sprints 1 et 2

Nouveaux parcours : `/annonces`, `/annonces/nouvelle`, `/annonces/:id`,
`/annonces/:id/modifier`, `/mes-annonces` et `/favoris`. Catalogue public,
fiches connectées, offres/demandes, photos, filtres, tri et carte Leaflet.
Le catalogue affiche uniquement les données Spring Boot, avec un état vide réel.
Recette complète : **14 tests unitaires et 7 parcours Playwright**.
Voir [le rapport Sprint 2](../docs/17-SPRINT2-ANNONCES-RECHERCHE.md).

Angular **21.2.26**, CLI/build **21.2.29**, TypeScript **5.9.3**, composants standalone,
routes lazy, Reactive Forms, HttpClient. Les versions sont figées et le lockfile doit être conservé.
Node 22.19.0 / npm 11.6.0 utilisés pour la recette. Node compatible Angular 21 requis.

## Lancement local

Dans un premier terminal, depuis la racine FastColoc :

```powershell
cd backend
mvn.cmd -B -ntp verify
.\tools\start-backend.ps1 -Background
```

Dans un second terminal, depuis la racine FastColoc :

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Ouvrir **http://localhost:4200**. API : **http://localhost:8001** ;
Swagger : **http://localhost:8001/swagger-ui/index.html**.
Le proxy Angular transmet `/api` au backend. Aucun secret ni clé Supabase n’est requis par Angular.
Le backend charge sa configuration locale ignorée, `backend/.env.local` : ne pas l’écraser.

## Parcours

- `/` : accueil conforme à la direction Social Pop des maquettes.
- `/register/1` à `/register/5` : inscription, confirmation préalable, étapes sauvegardées.
- `/verify-email`, `/auth/callback` : confirmation et retour OAuth/PKCE/récupération.
- `/login`, `/forgot-password`, `/reset-password` : authentification et récupération.
- `/profile` : données privées, modifications, préférences et photos.
- `/admin` : accès contrôlé par l’API, liste paginée, blocage/déblocage, contacts, suppression confirmée.

Pour l’inscription e-mail, créer le compte à l’étape 1, confirmer l’adresse reçue puis
se connecter pour enregistrer les étapes métier. Les champs préconfirmation restent
en mémoire ; après fermeture, ils sont à ressaisir. Les étapes déjà sauvegardées côté
serveur sont restaurées après reconnexion. Un compte Google rejoint les mêmes étapes
sans mot de passe local. Au moins une photo privée est nécessaire pour finaliser.

L’API de géographie fournit tous les pays mais seulement des gouvernorats/localités
tunisiens. Une adresse hors du référentiel ne peut pas être validée par ce contrat.
Attribution : **GeoNames, CC BY 4.0**, provenance dans `../docs/GEOGRAPHY-SOURCE.json`.

## Tests

```powershell
npm.cmd test
npm.cmd run typecheck
npm.cmd run build
npm.cmd run test:e2e
node tools/check-secrets.mjs
```

Playwright utilise Chrome installé sous Windows s’il est présent ; sinon :

```powershell
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

On peut choisir un canal via `$env:FASTCOLOC_BROWSER_CHANNEL='msedge'`.
La recette E2E nécessite le backend réel et ses valeurs opérateur locales. Elle crée
uniquement une fixture Auth identifiée, la nettoie en fin de suite et emploie le
compte ADMIN configuré pour agir sur cette seule fixture. Elle ne change ni le rôle,
ni le mot de passe, ni le profil du compte ADMIN initial. Ne pas exécuter cette
recette avec une autre base ou un fichier de configuration provenant d’Immovia.

Les traces réseau et vidéos sont désactivées pour ne pas exporter de tokens ou de
mots de passe. Rapports et captures : `test-results/`, ignoré par Git. Ne pas publier
les configurations locales ou les artefacts d’une recette contenant des données personnelles.

## Sessions et configuration Auth

Un seul gestionnaire de session, sans SDK Supabase parallèle. Access/refresh tokens
dans `sessionStorage` (durée de l’onglet), refresh sérialisé et retry HTTP borné à un.
Le serveur contrôle les identités, la confirmation et le statut BLOCKED ; les guards
ne remplacent pas ces contrôles. Un JWT d’accès peut rester valide après logout jusqu’à
son expiration ; le logout révoque les refresh tokens via Auth.

Google nécessite le consentement humain pour terminer la recette. Ne pas modifier le
provider existant. Dans Supabase, vérifier les URLs de redirection de l’application,
dont `http://localhost:4200/auth/callback` et sa variante avec `state` applicatif.
Les templates FastColoc préparés sont sous `../backend/config/email-templates/` ;
leur installation distante n’est pas attestée. Procédure dans le rapport Web.

## Build et hébergement

Le build est produit sous `dist/fastcoloc-web/browser/`. Tout hébergement doit fournir
un fallback SPA vers `index.html`, HTTPS, un proxy `/api` vers Spring et les URLs
Auth/CORS correspondant à son domaine. Le proxy de `ng serve` est uniquement local.
Aucun hébergement public n’a été demandé ou effectué.

Les styles, compositions et logo proviennent de `../maquette/FastColoc_Maquettes_Sprint1/`.
Baloo 2 et DM Sans sont servis depuis les assets locaux, sans appel Google Fonts au runtime.
