# FastColoc — Sprint 2 : annonces et recherche Web / Android

Dates de recette : 16 et 17 novembre 2026. Sources fonctionnelles et arbitrages :
[périmètre vérifié](15-SPRINT2-PERIMETRE.md). Inventaire des cours, lecture des PDF
et conventions : [références Android](16-SPRINT2-REFERENCES-ANDROID.md).

## Résultat fonctionnel

Un compte confirmé, ACTIVE et ayant terminé son inscription peut créer une offre
ou une demande, enregistrer un brouillon, ajouter des photos, publier, modifier,
renouveler et supprimer sa propre annonce. Les annonces publiées non expirées
apparaissent dans le catalogue commun. Les utilisateurs connectés accèdent aux
fiches et à leurs favoris. Aucun rôle supplémentaire n'a été créé.

Les titres, descriptions, localités, coordonnées, loyers en TND, capacités, types
de logement/colocataire, équipements et disponibilités proviennent de Spring Boot.
La durée est choisie par annonce ; la borne serveur se configure avec
`fastcoloc.listings.max-publication-days` (365 au maximum technique PostgreSQL).
Le renouvellement actualise `published_at` et `expires_at`, donc le tri récent.
Une édition ordinaire conserve l'échéance existante.

Les écrans distinguent chargement, indisponibilité, résultats vides, brouillon et
copie locale. Les listes réelles restent vides après nettoyage des fixtures :
aucun catalogue fictif n'est embarqué. Les fixtures de recette portent explicitement
`[TEST TEMPORAIRE]`, utilisent leurs propres comptes et sont supprimées après test.

## PostgreSQL et protection des données

Migration locale approuvée explicitement par l'utilisateur :
`backend/supabase/migrations/20261116215125_sprint2_listings.sql`.

- SHA-256 du contenu appliqué :
  `496C283DF287377962BE888F44D4A7A19E1F1EAB90B8A47A097E5CAF910F5505`.
- Entrée distante créée par le MCP : `20261116220816 / sprint2_listings`.
  Le timestamp distant est celui du serveur d'application de la migration.
- Les quatre entrées historiques sont restées présentes sans rejeu.
- Les 14 tables du Sprint 1 ont conservé exactement leurs comptes de lignes et
  leurs empreintes de contenu immédiatement avant/après cette migration.
- Aucun reset, aucune intervention sur Immovia, aucune suppression de données
  réelles et aucune ancienne migration exécutée à distance.

| Nouvelle table privée | Relations / contraintes | Index et RLS |
|---|---|---|
| `fastcoloc.listings` | Auteur → profiles ; localité → localities ; OFFER/REQUEST ; DRAFT/PUBLISHED ; loyer/capacité/coordonnées/dates contraints ; version d'édition | Auteur/date, localité, publication, type/loyer, coordonnées, GIN équipements ; lecture publiée non expirée ou auteur ; écritures réservées à l'auteur ACTIVE ; création avec profil complet |
| `fastcoloc.listing_photos` | Annonce → listings en cascade ; chemin unique ; MIME/taille/rang contrôlés | Unique annonce/rang ; lecture selon visibilité de l'annonce ; upload/suppression selon auteur et préfixe UUID |
| `fastcoloc.listing_favorites` | Compte → profiles ; annonce → listings ; clé composée empêchant les doublons | Index inverse annonce ; lecture/écriture du compte courant ; ajout uniquement à une annonce publiée visible |

Les trois tables ont RLS activée. Le rôle `fastcoloc_backend` reste non propriétaire,
sans SUPERUSER, BYPASSRLS, CREATEROLE, CREATEDB ou héritage. Aucun droit SQL direct
n'est accordé à `anon`, `authenticated` ou PUBLIC. Les colonnes d'auteur, ID et dates
de création ne sont pas modifiables par le rôle runtime sur `listings`.

Deux politiques SELECT additionnelles, uniquement pour le backend, rendent lisibles
les noms de régions et localités de référence pour l'accueil public. Les politiques
Sprint 1 existantes restent présentes. Aucun profil, contact ou photo de profil
n'est ouvert par ces politiques. La suspension du compte continue d'être vérifiée
par le filtre Spring sur toutes les opérations authentifiées.

Le bucket `listing-photos` est privé : JPEG/PNG/WebP, 5 MiB maximum, dix photos par
annonce par défaut. Spring vérifie signature, décodage et dimensions avant upload.
Les objets suivent `auteurUUID/annonceUUID/photoUUID.extension`. Les URL sont signées
pour 300 secondes et créées par lots ; aucune clé serveur ne quitte le backend.
L'upload compense un rollback SQL. Les suppressions Storage précèdent la suppression
de métadonnées et peuvent être retentées en cas d'échec.

L'ancien parcours de suppression de compte Web nettoie maintenant aussi les photos
d'annonces du compte, avant la cascade Auth/PostgreSQL. Il garde son contrôle ADMIN,
son blocage préalable, son audit et son interdiction de suppression de soi-même.
Ce nettoyage ne crée pas une administration d'annonces du Sprint 3.

L'advisor Supabase ne relève aucun défaut propre aux trois nouvelles tables.
Restent deux observations antérieures : journal d'audit Sprint 1 sans politique
de lecture (accès refusé par défaut, fonctions contrôlées) et protection des mots
de passe compromis non activée dans Auth. Ces réglages n'ont pas été modifiés.

## Contrat REST

Base : `/api/v1`. Contrôleur/service : `backend/src/main/java/tn/fastcoloc/listings`.
DTO entrants : `ListingWrite` et `ListingQuery`, Bean Validation et validations
croisées côté service. Les champs d'auteur/statut/dates serveur sont rejetés s'ils
sont injectés dans le DTO. L'édition exige `version` et renvoie 409 si elle est périmée.

| Méthode | Chemin | Accès / résultat |
|---|---|---|
| GET | `/listings` | Catalogue public des publications non expirées ; `{items,total,page,size}` |
| GET | `/listings/areas` | Régions/localités tunisiennes de référence, public |
| GET | `/listings/map` | Positions paginées de toutes les offres filtrées ; compte connecté ; sans chargement des photos |
| GET | `/listings/mine` | Compte courant ; inclut ses brouillons et annonces expirées |
| GET | `/listings/favorites` | Favoris du compte courant dont les annonces sont visibles |
| GET | `/listings/{id}` | Compte connecté ; fiche, photos signées et état favori |
| POST | `/listings` | Profil complet ; crée son brouillon, HTTP 201 |
| PUT | `/listings/{id}` | Auteur seulement, version obligatoire |
| POST | `/listings/{id}/publish` | Auteur avec profil complet ; publication du brouillon |
| POST | `/listings/{id}/renew` | Auteur avec profil complet ; renouvelle une publication |
| DELETE | `/listings/{id}` | Auteur seulement ; supprime photos, annonce et favoris liés |
| PUT / DELETE | `/listings/{id}/favorite` | Ajout idempotent / retrait du favori du compte |
| POST | `/listings/{id}/photos` | Multipart `file`, `caption` ; auteur seulement |
| DELETE | `/listings/{id}/photos/{photo}` | Auteur seulement |

Filtres combinables : `q`, `kind`, `governorateId`, `localityId`, `minRent`, `maxRent`,
`minCapacity`, `propertyType`, `roommateType`, `equipment`, `availableOn` et rectangle
`south/west/north/east`. Tri : NEWEST, RENT_ASC ou RENT_DESC, avec UUID comme second
critère stable. Pages à partir de zéro, taille 1–50, borne de page 1000 ; l'interface
emploie 12 annonces. SQL paramétré, tri sur liste autorisée et budget cohérent contrôlé.

Swagger est exposé à `http://localhost:8001/swagger-ui/index.html` ; titre Sprint 1/2,
schémas des DTO et exigences Bearer sur les opérations protégées. Export vérifié :
`backend/target/openapi-sprint2.json`. La collection
`backend/postman/FastColoc-Sprint2.postman_collection.json` conserve les dossiers
Sprint 1 et ajoute le dossier `Sprint 2 — Annonces` sans identifiant secret embarqué.

## Interfaces et matrice Web / Android

| Fonction | Angular | Android natif | Référence |
|---|---|---|---|
| Nouvelles annonces / catalogue | Accueil et `/annonces` | Catalogue après connexion | US13, US33 |
| Offre et demande par étapes | `/annonces/nouvelle` | ListingEditorFragment, trois étapes XML | US08–10, US33 |
| Fiche et photos | `/annonces/:id` | ListingDetailFragment et grille de photos | US14, US33 |
| Modification personnelle | `/annonces/:id/modifier` | Même éditeur, version serveur | US11, US33 |
| Annonces personnelles / suppression | `/mes-annonces` | Menu « Mes annonces », fiche auteur | US11–12, US33 |
| Publication et renouvellement | Liste personnelle / éditeur | Fiche / éditeur | US10–12, US33 |
| Recherche multicritère / pagination / tri | Catalogue, filtres repliables | SearchView, dialogue de filtres, précédent/suivant | US15, US34 |
| Favoris communs | `/favoris` | Menu « Mes favoris », boutons cœur | US34 + répartition DOCX |
| Smart-Map | Leaflet, sélection de zone visible, marqueurs et liens de fiches, centrage des résultats | Reportée selon planning | US16–19 ; carte Android US35/S3 |
| Cache local | Pas de catalogue indépendant | Room, pages/fiches isolées par compte | US34 |
| Administration, IA, messagerie, boost, swipe | Aucune nouveauté Sprint 3 | Aucun de ces modules ajouté | Planning Sprint 0 |

Angular réutilise shell, navigation, Api/Auth/intercepteur, notifications, variables
de marque et polices locales. Les nouveaux composants sont dans
`frontend/src/app/features/listings`. La carte emploie Leaflet 1.9.4 et les tuiles
OpenStreetMap avec attribution ; les info-bulles utilisent des éléments DOM et
`textContent`, sans interpolation HTML de contenu utilisateur.

La carte charge les positions par pages dédiées de 50, indépendamment des douze
cartes du catalogue : chaque offre correspondant aux filtres porte son marqueur ;
les demandes n'affichent pas de logement fictif. Le rectangle
visible permet d'affiner la recherche. Le choix région/localité recentre les
résultats disponibles ; aucun service de géocodage externe n'est ajouté.

Les maquettes présentes concernent uniquement Sprint 1. Les nouveaux écrans gardent
violet/corail/rose/crème/prune, Baloo 2 et DM Sans et les composants existants.
Les contrôles responsive ont été exécutés à 390, 768 et 1440 px.

## Matrice de conformité pédagogique Android

Chemins ci-dessous relatifs à `mobile/app/src/main/`, sauf tests explicités.
Les preuves de recette sont détaillées dans la section suivante.

| Notion | Chapitre / TP | Fichiers Kotlin / XML | Utilisation réelle et preuve |
|---|---|---|---|
| Kotlin, data classes, null safety, lambdas | Ch.1 p.19–45 ; TP7 p.2 | `data/remote/ListingContracts.kt`, `data/ListingDraft.kt` | UUID serveur, DTO annotés Gson, dates facultatives ; trois tests JVM de validation |
| XML, ressources et ViewBinding | Ch.2 p.5–11, 45–48 | `res/layout/fragment_listing_*.xml`, `item_listing*.xml`, `res/values/strings_sprint2*.xml` | Layouts compilés, bindings générés et formulaires remplis par la recette native |
| Fragments et libération des vues | Ch.5 p.7–8 | `ui/listings/ListingBoundFragment.kt`, trois Fragments | Héritage du BoundFragment Sprint 1, détachement des adapters ; passage entre liste/fiche/éditeur exécuté |
| Navigation Component / pile | Ch.5 p.10–16 ; TP6 p.6 | `res/navigation/nav_auth.xml`, `ListingBoundFragment.kt` | NavHost existant, ID d'annonce en argument, retour liste après suppression testé |
| MVVM / Repository | Ch.5 p.4–5 ; Ch.6 p.20–24 ; TP7 exercice p.6 | `viewmodel/ListingViewModel.kt`, `data/ListingRepository.kt` | Aucun appel Retrofit/Room dans les Fragments ; mutations réelles Spring puis actualisation |
| ViewModel partagé / Factory | Ch.5 p.18–31 ; Ch.6 p.25–30 ; TP6 p.4–5 | `ListingViewModelFactory`, `ListingBoundFragment.kt`, `data/AppContainer.kt` | activityViewModels + injection depuis singleton d'application ; recréation Activity et conservation du formulaire testées |
| LiveData / observation de cycle de vie | Ch.5 p.19, 27–31 ; TP6 p.8–9 | `ListingViewModel.kt`, `ListingCache.kt`, Fragments | LiveData Room via switchMap, observation viewLifecycleOwner ; liste issue du cache observée sur émulateur |
| Entity / DAO / Database Room | Ch.6 p.11–18 ; TP6 p.1–4 | `data/local/ListingCache.kt` | Deux entités de snapshots serveur, clés compte/recherche et compte/annonce ; vraie copie relue après IOException et effacée au logout |
| Coroutines, IO, suspend | Ch.6 p.15–16, 23 ; Ch.7 p.22–29 ; TP7 p.3–4 | ViewModel, Repository, DAO, FastColocApi | Travail réseau/fichier sur IO, postValue ; aucun enregistrement local lors d'un POST hors ligne (test exécuté) |
| Retrofit / OkHttp / Gson | Ch.7 p.6–18, 25 ; TP7 p.1–2 | `data/remote/FastColocApi.kt`, `ListingContracts.kt`, ApiClient existant | GET/POST/PUT/DELETE multipart et query map ; données créées Web relues Android puis inversement |
| RecyclerView / Adapter / ViewHolder | Ch.4 p.4–23, 28–35 | `ui/listings/ListingAdapter.kt`, `ListingPhotoAdapter.kt`, `item_listing*.xml` | ListAdapter/DiffUtil, bindings et callbacks ; vraie ligne et vraie Bitmap chargée depuis Storage vérifiées |
| Linear / GridLayoutManager | Ch.4 p.13, 22–25 | Catalogue, fiche et éditeur | Liste téléphone, grille tablette, grille photos ; LinearLayoutManager constaté à l'exécution sur téléphone virtuel |
| Menus / SearchView | Ch.4 p.36–41 ; TP6 p.8 | `res/menu/menu_listings.xml`, `ListingCatalogFragment.kt`, AuthActivity | MenuProvider attaché à RESUMED, recherche et rubriques personnelles ; code vérifié et menu compilé dans l'écran exécuté |
| Événements / dialogues / galerie | Ch.3 p.6–9, 40–41, 48–50 | Fragments, PhotoAdapter, éditeur | setOnClickListener, AlertDialog, ActivityResult GetContent ; upload d'une image MediaStore par le ViewModel testé |
| Cycle de vie / rotation | Ch.3 p.19–26 ; Ch.5 p.25–27 | Éditeur, ViewModel, BoundFragment | `ActivityScenario.recreate()` exécuté ; titre restauré et édition ultérieure acceptée par version serveur |

Le menu et le dialogue de filtres sont aussi revus dans le code ; la recette
instrumentée transmet ses critères au ViewModel, sans prétendre avoir automatisé
chaque interaction du menu ou le sélecteur de fichiers système. La tablette utilise
le chemin GridLayoutManager, compilé mais non exécuté sur une tablette physique.

## Tests réellement exécutés

| Commande / recette | Résultat | Preuve locale |
|---|---|---|
| `mvn.cmd -B -ntp verify` | 57 tests, 0 échec, 0 erreur, 0 ignoré | `backend/target/sprint2-final-verify.log`, rapports Surefire |
| Angular `npm test` | 14 tests réussis | `frontend/sprint2-unit.log` |
| Angular typecheck + production build | Réussis | `frontend/sprint2-final-typecheck.log`, `sprint2-final-build.log` |
| `npx playwright test` | 7 parcours réussis, dont les 6 régressions Sprint 1 | `frontend/sprint2-all-e2e.log` |
| Playwright Sprint 2 après extension finale de la carte | 1 parcours réexécuté avec succès | `frontend/sprint2-map-e2e.log`, `test-results/results.json` |
| Newman, dossier Sprint 2 final | 17 requêtes, 20 assertions, 0 échec | `backend/target/sprint2-postman-report.json` |
| Gradle assembleDebug + testDebugUnitTest + assembleDebugAndroidTest + lintDebug | Compilation réussie, 10 tests JVM, lint sans erreur | `mobile/build/sprint2-verification.log`, résultats JUnit / lint |
| Recette native Sprint 2 sur `emulator-5554` | 1 scénario intégré réussi (124,562 s) | `mobile/build/sprint2-device-recipe.log` |
| Régression native Sprint 1 sur le même émulateur | 5 tests réussis (193,342 s) | `mobile/build/sprint2-sprint1-device-regression.log` |

Le dernier APK de livraison a été recompilé après mise en forme des sources Kotlin
et XML (`mobile/build/sprint2-delivery-build.log`) : dix tests JVM réussis et lint
sans erreur, avec 125 avertissements. Le scan des secrets configurés n'en a retrouvé
aucun dans 1 233 entrées Android/source/APK ni dans 90 entrées Angular/source/build.
Le contrat Swagger final a été vérifié : quinze opérations d'annonces, deux publiques
et treize protégées par Bearer.

L'APK a été installé avec succès sur le Samsung SM-F946B USB par `adb install -r`,
sans effacer les données de l'application, et son Activity a été lancée. Les six
tests instrumentés complets ont été exécutés sur l'émulateur ; une recette humaine
complète sur le Samsung n'est pas revendiquée pour ce sprint.

Les sept tests backend ajoutés utilisent un vrai PostgreSQL local avec le rôle
non privilégié ; seuls Auth/Storage externes y sont simulés. Ils couvrent offres et
demandes, brouillons, publication/expiration/renouvellement, critères combinés,
pagination/tri, droits d'auteur, version périmée, favoris isolés et idempotents,
photos privées, positions de treize offres sur plusieurs pages du catalogue,
absence de grants clients et nettoyage du nouveau bucket lors de
la suppression d'un compte. Les parcours Playwright/Postman/natifs utilisent le
vrai backend et le projet Supabase FastColoc avec des fixtures isolées.

### Vérifications croisées réellement observées

`backend/target/sprint2-cross-report.json` confirme :

1. Publication et photo depuis le formulaire Angular.
2. Lecture du même UUID, du loyer et de la vraie image dans Android via Retrofit/Coil.
3. Favori et retrait Android visibles sur la page Angular des favoris du même compte.
4. Création/publication Android et photo visibles sur Angular.
5. Édition Web de cette annonce relue sur Android après actualisation.
6. Édition Android relue dans Angular, puis suppression partagée.
7. Nettoyage des deux comptes temporaires de cette recette.

Le test natif vérifie également : refus HTTP 404 de suppression par un autre auteur,
absence de bouton d'édition pour celui-ci, relecture réelle du cache après refus de
connexion loopback, absence de création autonome hors ligne et effacement au logout.

La validation Google humaine du Sprint 1 n'a pas été refaite dans ce sprint. Les
tests existants de state/PKCE, redirection fournisseur et gestion des retours ont
été réexécutés avec succès ; aucun nouveau résultat de consentement humain n'est
inventé.

## Problèmes corrigés pendant la recette

- Lecture des référentiels publics : ajout contrôlé des deux politiques SELECT.
- Filtres invalides : validation hors du constructeur de binding pour obtenir 400
  et non une exception de construction masquée en 500.
- Sélection de localité dans Playwright : ciblage sémantique du combobox.
- Carte/photos non mises en forme dans le serveur de développement qui conservait
  sa configuration CSS initiale : redémarrage Angular après ajout des styles, puis
  nouvelle recette complète et vérification responsive réussie.
- En-tête de fiche étroit : règles de retour à la ligne et largeur minimale des grilles.
- Tests croisés autonomes : délai d'attente adapté au bootstrap réel de session ;
  les premiers échecs de recette ne sont pas comptabilisés comme des succès.
- Réouverture de l'éditeur Android : nouveau brouillon d'interface au début d'une
  édition, conservation lors de la rotation et version serveur pour éviter un écrasement.
- Accès fichier Android : lecture bornée compatible avec minSdk 24, sans readNBytes
  dépendant d'une API Android plus récente.
- Suppression d'un compte existante : prise en charge des nouveaux objets Storage
  pour éviter de laisser des photos orphelines.
- Smart-Map indépendante de la pagination du catalogue : endpoint léger dédié et
  test PostgreSQL avec treize offres publiées, une demande et un brouillon exclu.

## Limites et choix de livraison

- La carte charge toutes les pages de positions correspondant aux filtres, dans
  la limite technique de pagination de l'API (page 1000). Les coordonnées sont
  contrôlées dans une enveloppe tunisienne, sans géocodage automatique de l'adresse.
- Hors ligne Android : uniquement les recherches déjà téléchargées et les fiches
  mises en cache (24 h maximum). Aucun changement ni favori n'est mis en file locale.
  Les images signées ne constituent pas une galerie hors ligne ; actualiser la fiche
  renouvelle leurs URL.
- Les suppressions Storage et PostgreSQL ne sont pas une transaction distribuée.
  Une suppression interrompue entre les deux peut nécessiter une nouvelle tentative.
- Le catalogue distant est sans données de démonstration persistantes. Il faut
  publier une vraie annonce depuis son compte complet pour le peupler.
- Carte Android, messagerie, swipe, IA, boost et modération d'annonces restent
  au Sprint 3 conformément au planning, et non à cause d'un écran inachevé caché.
- Leaflet 1.9.4 produit un avertissement CommonJS de build ; aucun blocage de compilation.
  Les tuiles OSM dépendent du réseau et de la politique du fournisseur : attribution
  visible, pas de préchargement ni de téléchargement hors ligne.

## Démarrage et reproduction

1. Backend : conserver `backend/.env.local` et son rôle runtime, Java 21 et Maven.
   Depuis `backend`, `mvn.cmd -B -ntp verify`, puis
   `powershell -File tools/start-backend.ps1 -Background`. Arrêter d'abord le processus
   FastColoc enregistré s'il tourne déjà. API sur 8001.
2. **Ne pas pousser/rejouer l'historique Supabase.** La migration approuvée est déjà
   appliquée au projet distant. Ne lancer ni reset ni les anciens fichiers SQL.
   Le script `node backend/tools/prepare-listing-storage.cjs` vérifie le bucket privé
   et ne modifie jamais les paramètres d'un bucket existant.
3. Angular : `cd frontend`, `npm ci`, `npm start` ; interface sur 4200, proxy vers 8001.
   `npm run build`, `npm test` et `npm run test:e2e` pour reproduire les contrôles.
4. Android émulateur : URL habituelle `http://10.0.2.2:8001/api/v1/`. Pour téléphone USB
   ou les scripts de recette, construire avec
   `-PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/`, puis `adb reverse tcp:8001 tcp:8001`.
   Depuis `mobile`, `gradlew.bat assembleDebug testDebugUnitTest lintDebug`.
   La configuration release reste HTTPS et ne doit pas employer le tunnel debug.
5. Recette croisée : `mobile/tools/test-sprint2-device.ps1` ; backend et Angular doivent
   déjà tourner. Le pont de fixtures écoute uniquement 127.0.0.1:8124, puis est fermé.
   `mobile/tools/test-sprint1-device.ps1` reproduit la régression native Sprint 1.
6. Postman : régénérer avec `node backend/tools/build-postman-sprint2.cjs`. Pour la
   recette Newman isolée, installer Newman 6.2.1 dans `backend/target/recipe-tools`
   puis lancer `node backend/tools/run-postman-sprint2.cjs`. Les tokens de fixtures
   restent en mémoire Node, jamais dans la collection partagée.

Références techniques consultées : [RLS Supabase](https://supabase.com/docs/guides/database/postgres/row-level-security),
[Storage et URL signées](https://supabase.com/docs/reference/javascript/storage-from-createsignedurls),
[politique des tuiles OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/).

## Correctif cartographique après signalement — 17 novembre 2026

Le fond de carte affichait des images « 403 Access blocked ». Cause identifiée :
la balise globale `meta[name=referrer]` du Sprint 1 utilise `no-referrer`, et les
tuiles Leaflet héritaient de cette politique, incompatible avec l'identification
du site exigée par OpenStreetMap.

`LocationPicker` et `ListingMap` utilisent désormais l'option Leaflet
`referrerPolicy: 'strict-origin'`. Le navigateur transmet uniquement l'origine
du site pour ces images, sans chemin ni paramètres. La politique globale des
pages d'authentification reste `no-referrer`. Aucun proxy de contournement,
changement de fournisseur, modification Supabase ou donnée métier n'est nécessaire.

Les tests initiaux vérifiaient marqueurs et info-bulles, sans contrôler les réponses
du fournisseur de tuiles. Le nouveau test `frontend/e2e/map-referrer.spec.ts` vérifie
les vrais en-têtes des requêtes, HTTP 200, le décodage de toutes les tuiles visibles,
la sélection des coordonnées dans le formulaire et le chargement du catalogue.
Il conserve le cache HTTP normal, sans balayage de zones ni préchargement.

Preuves : `frontend/map-referrer-e2e.log`,
`frontend/test-results/map-referrer-report.json` et
`frontend/test-results/screenshots/map-picker-referrer-fixed.png`.
Typecheck et compilation de production : `frontend/map-fix-typecheck.log` et
`frontend/map-fix-build.log`. Ce test ciblé s'ajoute aux sept parcours historiques ;
la suite complète de huit tests n'est pas revendiquée comme réexécutée ici.
