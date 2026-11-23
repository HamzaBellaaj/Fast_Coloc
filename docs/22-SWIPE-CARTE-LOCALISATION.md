# Swipe Android, carte Android et localisation des annonces

Implémentation du 23 novembre 2026, à la suite de l'audit statique. L'audit d'origine reste un constat historique ; il n'a pas été réécrit.

## Parcours livrés

- Android, menu latéral **Découvrir** : une annonce active à la fois, photo/titre/ville/loyer ; geste droite ou bouton cœur = `PUT /listings/{id}/favorite`, gauche/croix = passer. Un appui ouvre la fiche existante. Les annonces du compte et les annonces indisponibles sont exclues. Les choix traités restent en mémoire dans le ViewModel de l'activité ; une erreur réseau conserve la carte pour une reprise. Une actualisation recharge le catalogue en conservant les identifiants déjà traités. Pas de matching ni de modification des recommandations IA.
- Android, menu **Carte des annonces**, ou menu du catalogue : carte native osmdroid/OpenStreetMap, sans clé. Le catalogue transmet ses filtres à `/listings/map`, paginé jusqu'au dernier résultat. Les offres sans coordonnées valides sont ignorées. Le marqueur montre titre/ville/loyer et propose la fiche existante. Les résultats définissent le cadrage ; une zone vide est recherchée par son nom si possible, sinon la Tunisie est affichée. Les erreurs API et l'absence de résultats ont un état explicite. Le fond de carte nécessite Internet ; les tuiles sont mises en cache dans l'espace privé de l'application.
- Angular et Android, éditeurs existants : clic sur carte et déplacement du marqueur → validation terrestre serveur → géocodage inverse → coordonnées/adresse. Saisie d'adresse → recherche explicite → choix parmi les propositions → validation et centrage. Modification manuelle des deux coordonnées → validation après une courte temporisation. Les mises à jour programmatiques ne relancent pas cette temporisation. Les réponses devenues obsolètes sont ignorées.
- Une adresse issue du géocodage inverse est signalée comme approximative lorsque le numéro/la rue manque ou que l'objet retourné est éloigné. Une adresse inconnue n'est pas inventée : l'utilisateur doit la préciser. Les autres valeurs du formulaire, photos et fonctions d'estimation existantes sont conservées.
- Les trois valeurs sont enregistrées par les DTO et endpoints de création/modification existants. Le backend revalide la position lors de chaque écriture dotée de coordonnées, y compris la correction administrative utilisant le même service de validation. Une demande sans GPS reste possible.

## Validation terre/eau et dépendances

Le géocodage inverse n'est **pas** utilisé comme preuve de terrain sec. `LocationService` interroge le pixel brut du raster **ESA WorldCover 2021 v200** avec TiTiler, à sa résolution nominale de 10 m. Les classes eau permanente (80), zone humide (90) et mangrove (95) sont refusées. Seules les classes terrestres explicitement autorisées sont acceptées ; valeur inconnue, absence de données et indisponibilité empêchent l'acceptation. Le pays `tn` du géocodage et les bornes déjà utilisées par le projet limitent la position à la Tunisie.

Ce contrôle s'appuie sur une classification géographique réelle, pas sur la couleur d'une tuile ni sur des polygones dessinés à la main. Il reste limité par la résolution et la date du produit : ce n'est pas un relevé hydrologique en temps réel ; un très petit bassin ou une étendue d'eau apparue après l'acquisition peut échapper à cette donnée. Les zones humides sont refusées de façon conservatrice. Référence et licence CC BY 4.0 : [ESA WorldCover, données et attribution](https://esa-worldcover.org/en/data-access). Attribution : © ESA WorldCover project 2021 / Contains modified Copernicus Sentinel data (2021), processed by ESA WorldCover consortium.

La lecture passe par l'API `GET /cog/point/{lon},{lat}` de [TiTiler](https://developmentseed.org/titiler/endpoints/cog/), avec une URL de tuile WorldCover construite uniquement par le serveur. Aucun client ne peut fournir une URL arbitraire. La valeur brute du pixel est utilisée, pas une image WMS colorisée.

Le géocodage est assuré par Nominatim. Le backend espace globalement les départs de requêtes d'au moins 1,1 seconde par processus et conserve un cache mémoire borné de 15 minutes. Aucune autocomplétion au fil de la frappe d'adresse, aucun préchargement systématique : l'utilisateur déclenche la recherche et choisit le résultat. Un `User-Agent` identifie FastColoc. [Politique Nominatim](https://operations.osmfoundation.org/policies/nominatim/) : maximum 1 requête/seconde pour l'ensemble de l'application, attribution OSM et cache requis, service public sans SLA. **En déploiement multi-instance, utiliser un proxy avec limite commune ou un fournisseur/une instance dédiée ; la limite en mémoire d'un seul serveur ne coordonne pas plusieurs instances.**

Les tuiles OSM utilisent le cache HTTP/osmdroid, l'identification de l'application et l'attribution, sans téléchargement hors ligne massif : [politique des tuiles OSM](https://operations.osmfoundation.org/policies/tiles/). Les services publics ne fournissent pas de garantie contractuelle de disponibilité. Les points validés sont mis en cache pendant 15 minutes avec leurs coordonnées exactes : aucune cellule arrondie susceptible de traverser un rivage n'est assimilée à un point validé. Un nouveau point sans preuve valide reste refusé en cas de panne.

Configuration backend facultative, sans changement de port ni de conteneur :

| Propriété Spring | Défaut | Usage |
|---|---|---|
| `fastcoloc.geo.geocoder-url` | `https://nominatim.openstreetmap.org` | Instance Nominatim compatible, remplaçable côté serveur |
| `fastcoloc.geo.raster-url` | `https://titiler.terrascope.be` | API TiTiler COG compatible pour lire les valeurs du raster |
| `fastcoloc.geo.user-agent` | `FastColoc/0.1 (https://github.com/HamzaBellaaj/Fast_Coloc)` | Identification de l'application à adapter au contact de déploiement |

Aucune clé supplémentaire. Les sorties HTTPS vers ces fournisseurs et les tuiles OSM doivent être autorisées. Le client géographique Java force HTTP/1.1 : le contrôle réel a échoué avec la négociation par défaut, puis réussi avec HTTP/1.1. Les timeouts et les erreurs produisent un refus explicite, sans désactiver la vérification TLS.

## API et principaux fichiers

Deux routes géographiques ont été ajoutées, sous les règles JWT/USER/ADMIN existantes de `/api/v1/geography/**` :

- `GET /api/v1/geography/search?q=...` → liste de `{address, latitude, longitude}`. Cette réponse ne valide pas automatiquement les positions.
- `POST /api/v1/geography/position` avec `{latitude, longitude}` → `{latitude, longitude, address, approximate, source}` seulement après validation.

Les favoris et la carte consomment les endpoints d'annonces existants ; aucune table, migration ou modification distante Supabase n'est nécessaire.

Fichiers centraux :

- Backend : `backend/src/main/java/tn/fastcoloc/geography/{LocationController,LocationService,LocationGateway}.java` ; branchement dans `listings/ListingService.java`.
- Angular : `frontend/src/app/features/listings/location-picker.ts`, `listing-editor.ts`, `listing-editor.html` ; messages géographiques dans `frontend/src/app/core/feedback.ts`. La Smart-Map Web du catalogue n'a pas été reconstruite.
- Android : `ui/listings/{DiscoverFragment,ListingMapFragment,EditorLocationController,FastColocMapView}.kt`, `viewmodel/{DiscoveryViewModel,MapViewModel,LocationViewModel}.kt`, intégration dans `ListingEditorFragment.kt`, repository/contrats Retrofit et ressources XML/navigation. Le changement d'`AuthActivity.kt` concerne uniquement les destinations de navigation ; aucune logique Auth n'a été modifiée.

## Vérification

Les vérifications ciblées distinguent les fournisseurs réels des simulateurs :

- `LocationServiceTest` : eau/zones humides, données inconnues, bornes/pays, cache exact, sélection explicite et panne, avec fournisseur simulé.
- `LocationServiceTest#realProvidersIdentifyTunisLandLakeAndSea` avec `-DfastcolocGeoLive=true` : appels Java réels aux services publics ; point terrestre de Tunis accepté, lac de Tunis et mer refusés. Contrôle volontairement opt-in pour éviter un accès externe dans les tests ordinaires.
- Trois méthodes de `Sprint1IntegrationTest` : création/modification et relecture des coordonnées/adresse, rejet d'eau sans écrasement, protection JWT, propriété/favoris idempotents et contrat carte. HTTP Spring, JWT et PostgreSQL locaux réels ; Auth/Storage/géographie simulés, aucune base distante touchée.
- Tests Angular ciblés du composant de localisation et de l'éditeur : conservation du dernier point, panne, choix explicite, exclusion des réponses anciennes et maintien de l'estimation existante.
- `DiscoveryGeographyTest` Android : HTTP Retrofit vers MockWebServer, swipe/favori idempotent, absence de favori pour « passer », maintien de la carte sur erreur, exclusion des annonces déjà traitées, filtres/contrat carte et restauration après refus d'eau.
- `frontend/e2e/geography.spec.ts` : parcours navigateur Angular/Leaflet avec réponses HTTP et tuiles de test isolées ; aucune connexion Supabase réelle.

Résultats effectivement obtenus :

| Contrôle | Résultat |
|---|---|
| Backend `mvn -q -DskipTests package` | Réussi |
| Angular `npm run build` | Réussi ; avertissements existants budget CSS de l'accueil et Leaflet CommonJS |
| Android `gradlew assembleDebug` | Réussi, APK debug produit |
| `LocationServiceTest`, fournisseur simulé | 8 cas réussis |
| Trois méthodes ciblées `Sprint1IntegrationTest` | 3 réussies, PostgreSQL/HTTP locaux |
| Contrôle Java opt-in des fournisseurs réels | 1 réussi : Tunis accepté, lac et mer refusés |
| Vitest localisation + éditeur | 6 tests réussis |
| Android `DiscoveryGeographyTest` | 4 tests réussis |
| Playwright `e2e/geography.spec.ts` | 1 parcours réussi dans Chrome : carte → champs, coordonnées → carte, refus d'eau, recherche/choix, sauvegarde et rechargement |

Une erreur de structure de template rencontrée pendant l'intégration a été corrigée avant le dernier build Angular et le succès Playwright. Le premier passage du test favori Android avait un faux corps HTTP 204 dans son simulateur ; le simulateur a été corrigé, puis les quatre tests ont réussi. Aucun résultat d'un passage en échec n'est présenté comme réussi.

Aucun appareil Android n'était connecté lors de l'inspection `adb devices` ; les tests JVM ne démontrent pas une interaction tactile sur un appareil réel. La validation croisée des clients sur Supabase n'a pas été exécutée : les tests de persistance utilisent PostgreSQL local, et le parcours navigateur utilise une API simulée.

## Essai manuel minimal

1. Démarrer le backend et Angular selon les scripts existants. Installer `mobile/app/build/outputs/apk/debug/app-debug.apk` sur l'émulateur/téléphone avec la configuration réseau habituelle ; aucun nouveau port.
2. Android : menu **Découvrir**, passer une annonce, aimer la suivante, puis vérifier le favori sur Angular après actualisation ; ouvrir une carte puis revenir. Couper le réseau lors d'un ajout : la carte doit rester disponible pour réessayer.
3. Android : appliquer des filtres dans le catalogue, puis ouvrir **Carte des annonces** depuis son menu ; sélectionner un marqueur et **Ouvrir la fiche**.
4. Dans un formulaire d'offre Web ou Android : rechercher une adresse tunisienne et sélectionner une proposition ; déplacer le marqueur ; modifier les coordonnées. Vérifier les trois champs et préciser l'adresse approximative si nécessaire.
5. Tester `36.822, 10.249` (lac de Tunis), puis `36.88, 10.5` (mer) : refus et maintien du dernier point valide. `36.8065, 10.1815` sert de point terrestre de contrôle à Tunis.
6. Enregistrer, recharger l'annonce sur les deux clients puis modifier/enregistrer de nouveau. Vérifier les mêmes adresse/latitude/longitude et la conservation des autres champs/photos.
