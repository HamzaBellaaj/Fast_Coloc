# Architecture recommandée

## Flux

Angular / Android → Supabase Auth pour identité et session.
Angular / Android → Spring REST avec access token → PostgreSQL FastColoc.
Spring → Supabase Storage pour upload contrôlé et URL signée.
Aucun accès direct des clients aux tables métier. Cette décision simplifie le blocage immédiat et centralise la validation.

Même UUID Supabase sur Web/Mobile, sessions et refresh tokens propres à chaque appareil. Ne jamais envoyer refresh tokens au backend métier.

## Backend

Package `tn.fastcoloc`, modules security, profiles, onboarding, geography, preferences, media, admin. Chaque module possède controller, service, dto et repository selon besoin. Contrôleurs légers, transactions dans services, DTO allowlist, erreurs JSON avec code métier stable, JPA ddl-auto=validate. Pas de génération de JWT ni gestion locale des mots de passe.

Compte JDBC dédié à privilèges minimaux; ne pas employer le propriétaire postgres en runtime. Les accès JPA ne bénéficient pas automatiquement des politiques auth.uid() des clients Supabase : autorisation via sub/roles/status explicitement dans l'API. Compte migration séparé. Pas de BYPASSRLS gratuit pour le compte runtime; sa stratégie RLS sera testée localement avant application.

## Angular

core/auth (SDK/session/interceptor/guards), core/api, shared/ui, features/auth, onboarding, profile et admin. Les formulaires réactifs des cinq étapes partagent un état éphémère; aucune persistance du mot de passe dans localStorage, logs ou brouillon. Guard de complétion distinct du guard de session. Administration Web seulement; les règles serveur restent nécessaires.

## Android strict MVVM

presentation/auth : Fragments XML, Navigation graph, AuthViewModel/OnboardingViewModel, UiState en StateFlow.
domain : modèles, interface AuthRepository et cas d'usage ciblés.
data : AuthRepositoryImpl, SessionManager, AuthRemoteDataSource Retrofit pour Supabase Auth, ProfileRemoteDataSource Retrofit pour Spring, DAO Room de cache non sensible.

Fragment → ViewModel → Repository → source distante/locale. Aucun appel Retrofit, token ou SQL dans les Fragments. ViewModel ne conserve pas Activity/Fragment. Coroutines viewModelScope, collecte repeatOnLifecycle, erreurs/loading explicites, DTO réseau mappés en modèles domaine. ViewBinding libéré en onDestroyView.

Un seul SessionManager contrôle refresh/rotation avec exclusion mutuelle; retry d'une requête au plus une fois sur expiration, aucun retry de login incorrect. Secrets de session chiffrés avec clé Android Keystore et stockage privé; exclus des sauvegardes et de Room, jamais BuildConfig pour un secret serveur. Room ne cache pas le rôle ni le blocage comme autorité. OAuth dans navigateur système/Custom Tabs, pas WebView; PKCE verifier et state liés au parcours, callbacks strictement validés.

## Dépendances à installer après feu vert Supabase

| Cible | Dépendances |
|---|---|
| Backend Java 21 | Spring Web, Security, OAuth2 Resource Server, Data JPA, Validation, PostgreSQL, Flyway core + PostgreSQL, springdoc dev, test/security-test et Testcontainers PostgreSQL |
| Angular 21 | router/forms/common/HttpClient, RxJS, supabase-js; tests unitaires Angular et Playwright |
| Android | AndroidX core/appcompat, Fragment KTX, Navigation Fragment/UI KTX, Lifecycle ViewModel/runtime KTX, coroutines, Retrofit + convertisseur JSON, OkHttp, Room + KSP, DataStore, Browser Custom Tabs, JUnit, MockWebServer, tests instrumentés |

Ne pas ajouter simultanément deux gestionnaires de session Android (SDK Supabase + refresh Retrofit indépendant). Le choix proposé ici satisfait Retrofit/AuthRepository explicitement; vérifier le contrat REST Auth officiel avant codage. Versions stables à figer ensemble dans lockfiles/catalogue; réutiliser le couple AGP 9.3.3/Gradle 9.5 et le JDK 21 du test local s'il reste adapté, KSP à aligner sur le Kotlin intégré d'AGP. Aucun JDK installé dans cette mission.

## Design SOCIAL POP

Tokens communs : primary #7455F6, accent #FF7898, highlight #FFC857, surface #F7F4FF, ink #222039. Décliner dans variables CSS et colors.xml. Cartes arrondies, hiérarchie typographique affirmée, photos utiles, états chargement/vide/erreur, contrastes vérifiés (notamment jaune/corail pour texte), focus et TalkBack, animations courtes respectant la réduction des mouvements. Aucune image ni écran métier créé à ce stade.

## Répartition ferme

Web : administration, modération/signalements et boost payant. Android : swipe uniquement, messagerie REST et refresh. Le serveur impose les permissions; masquer un bouton n'est pas une autorisation. Une session d'administrateur seule ne prouve pas la plateforme cliente : si une exclusivité technique Web est exigée, prévoir un canal admin séparé et authentifié (BFF/session dédiée) plutôt qu'un simple en-tête client falsifiable.

Recommandations et estimations IA côté serveur; n8n via appels serveur authentifiés et secrets serveur. Vidéo Web et prototype 3D hors chemin critique et hors des trois sprints principaux.
## Alignement après lecture des sources officielles du projet

Le cahier des charges et Sprint 0 sont maintenant présents et lus dans docs, avec la répartition Word. Toute mention antérieure d'absence ou d'étapes provisoires est remplacée par docs/06-MODELE-DONNEES.md : 1 identité, 2 géographie, 3 informations personnelles, 4 coordonnées, 5 images. Budget et recherche ne constituent pas les étapes 3 et 4.

Sprint 1 officiel : US01–US07 + US32, 38 points / deux semaines. L'administration Angular inclut liste paginée, modification de coordonnées, blocage/déblocage ET suppression. Prévoir GET /admin/users, PATCH /admin/users/{id}/contacts, PATCH /admin/users/{id}/status et DELETE /admin/users/{id}; autorisation ADMIN et journal pour chaque mutation. Suppression : bloquer d'abord, puis nettoyer Storage/Auth via API officielle avec reprise sur échec; aucune clé serveur dans les clients.

Le mobile démarre au Sprint 1 avec MVVM/Retrofit/Room/session. Swipe sur annonces seulement (gauche passer/droite intéressé), pas de swipe de recommandations ni matching complexe. Vidéo : lecture possible Android, expérimentation de génération Web; aucune 3D Android. Messagerie GET/POST avec refresh, sans WebSocket. Leaflet/OpenStreetMap Web et carte Android simple. Spring Boot unique modulaire; IA/n8n/paiement isolés derrière interfaces. Vidéo/3D hors acceptation des trois sprints.

Les règles photos (nombre/taille), âge minimum, taxonomies neutres et remplacement éventuel MSN/Skype restent à confirmer. Nouveau Supabase et paramètres indépendants restent requis avant implémentation métier.
