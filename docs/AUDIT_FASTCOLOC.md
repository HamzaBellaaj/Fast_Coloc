# Audit fonctionnel FastColoc — Web, Android et backend

Date : 23 novembre 2026. Méthode : inspection statique du dépôt ouvert.

Seul ce rapport a été créé. Aucun fichier source, paramètre, service, workflow n8n, base ou migration n'a été modifié. Aucune application, compilation, campagne de tests, installation ou requête réseau n'a été lancée. Les résultats de recettes anciennes cités dans les documents ne constituent pas des résultats de cet audit.

## Références et portée des verdicts

Documents effectivement consultés :

- `docs/sprint0_pi.pdf`, équivalent du Sprint 0 révisé : 12 pages, Product Backlog US01–US36 pages 6–8, planification page 9 et répartition page 10.
- `docs/Projet 2fastcoloc (1).pdf` : cahier des charges, 2 pages.
- `docs/FastColoc_repartition_web_mobile_coches.docx` : répartition fonctionnelle et décisions techniques. L'équivalent Markdown demandé n'est pas présent.
- `docs/05-SPRINT1.md`, `docs/12-SPRINT1-OPERATIONNEL.md`, `docs/15-SPRINT2-PERIMETRE.md`, `docs/16-SPRINT2-REFERENCES-ANDROID.md`, `docs/17-SPRINT2-ANNONCES-RECHERCHE.md`, `docs/18-US29-GESTION-ADMIN-ANNONCES.md`, `docs/20-PARCOURS-ANNONCES.md` et `README.md`.

Les PDF et le DOCX ont été lus localement, sans téléchargement ni installation. Les consignes de la demande priment sur les documents antérieurs : aucune administration des boosts, aucun suivi administratif des paiements, aucune statistique de boost ; vidéo et 3D facultatives ; aucun swipe Angular ni administration Android.

Statuts utilisés :

- ✅ **IMPLÉMENTÉ** : code présent et parcours relié de bout en bout.
- 🟠 **PARTIEL** : implémentation incomplète ou éléments manquants.
- ❌ **ABSENT** : aucune implémentation fonctionnelle identifiée.
- ❔ **NON VÉRIFIABLE** : preuves insuffisantes sans exécution réelle.

Dans cet audit statique, « IMPLÉMENTÉ » valide le raccordement interface → appel HTTP → contrôleur → service → accès aux données ou adaptateur externe → réponse → affichage. Il ne certifie ni la disponibilité actuelle de Supabase/n8n ni le succès d'un parcours utilisateur exécuté aujourd'hui. Ces limites sont isolées dans le tableau pour ne pas confondre un service non sollicité avec du code manquant. « — » signifie hors périmètre de cette plateforme ou absence de composant nécessaire ; ce n'est pas un statut supplémentaire.

## A. Tableau global

Les chemins ci-dessous sont relatifs à la racine du dépôt. Pour alléger les preuves Android, `M/` désigne exactement `mobile/app/src/main/java/com/iset/fastcolloc/` ; `R/` désigne `mobile/app/src/main/res/`. Les recherches d'absence ont porté sur les sources de production, routes, ressources, dépendances et migrations locales, et non sur les seuls noms de fichiers.

| Fonctionnalité | Backend | Angular | Android | Statut global | Preuves |
|---|---|---|---|---|---|
| Inscription et connexion e-mail/mot de passe, US01/02 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/auth/AuthController.java`, `auth/AuthService.java` dans le même package ; `frontend/src/app/features/onboarding/registration.ts`, `features/auth/auth-page.ts` ; `M/data/AuthRepository.kt`, `M/viewmodel/Sprint1ViewModel.kt`, `M/ui/auth/LoginFragment.kt` |
| Connexion Google, US32 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/auth/AuthService.java` (`google`, `exchange`) ; `frontend/src/app/core/auth.ts` ; `M/data/OAuthCoordinator.kt`, `M/ui/auth/AuthActivity.kt`, `mobile/app/src/main/AndroidManifest.xml` |
| Confirmation e-mail et renvoi, US32 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/auth/AuthService.java` ; `frontend/src/app/features/auth/verify.ts`, `core/auth.ts` ; `M/ui/auth/EmailFragment.kt`, `M/data/OAuthCoordinator.kt` |
| Mot de passe oublié et réinitialisation, US32 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/auth/AuthController.java` ; `frontend/src/app/features/auth/auth-page.ts` ; `M/ui/auth/EmailFragment.kt`, `M/ui/auth/ResetFragment.kt`, `M/viewmodel/Sprint1ViewModel.kt` |
| Déconnexion, restauration et renouvellement de session | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/auth/AuthService.java` ; `frontend/src/app/core/auth.ts`, `core/interceptor.ts` ; `M/data/AuthRepository.kt`, `M/data/SessionManager.kt`, `M/data/SecureStore.kt` |
| Consultation et modification du profil, US03 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/onboarding/OnboardingController.java`, `OnboardingService.java`, `profiles/ProfileDataService.java` ; `frontend/src/app/features/profile/profile.ts`, `profile.html`, `features/onboarding/registration.ts` ; `M/ui/auth/ProfileFragment.kt`, `M/viewmodel/Sprint1ViewModel.kt` |
| Photos du profil, US04 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/media/PhotoController.java`, `PhotoService.java` ; `frontend/src/app/shared/photos.ts` ; `M/ui/auth/RegisterStep5Fragment.kt`, `M/viewmodel/Sprint1ViewModel.kt`, `M/data/remote/FastColocApi.kt` |
| Consultation des annonces et accueil, US13/24 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingController.java`, `ListingService.java` ; `frontend/src/app/features/listings/catalog.ts`, `features/home/home.ts` ; `M/ui/listings/ListingCatalogFragment.kt`, `M/ui/auth/HomeFragment.kt`, `M/data/ListingRepository.kt` |
| Détails et photos des annonces, US14/04 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingService.java` (`detail`, `photos`, `sign`) ; `frontend/src/app/features/listings/listing-detail.ts` ; `M/ui/listings/ListingDetailFragment.kt`, `M/ui/listings/ListingPhotoAdapter.kt` |
| Création, modification et suppression personnelles, US11/33 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingWrite.java`, `ListingService.java` ; `frontend/src/app/features/listings/listing-editor.ts`, `catalog.ts` ; `M/ui/listings/ListingEditorFragment.kt`, `M/viewmodel/ListingViewModel.kt`, `M/data/ListingRepository.kt` |
| Publication d'offres par étapes, US08 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingWrite.java`, `ListingService.java` ; `frontend/src/app/features/listings/listing-editor.ts`, `listing-editor.html` ; `M/ui/listings/ListingEditorFragment.kt`, `R/layout/fragment_listing_editor.xml` |
| Publication de demandes, US09 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingWrite.java`, `backend/supabase/migrations/20261117062435_listing_request_location.sql` ; `frontend/src/app/features/listings/listing-editor.ts` ; `M/ui/listings/ListingEditorFragment.kt`, `M/data/ListingDraft.kt` |
| Durée, expiration et renouvellement, US10/12 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingService.java` (`publish`, `search`) ; `frontend/src/app/features/listings/catalog.ts`, `listing-editor.ts` ; `M/data/remote/FastColocApi.kt`, `M/data/ListingRepository.kt`, `M/ui/listings/ListingDetailFragment.kt` |
| Recherche, filtres, tri et pagination, US15/34 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingQuery.java`, `ListingService.java` ; `frontend/src/app/features/listings/catalog.ts`, `catalog.html` ; `M/ui/listings/ListingCatalogFragment.kt`, `R/layout/dialog_listing_filters.xml` |
| Favoris synchronisés, US34 et répartition Web | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingService.java` (`favorite`, mode `favorites`) ; `backend/supabase/migrations/20261116215125_sprint2_listings.sql` ; `frontend/src/app/features/listings/catalog.ts` ; `M/data/ListingRepository.kt`, `M/ui/listings/ListingDetailFragment.kt` |
| Cache Android Room, US24/34 | — | — | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `M/data/ListingRepository.kt`, `M/data/local/ListingCache.kt`, `M/data/local/ProfileCache.kt`, `M/viewmodel/ListingViewModel.kt`, `M/data/AppContainer.kt` |
| Estimation IA Spring Boot → n8n, US25 | ✅ **IMPLÉMENTÉ** | — | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/ai/RentEstimateController.java`, `RentEstimateRequest.java`, `RentEstimateService.java`, `RentEstimatePhotos.java`, `RentEstimateResponse.java` |
| Bouton d'estimation, affichage et choix du loyer, US25 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `frontend/src/app/features/listings/listing-editor.ts`, `listing-editor.html` ; `M/ui/listings/ListingEditorFragment.kt`, `M/viewmodel/ListingViewModel.kt`, `M/data/remote/ListingContracts.kt`, `M/data/remote/FastColocApi.kt` |
| Recommandations de logements Spring Boot → n8n, US26 demandé | ✅ **IMPLÉMENTÉ** | — | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/recommendations/RecommendationController.java`, `RecommendationData.java`, `RecommendationGateway.java`, `RecommendationService.java`, `RecommendationResponse.java` |
| Liste de recommandations, scores, raisons et détails Web/Android | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `frontend/src/app/features/listings/recommendations.ts`, `recommendations.html`, `app.routes.ts` ; `M/ui/listings/RecommendationsFragment.kt`, `M/viewmodel/RecommendationViewModel.kt`, `M/data/ListingRepository.kt`, `R/navigation/nav_auth.xml` |
| Disponibilité réelle Auth/SMTP/Google, SQL/Storage et n8n aujourd'hui | ❔ **NON VÉRIFIABLE** | ❔ **NON VÉRIFIABLE** | ❔ **NON VÉRIFIABLE** | ❔ **NON VÉRIFIABLE** | `backend/src/main/resources/application.properties`, `backend/src/main/java/tn/fastcoloc/auth/SupabaseGateway.java`, `recommendations/RecommendationGateway.java`, `ai/RentEstimateService.java` ; aucune sollicitation des services pendant l'audit |
| Messagerie : entités/tables, repositories, envoi POST et lecture GET, US20/21 | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | Aucun module correspondant dans `backend/src/main/java/tn/fastcoloc/` ni table dédiée dans `backend/supabase/migrations/` ; aucun contrat dans `M/data/remote/FastColocApi.kt` ; aucune route dans `frontend/src/app/app.routes.ts` |
| Conversations, historique, réponse et actualisation REST, US20/21/35 | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | `frontend/src/app/features/`, `frontend/src/app/app.routes.ts`, `M/ui/`, `M/viewmodel/`, `R/navigation/nav_auth.xml` : aucun parcours de messagerie ; pas de polling ni d'actualisation de messages |
| Swipe gauche/passer, droite/intéressé, US27 | ❌ **ABSENT** pour un intérêt dédié | — | ❌ **ABSENT** | ❌ **ABSENT** | `M/ui/listings/ListingAdapter.kt`, `HomeListingAdapter.kt`, `ListingCatalogFragment.kt` : cartes de liste seulement ; aucun écran/geste de swipe ni contrat d'intérêt dans `M/data/remote/FastColocApi.kt` |
| Smart-Map Web : carte, filtres géographiques, marqueurs et liens, US16–18 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/ListingController.java` (`/map`), `ListingQuery.java`, `ListingService.java` ; `frontend/src/app/features/listings/catalog.ts`, `listing-map.ts`, `listing-detail.ts` |
| Smart-Map Web : centrage sur le domaine choisi, US19 | ✅ **IMPLÉMENTÉ** pour filtrer | 🟠 **PARTIEL** | — | 🟠 **PARTIEL** | `frontend/src/app/features/listings/catalog.ts`, `listing-map.ts` : centrage via `fitBounds` des résultats, aucun centrage autonome sur la région sélectionnée si elle est vide |
| Carte simple Android, US35 | ✅ **IMPLÉMENTÉ** pour l'API carte commune | — | ❌ **ABSENT** | ❌ **ABSENT** | `/api/v1/listings/map` existe ; `M/data/remote/FastColocApi.kt` ne l'expose pas ; `R/navigation/nav_auth.xml`, `M/ui/`, `mobile/app/build.gradle.kts` : aucun parcours de carte intégré |
| E-mails techniques de confirmation/récupération | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/auth/AuthService.java` (`signup`, `recover`, `resend`) ; écrans Auth Web et Android ; envoi délégué à Supabase Auth, réception actuelle non vérifiée |
| Notifications e-mail liées aux messages/annonces, US22 | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | Aucun déclencheur dans `backend/src/main/java/tn/fastcoloc/listings/ListingService.java` ni service de notifications ; `auth/AuthService.java` ne couvre que les e-mails Auth |
| Notifications SMS métier, US23 | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | ❌ **ABSENT** | Aucun adaptateur SMS, endpoint ou événement métier identifié ; `R/values/strings_registration.xml` contient une promesse de SMS, sans implémentation correspondante |
| Administration Web : liste utilisateurs, US05 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/admin/AdminController.java`, `AdminService.java` ; `frontend/src/app/core/api.ts`, `features/admin/admin.ts`, `admin.html` ; fonction SQL `admin_users` dans `backend/supabase/migrations/20261112050615_sprint1_backend_capabilities.sql` |
| Administration Web : blocage/déblocage, US06 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/admin/AdminService.java`, `security/AccountAccessFilter.java`, `security/AccountAuthorizationService.java` ; `frontend/src/app/features/admin/admin.ts` ; fonction SQL `admin_change_status` |
| Administration Web : suppression d'un compte, US06 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/admin/AdminService.java` (`delete`, `cleanListingPhotos`) ; `frontend/src/app/features/admin/admin.ts`, `admin.html` |
| Administration Web : modification des coordonnées, US07 | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/admin/AdminController.java`, `AdminRequests.java` ; `backend/supabase/migrations/20261117000621_admin_contact_read.sql` ; `frontend/src/app/features/admin/admin.ts` (`loadContacts`, `confirm`) |
| US29 : liste des annonces administrateur | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/AdminListingController.java`, `AdminListingRepository.java` ; `frontend/src/app/features/admin/admin-listings.ts`, `admin-listings.html` |
| US29 : modification des annonces | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/AdminListingService.java` ; `frontend/src/app/features/listings/listing-editor.ts` (`adminMode`), `frontend/src/app/app.routes.ts` |
| US29 : masquage et réactivation | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/AdminListingService.java`, `ListingService.java` ; `backend/supabase/migrations/20261116233807_us29_listing_moderation.sql` ; `frontend/src/app/features/admin/admin-listings.ts` |
| US29 : suppression et historique de modération | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/listings/AdminListingRepository.java`, `AdminListingService.java` ; migration US29 ; `frontend/src/app/features/admin/admin-listings.ts` (`history`, `deleted`) |
| Restrictions d'accès administrateur | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | — | ✅ **IMPLÉMENTÉ** | `backend/src/main/java/tn/fastcoloc/security/SecurityConfig.java`, `AccountAccessFilter.java`, `AccountAuthorizationService.java` ; SQL `require_admin` ; `frontend/src/app/core/guards.ts`, `app.routes.ts` |
| Restitution Android du masquage d'une annonce personnelle | ✅ **IMPLÉMENTÉ** | ✅ **IMPLÉMENTÉ** | 🟠 **PARTIEL** | 🟠 **PARTIEL** | `M/data/remote/ListingContracts.kt` omet `is_hidden` ; `M/ui/listings/ListingDetailFragment.kt`, `ListingAdapter.kt` ignorent le masquage ; serveur et `frontend/src/app/features/listings/listing-detail.ts` le gèrent |
| Signalement depuis le Web : utilisateur/annonce, US30/36 | ❌ **ABSENT** | ❌ **ABSENT** | — | ❌ **ABSENT** | Aucun modèle, endpoint ni table dans `backend/src/main/java/tn/fastcoloc/`, `backend/supabase/migrations/` ; aucune action dans `frontend/src/app/features/listings/listing-detail.ts` ou route de signalement |
| Consultation et traitement des signalements par ADMIN | ❌ **ABSENT** | ❌ **ABSENT** | — | ❌ **ABSENT** | `frontend/src/app/features/admin/admin-nav.ts`, `admin.ts`, `admin-listings.ts` : utilisateurs/modération directe seulement ; `backend/src/main/java/tn/fastcoloc/admin/` et `listings/AdminListingController.java` : aucune file de signalements |
| Blocage d'un autre utilisateur par l'utilisateur Web, variante US30 | ❌ **ABSENT** | ❌ **ABSENT** | — | ❌ **ABSENT** | Le seul blocage identifié est administratif dans `backend/src/main/java/tn/fastcoloc/admin/AdminController.java` ; aucune relation de blocage personnel ni action Web correspondante |
| Boost payant utilisateur Web, US28 | ❌ **ABSENT** | ❌ **ABSENT** | — | ❌ **ABSENT** | Aucun contrat de boost/paiement dans `backend/src/main/java/tn/fastcoloc/` ou migration dédiée ; `frontend/src/app/features/listings/catalog.ts`, `listing-detail.ts`, `listing-map.ts` : aucune activation, achat ou pin gold |
| Génération vidéo IA Web, facultative | ❌ **ABSENT** | ❌ **ABSENT** | — | ❌ **ABSENT** | Aucun endpoint, stockage vidéo, job ou interface identifié dans les sources de production `backend/src/main/`, `frontend/src/app/` |
| Lecture vidéo Android, facultative | ❌ **ABSENT** pour une vidéo d'annonce | — | ❌ **ABSENT** | ❌ **ABSENT** | `M/data/remote/ListingContracts.kt`, `M/ui/listings/ListingDetailFragment.kt`, `R/layout/fragment_listing_detail.xml` : aucun champ vidéo ni lecteur |
| Prototype 3D rotatif Web, facultatif | — | 🟠 **PARTIEL** | — | 🟠 **PARTIEL** | `frontend/src/app/features/home/apartment-stage.ts`, `apartment-scene.ts`, `home.html`, `frontend/package.json` : scène Three.js décorative avec rotation au défilement, aucun modèle d'annonce reconstruit |

Les préfixes de package abrégés à l'intérieur d'une cellule partagent le répertoire du fichier backend ou frontend complet qui les précède. Aucune absence déclarée ne prétend décrire des composants privés ou des workflows externes non présents dans le dépôt.

## B. Fonctionnalités terminées dans le code

### Authentification, profil et photos

Les deux clients appellent les routes Auth Spring Boot. Le serveur délègue inscription, login, refresh, vérification, récupération et changement de mot de passe à `SupabaseGateway`. Google dispose d'un parcours PKCE avec vérificateur, échange du code et contrôle de l'état ; Android possède le retour `fastcoloc://auth/callback`, Angular `/auth/callback`.

La confirmation est vérifiée avant l'accès métier. Le filtre vérifie l'identité confirmée et lit rôle/statut en base : le client ne choisit pas son autorité. Le rôle ADMIN ne provient pas des métadonnées utilisateur éditables. Les comptes bloqués sont refusés sur les routes métier, tout en conservant les parcours Auth nécessaires à la déconnexion/récupération.

Le Web conserve la session dans `sessionStorage`, la restaure au rechargement du même onglet et renouvelle le jeton. Il ne promet donc pas une reconnexion automatique après fermeture de toute la session navigateur. Android utilise `SessionManager`/`SecureStore`, rotation du refresh sous mutex et purge des caches privés à la fermeture de session ou au refus du compte.

Le profil est lu via `/users/me/details`, modifié par les cinq étapes `/onboarding/steps/{n}` et complété côté serveur. Les deux interfaces réutilisent ces étapes pour l'édition ; les préférences budget/localités sont persistées. Les photos ont upload multipart, légende, suppression et URL privée signée. La présence de ces parcours est confirmée ; aucune réimplémentation n'est nécessaire pour les déclarer raccordés.

### Annonces Web et Android

Le CRUD est réel : UUID de l'auteur issu du JWT, écritures SQL, contrôles de propriété, validation du DTO et version pour l'édition concurrente. Les formulaires distinguent `OFFER` et `REQUEST`. Les offres exigent adresse et coordonnées complètes ; une demande exige une localité mais peut omettre le GPS, conformément au DTO et à la migration corrective locale.

Création/enregistrement puis publication sont deux opérations reliées dans les interfaces. Les photos sont transférées au backend et signées depuis le bucket privé. Le catalogue, les fiches et les listes personnelles consomment les réponses serveur, pas un catalogue fictif embarqué.

`publicationDays`, `published_at` et `expires_at` sont utilisés par le serveur. La recherche exclut les annonces expirées ou masquées ; le renouvellement recalcule publication et échéance ; l'édition normale ne renouvelle pas implicitement. Aucun ordonnanceur n'est nécessaire pour cette exclusion, puisqu'elle est appliquée à la lecture.

Les filtres sont transmis et utilisés dans des requêtes paramétrées : mots-clés, type offre/demande, gouvernorat/localité, loyer, capacité, logement, colocataire, équipements et disponibilité. Tri et pagination sont présents. Les favoris utilisent `PUT`/`DELETE /listings/{id}/favorite`, une table persistante avec clé utilisateur/annonce et une liste dédiée sur chaque client. La synchronisation passe par la même API lors des lectures/actualisations, sans diffusion en temps réel.

Android dispose du flux Fragment → ViewModel → Repository → Retrofit et d'un cache Room par compte. Les fiches et pages déjà chargées peuvent servir de copies locales ; le code retire les URL signées périmées et limite la durée du cache. Cela ne constitue pas une publication hors ligne autonome.

### US25 : estimation du loyer

Flux contrôlé dans le code : bouton Web/Android → `POST /api/v1/ai/rent-estimate` → `RentEstimateController` → `RentEstimateService` → webhook n8n → `RentEstimateResponse` → affichage et application facultative du loyer.

Contrat concordant : partie multipart `logement` en JSON avec `localisation`, `type`, adresse, description, capacité et équipements ; une ou plusieurs parties `photo`. Android construit explicitement ces noms malgré le paramètre Kotlin nommé `photos`. Le serveur accepte 1–10 photos et prépare une vue groupée lorsque le workflow attend une seule image. Il transmet `message` et `photo` au webhook et contrôle la cohérence min/max/loyer conseillé de la réponse.

Les deux écrans lisent `success`, `estimation.disponible`, `loyer_min_tnd`, `loyer_max_tnd`, `loyer_conseille_tnd`, fiabilité et explication. Ils permettent d'utiliser ou d'ignorer l'estimation et de saisir son prix. Aucun prix de secours codé en dur n'a été identifié. Les deux boutons d'application arrondissent actuellement au dinar entier ; le loyer saisi manuellement accepte trois décimales.

### US26 : recommandations de logements

Flux contrôlé : route/écran « Pour toi » → `POST /api/v1/recommendations/ai` → snapshot du profil et des offres → webhook n8n → validation du classement → relecture des annonces → liste Web/Android.

Le backend envoie budget/date/localités et au plus 30 offres candidates publiées, non expirées, non masquées et appartenant à d'autres utilisateurs. Le payload n'inclut pas les contacts, photos, jetons ou biographies. La réponse n8n attend `success` et une liste de `annonce_id`, `score`, `raison` ; le serveur ignore les identifiants étrangers aux candidats, évite les doublons, borne les scores et renvoie au plus dix résultats.

Le DTO public est volontairement différent du webhook : chaque résultat contient `listing`, `score`, `raison`, avec `profileIncomplete` au niveau racine. Angular et Android consomment bien ce DTO enrichi. Les résultats ouvrent les vraies fiches et permettent les favoris. En absence de candidats, une liste vide est retournée sans prix ni annonce inventés. Le calcul n'est pas un moteur de recommandation de profils de colocataires : voir la divergence de périmètre en F.

### Administration Angular et US29

La liste paginée des utilisateurs, le blocage/déblocage, la correction des six champs de contact et la suppression sont reliés aux endpoints et fonctions SQL. La suppression bloque d'abord le compte, nettoie les photos de profil et d'annonces, puis appelle la suppression Auth. Les erreurs de nettoyage donnent un état à reprendre, pas un succès fictif.

US29 couvre liste/recherche/filtres, détail, édition avec motif, masquage, réactivation, suppression et historique conservé après suppression. Les dates de publication ne sont pas prolongées par une réactivation. La sécurité existe aux trois niveaux : garde Angular, règles Spring `/admin/**`, fonctions SQL vérifiant `require_admin()` et un compte actif. L'administration directe des annonces n'implique aucune gestion de signalements.

Les fichiers Android `ui/admin/*`, `AdminRepository.kt` et `AdminViewModel.kt` sont des restes locaux. Le graphe `nav_auth.xml` et les menus actuels ne les exposent pas ; `SessionManager.getAccountType()` retourne `null` et empêche leur contrôle ADMIN local de réussir. Ils ne représentent ni une administration Android opérationnelle ni un backend d'administration manquant. Le périmètre Web exclusif est respecté dans la navigation actuelle.

## C. Fonctionnalités partielles : manque précis

| Élément | Ce qui existe | Ce qui manque pour le parcours complet |
|---|---|---|
| Smart-Map Web, centrage US19 | Leaflet/OSM, API `/listings/map`, filtres de région/localité et de rectangle, récupération paginée des marqueurs, liens vers les fiches, `fitBounds` sur les annonces | Le composant reçoit seulement les annonces, pas la zone administrative sélectionnée. Sans résultat, aucun `fitBounds` n'est exécuté et la carte garde sa vue précédente ou sa vue nationale. Il manque un centrage sur le domaine choisi indépendant de la présence d'annonces. Les autres sous-fonctions US16–18 sont raccordées. |
| Affichage du masquage dans les parcours personnels Android | Le serveur fournit `is_hidden`, filtre catalogue/favoris et refuse publication/renouvellement d'une annonce masquée ; le propriétaire peut encore lire son annonce | `ListingDto` ne déclare pas `is_hidden`. La fiche et les cartes présentent une annonce masquée comme publiée et proposent favori/renouvellement selon le seul `status`. Il manque le transport de cet état et l'adaptation du libellé/des boutons. Il ne s'agit pas d'ajouter de l'administration Android. |
| Notifications, si l'on regroupe Auth et métier | Demandes d'e-mails de confirmation et récupération déléguées à Supabase | Les événements métier, destinataires, envois e-mail et SMS ne sont pas implémentés. US22 et US23 restent **ABSENT**, les e-mails Auth ne les réalisent pas. Le bandeau `Feedback` Angular et les Toast/Snackbar Android sont des retours d'interface, pas des notifications métier. |
| Prototype 3D Web facultatif | `ApartmentStage` charge une scène Three.js procédurale ; `world.rotation.y` varie au défilement ; image de repli et réduction des animations | Aucun modèle d'annonce, reconstruction depuis photos, chargement de modèle reconstruit ou visionneuse associée à une fiche. Le décor est explicitement illustratif. Une base graphique existe, sans démontrer le prototype immobilier évoqué dans le cadrage. |

Les raccordements US25 et recommandations de logements ne sont pas classés partiels au seul motif que n8n n'a pas été appelé pendant l'audit. Leur comportement externe reste **NON VÉRIFIABLE** dans cette mission.

## D. Fonctionnalités absentes du périmètre validé

1. **Messagerie REST Web + Android — US20, US21, partie messagerie US35.** Aucun modèle de message/conversation, table locale de migration correspondante, repository, service, endpoint POST/GET, contrat client, écran de conversations, historique, réponse ou actualisation identifié. Les chaînes `message` repérées concernent erreurs HTTP, retours UI ou le prompt d'estimation. Elles ne sont pas de la messagerie privée. Aucun WebSocket n'est requis.
2. **Swipe Android — US27.** Les cartes RecyclerView sont réutilisables visuellement, mais il n'existe ni pile de swipe, ni gestes gauche/droite, ni état « passé/intéressé ». Les favoris ne constituent pas une preuve d'enregistrement d'intérêt par swipe. Le cadrage ne fixe pas un contrat de persistance de cet intérêt : aucun nouvel endpoint n'est imposé par cet audit.
3. **Carte Android simple — partie carte US35.** Des coordonnées et une API Web réutilisable existent. Aucun écran de carte, destination de navigation, liaison Retrofit `/listings/map` ni composant cartographique Android n'est intégré. Saisir des coordonnées dans un formulaire ne remplit pas cette exigence.
4. **Notifications métier e-mail — US22.** Aucun déclenchement sur nouveau message ou événement d'annonce, aucun adaptateur de notification dédié ou appel n8n de notification identifié.
5. **Notifications métier SMS — US23.** Aucun envoi identifié. La collecte d'un téléphone et un texte promettant des SMS ne constituent pas l'intégration. Priorité basse dans le backlog, mais report non acté dans les sources consultées.
6. **Signalements Web utilisateur et annonce — US30/US36 — et consultation/traitement ADMIN.** Absence de création, stockage, statuts, consultation et traitement des signalements. Le masquage manuel US29 existe séparément. Le blocage personnel d'un autre utilisateur évoqué comme variante d'US30 n'est pas implémenté non plus ; le blocage ADMIN de compte est un autre parcours.
7. **Boost payant utilisateur Web — US28.** Aucun achat/activation, passerelle de paiement, confirmation de paiement, droit de boost rattaché à l'auteur, durée de boost, effet de mise en avant ou marqueur gold identifié. `RecommendationData`/`ListingService.recommendationCandidates` ne lisent aucun état de boost et la carte utilise une icône identique pour toutes les offres. La réalisation attendue reste côté utilisateur Web ; aucune administration de boosts/paiements/statistiques n'est à ajouter.

La génération vidéo Web et sa lecture Android sont également absentes, mais sont volontairement traitées en E comme expériences facultatives, sans blocage de livraison.

## E. Priorités restantes

### 1. Fonctionnalités obligatoires manquantes

Ordre proposé selon les dépendances du code constaté, sans estimation arbitraire en jours :

1. **Messagerie REST commune puis interfaces Web/Android** : elle conditionne le contact avec l'auteur et les notifications de messages. Couvrir envoi, lecture/historique, réponse et actualisation manuelle ou périodique ; aucune architecture WebSocket à prévoir.
2. **Signalements Web et file ADMIN** : relier les remontées utilisateurs à la modération US29 déjà disponible. Conserver la distinction entre blocage personnel éventuel US30 et suspension administrative d'un compte.
3. **Carte Android simple** : exploiter les coordonnées et les routes existantes ; consultation, marqueurs et accès aux fiches suffisent au périmètre mobile, sans dupliquer toute la Smart-Map Web.
4. **Swipe Android simple** : partir des annonces réelles ; passer/intéressé uniquement. Ne pas ajouter de matching complexe ni de swipe aux recommandations. Définir le devenir de l'intérêt si sa persistance est retenue.
5. **Notifications métier e-mail**, une fois les événements à notifier disponibles ; puis **SMS**. Le SMS est de priorité basse et candidat au report dans le Sprint 0, mais reste prévu tant qu'un report explicite n'est pas validé.
6. **Boost payant Web utilisateur** : paiement, activation, effet de visibilité/recommandation et pin gold. La passerelle reste à choisir selon le cadrage. Exclure explicitement tout module administratif de boosts, paiements ou statistiques.

Ces lots n'obligent pas à reprendre l'authentification, le CRUD, US25, la recommandation de logements ou US29, dont les chaînes sont déjà présentes.

### 2. Corrections des fonctionnalités partielles et clôture

- Corriger l'affichage Android des annonces masquées, pour éviter les actions proposées puis refusées par le serveur.
- Compléter le centrage US19 sur une région/localité sans résultat et préciser le comportement de conservation d'une zone géographique lors du changement des filtres.
- Réconcilier la portée d'US26 : le texte PDF mentionne logements **et colocataires**, alors que la demande actuelle vérifie les recommandations de logements et que l'implémentation ne produit que des offres. Ne pas lancer un nouveau moteur de recommandation de personnes sans arbitrage de cette divergence.
- Aligner les documents d'état sur le code : l'IA ne doit plus être annoncée simplement « à venir » ; les SMS ne doivent pas être présentés dans l'interface comme un service déjà assuré.
- **US31, clôture de livraison** : les tests et collections existants sont des bases, pas une preuve de recette complète actuelle. Aucun fichier Dockerfile/Compose n'a été identifié dans l'inventaire du dépôt. Le déploiement Docker prévu au backlog et l'acceptation finale restent à préparer/vérifier dans une mission ultérieure. Rien n'a été exécuté ou modifié à ce titre pendant cet audit.

### 3. Fonctionnalités facultatives ou expérimentales

- Génération vidéo Web depuis les photos : **ABSENT**.
- Lecture d'une vidéo d'annonce sur Android : **ABSENT**, dépend d'une vidéo réellement disponible.
- Prototype immobilier 3D rotatif Web : **PARTIEL**, décor procédural existant seulement.

Ces trois éléments ne conditionnent pas l'acceptation du Sprint 3. Aucun travail 3D, génération vidéo, boost ou administration n'est requis sur Android.

## F. Risques et incohérences concrets

### F1. État de masquage perdu dans le DTO Android

Le backend retourne `is_hidden` et conserve les annonces masquées dans les lectures de leur propriétaire. `M/data/remote/ListingContracts.kt` omet ce champ. `M/ui/listings/ListingDetailFragment.kt` active le favori dès `status == "PUBLISHED"`, affiche « Publiée jusqu'au… » et propose le renouvellement au propriétaire ; `ListingAdapter.kt` ignore également le masquage. Les appels sont reliés, mais le serveur les refuse pour une annonce masquée. Aucun contournement des droits serveur n'est constaté : c'est une incohérence fonctionnelle d'affichage et d'actions.

### F2. Centrage cartographique dépendant des résultats

`frontend/src/app/features/listings/listing-map.ts` initialise la carte sur la Tunisie puis exécute `fitBounds` uniquement si `points.length > 0`. `catalog.ts` transmet les marqueurs et reçoit une zone ; il ne transmet pas de géométrie de région/localité sélectionnée. Le centrage promis par US19 n'est donc pas garanti pour une zone vide. Le filtre lui-même est bien appliqué par `ListingService.search`.

### F3. US26 : écart de formulation entre backlog et périmètre audité

Le PDF, page 7, décrit des « annonces et des colocataires recommandés ». L'implémentation `RecommendationData` → `ListingService.recommendationCandidates` sélectionne exclusivement des offres `OFFER`, et `RecommendationResponse.Item` contient une annonce, un score et une raison. Aucun classement de profils de colocataires n'a été identifié. Le besoin actuel de **recommandations de logements** est raccordé ; la version large de la story originale demeure **PARTIEL**. Cette réserve ne signifie pas que l'intégration n8n de logements doit être refaite.

### F4. Documents et texte d'interface en retard sur le code

`README.md` présente encore l'estimation et les recommandations dans la roadmap Sprint 3, alors que les endpoints, services et deux interfaces sont présents. À l'inverse, `R/values/strings_registration.xml` annonce que le téléphone sert aux notifications SMS, sans fournisseur ni déclenchement identifié. Les fichiers d'administration Android historiques peuvent également induire un lecteur en erreur, mais ils sont inaccessibles via la navigation actuelle et bloqués par le gestionnaire de session.

### F5. Configuration d'exécution externe non certifiable hors lancement

`backend/src/main/resources/application.properties` attend les variables JDBC et clés Auth/Storage ; `ddl-auto=validate`, initialisation SQL désactivée et migrations automatiques désactivées supposent une base déjà préparée. Les migrations locales montrent les fonctions, droits et politiques nécessaires ; elles ne démontrent pas leur application distante actuelle. `docs/12-SPRINT1-OPERATIONNEL.md` documente d'ailleurs un historique local incomplet et des horodatages distants différents. Aucune migration ne doit être déduite comme « à rejouer » à partir de cet audit.

Les webhooks d'estimation et de recommandations ont des valeurs par défaut sur `localhost:32768`, avec variables de surcharge. Cette adresse est cohérente pour un backend tournant sur l'hôte avec ce port publié ; elle ne suffit pas à prouver la bonne destination dans un autre environnement. Les timeouts IA sont prévus : serveur 180 s par défaut, Angular 200 s pour ces appels, Android lecture 200 s et durée totale 220 s. Aucun défaut manifeste de nom d'endpoint ou de multipart n'a été identifié dans ces raccordements. La disponibilité de n8n/Ollama/Tavily et le format réellement émis aujourd'hui restent **NON VÉRIFIABLE** sans appel externe, interdit ici.

La configuration Android release utilise `https://backend-not-configured.invalid/api/v1/` tant que `fastcolocReleaseApiUrl` n'est pas fourni. C'est une valeur de garde explicite, pas une adresse de production utilisable. Le debug vise `http://10.0.2.2:8001/api/v1/`. Angular utilise `/api/v1`, avec proxy de développement vers le port 8001 ; l'hébergement de production doit fournir le routage correspondant. Aucun fichier privé de secrets n'a été reproduit dans ce rapport.

### F6. Frontières des preuves de sécurité et d'exploitation

Des protections concrètes existent : signature/issuer/audience du JWT, identité confirmée, état actif en base, routes ADMIN, contrôle SQL `require_admin`, RLS et droits limités dans les migrations, propriété des annonces et validation serveur. L'audit n'a pas identifié de route administrative laissée sans contrôle dans le code examiné. Il ne certifie pas la configuration RLS/Storage actuellement déployée ni une absence universelle de vulnérabilités.

La suppression de photos Storage et les mutations SQL/Auth sont des opérations distinctes. `AdminService.delete` prévoit blocage préalable et reprise du nettoyage ; les suppressions d'annonces retirent les objets avant la suppression SQL. Sans exécution, la reprise après panne et les permissions du stockage ne sont pas certifiées. Les URL de photos d'annonces sont signées pour 300 secondes : les URL déjà émises ne sont pas révoquées instantanément par le masquage.

## Conclusion de l'audit

Le socle comptes/profils, les annonces Web/Android, recherche/favoris/cache, l'estimation IA, les recommandations de logements et l'administration Web US29 sont raccordés dans le code. La Smart-Map Web est présente, avec une réserve sur le centrage d'une zone vide ; l'affichage Android du masquage doit être complété.

Le reste fonctionnel principal concerne la messagerie REST, les notifications métier, le swipe Android, la carte Android, les signalements Web et le boost payant utilisateur Web. La clôture US31 et la portée large d'US26 demandent également une mise au clair avant de déclarer tout le backlog terminé. Vidéo et reconstruction/visualisation 3D d'annonces demeurent des expériences facultatives. Aucun succès d'exécution nouveau n'est revendiqué par ce rapport.
