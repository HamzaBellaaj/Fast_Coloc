# Sprint 2 — lecture et application des références Android

Le dossier fourni est `reference` (singulier). Aucun dossier `references` ni TP1–TP5
n'a été trouvé. Les neuf PDF ont été inventoriés avant les modifications Android.
Les textes des 334 pages ont été extraits et lus. Les 53 pages contenant moins
de 90 caractères de texte, ainsi que les 15 pages des TP, ont aussi été rendues
pour vérifier les schémas et les captures absents de l'extraction textuelle.
Les extractions et rendus de travail sont dans `backend/target/sprint2-courses`.

| Document présent | Pages | Enseignement utilisé |
|---|---:|---|
| `chapitre 1_2026 (2).pdf` | 64 | Kotlin, types, nullabilité, data classes, lambdas, SDK/Gradle, APK/ADB |
| `chapitre2 (2).pdf` | 57 | Ressources, XML, ViewGroups, dp/sp, Activity, ViewBinding, champs et événements |
| `chapitre 3 (1).pdf` | 51 | Écouteurs, cycle de vie, sauvegarde d'état, Intent/ActivityResult, dialogues |
| `chapitre 4_2026 (2).pdf` | 42 | RecyclerView, Adapter, ViewHolder, Linear/GridLayoutManager, menus |
| `chap5V2026.pdf` | 33 | Fragment, NavHost/NavController, MVVM, activityViewModels, LiveData |
| `chap6V2026-Room.pdf` | 42 | Entity, DAO, Database singleton, Repository, Factory, coroutines |
| `chap7-Retrofit.pdf` | 30 | REST, annotations Retrofit, OkHttp, Gson, DTO, gestion HTTP/IO |
| `TP6-SQLite-Room2026.pdf` | 9 | CRUD via Repository/DAO, Factory, ViewModel partagé, MenuProvider/SearchView |
| `TP7 - Retrofit.pdf` | 6 | Contrat JSON, appels GET/POST/PUT/DELETE, Repository, états et erreurs |

## Méthodes réellement retenues

Flux : `Fragment XML/ViewBinding → ListingViewModel → ListingRepository →
AuthRepository.authorized → Retrofit/OkHttp → Spring Boot`.

Après une lecture serveur réussie, le Repository écrit la page dans Room ;
`ListingCacheDao.observe(owner,key)` produit la LiveData observée par le ViewModel,
puis par le Fragment avec `viewLifecycleOwner`. Room contient des **copies de
réponses serveur**, avec UUID du compte, clé de recherche/pagination et date.
Il n'existe aucune publication ni modification d'annonce autonome en SQLite.

Le cache des fiches peut être relu après une IOException. Les URL photo signées
sont retirées de la copie hors ligne ; les pages de plus de 24 h ne sont plus
présentées. Le statut de copie locale reste visible. Déconnexion, session refusée
ou compte bloqué effacent aussi le cache des annonces. Les deux comptes de recette
servent à vérifier cette isolation.

Les Fragments n'appellent ni Retrofit ni Room. La Factory fournit le Repository
au ViewModel partagé. Les commandes du ViewModel utilisent `viewModelScope` sur
`Dispatchers.IO` et `postValue`. Les bindings et les adapters sont détachés dans
`onDestroyView`. Le brouillon d'interface et sa version sont conservés à la rotation.

La liste emploie LinearLayoutManager en portrait téléphone et GridLayoutManager
à partir de 600 dp ; les photos utilisent une grille à deux colonnes. Aucun
`setHasFixedSize(true)` sur les galeries à hauteur `wrap_content` (chapitre 4, p.25).
Le menu XML et son `MenuProvider` sont attachés à `viewLifecycleOwner` en état
RESUMED (TP6, p.8). Les clics passent par les callbacks des ViewHolders.

## Adaptations justifiées

- Les versions du Sprint 1 sont conservées : Retrofit 3.0.0, Room 2.8.4 et OkHttp
  4.12.0. Les numéros d'exemple des TP ne justifient pas une régression de dépendances.
- TP6 p.1 montre KAPT pour générer les implémentations Room. Le projet Sprint 1
  dispose déjà de KSP : ce pipeline est conservé. Entity/DAO/Database et les requêtes
  restent vérifiés et générés à la compilation, comme l'exige l'exercice.
- Le conteneur `AppContainer` existant centralise les singletons avec le contexte
  Application. Il remplit la fonction du conteneur d'application du TP6 p.5, sans
  créer plusieurs Database/Repository dans les Fragments.
- Le TP7 commence par appeler Retrofit dans le ViewModel, puis demande d'introduire
  un Repository dans l'exercice final. FastColoc applique cette séparation finale.
- Les tokens restent protégés par le mécanisme Keystore du Sprint 1. L'exemple de
  booléen `isLoggedIn` en préférences du chapitre 6 n'accorde aucun droit métier.
- Aucun journal HTTP de corps ou de jeton : la suggestion de logging du chapitre 7
  est adaptée aux exigences de confidentialité de FastColoc.
- Le chapitre 6 et le TP6, p.9, distinguent les DAO suspendus de la LiveData déjà
  asynchrone. Une coroutine lancée sans Dispatcher ne devient pas automatiquement
  un thread IO : les accès fichiers sont explicitement déportés sur IO.
- Le cache utilise une nouvelle base Room dédiée et conserve les bases du Sprint 1.
  Une méthode DAO supplémentaire ne change pas le schéma de `ProfileCache`.

## Lisibilité et portée de la lecture

Aucun des neuf PDF n'est entièrement illisible. L'extraction seule perd notamment
le code illustré du TP6 p.2–5 et l'interface/client Retrofit du TP7 p.2 : les rendus
ont été consultés. Les pages de titre à faible texte ne sont pas des pages manquantes.
La matrice finale associe les notions aux fichiers FastColoc et aux tests réellement
exécutés ; elle ne prétend pas reproduire tous les exemples ou toutes les fonctions
Android mentionnées dans ces cours.
