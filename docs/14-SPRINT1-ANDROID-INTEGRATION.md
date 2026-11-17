# FastColoc — intégration Android native du Sprint 1

Date : 16 novembre 2026. Périmètre : comptes communs, authentification, inscription,
profil, préférences, photos privées et conservation du Web/backend existants.
Aucune annonce, recherche, messagerie, IA, paiement ou fonctionnalité Sprint 2/3 ajoutée.
Aucune migration Supabase rejouée. Aucun vrai compte supprimé. Immovia non modifié.

## 1. Résultat et périmètre de validation

Android utilise maintenant Supabase Auth **via Spring Boot**, comme Angular.
L’identité est l’UUID retourné par le serveur. Le prototype SQLite ne permet plus
de créer un compte autonome ou de se connecter. Les écrans utiles, les couleurs,
les éléments de marque et le découpage en cinq étapes ont été repris.

Le code fournit connexion, inscription e-mail, confirmation/renvoi, récupération,
reset, Google PKCE, session persistante protégée, refresh sérialisé, déconnexion,
cinq étapes sauvegardées, accueil, profil et édition, préférences et photos privées.
Les validations réelles sont détaillées ci-dessous ; présence du code et réussite
d’une recette ne sont pas confondues avec réception d’un e-mail ou consentement Google humain.

## 2. Architecture finale

`Fragment XML/ViewBinding → Sprint1ViewModel/LiveData → AuthRepository → Retrofit → Spring → Supabase`.

`AuthRepository → ProfileCacheDao → Room` pour le résumé local non sensible.

- `AuthActivity` est le NavHost du parcours complet.
- `BoundFragment` initialise le binding dans `onCreateView` et le libère dans
  `onDestroyView`. Les vues observent avec `viewLifecycleOwner`.
- `Sprint1ViewModel` partage les états busy, données, feedback et événements de
  navigation ; il distingue session expirée, blocage et inscription incomplète.
- `AppContainer` est un singleton utilisant uniquement l’Application.
- `AuthRepository` coordonne l’API, le cache et la session. Un Mutex sérialise les
  rotations ; une requête refusée en 401 est retentée au plus une fois. Un refresh
  ne peut pas ressusciter une session effacée pendant l’appel.
- `SessionManager` et `SecureStore` conservent les tokens sous AES-GCM avec une clé
  Android Keystore ; les fichiers sont dans `noBackupFilesDir`. Aucune clé serveur
  Supabase ni secret Google dans l’application.

Les coroutines Retrofit sont suspendues, les DAO Room utilisent `suspend`, et la
lecture de fichiers photo est déportée sur `Dispatchers.IO`. Les LiveData sont
modifiées sur le thread principal après reprise des coroutines. Aucun réseau
bloquant dans les Fragments.

## 3. Composants conservés et remplacés

Conservés/adaptés : les cinq Fragments d’étape, le layout de connexion, les en-têtes
de progression, les ressources FastColoc, le symbole vectoriel, les fonds/styles,
les concepts Repository/ViewModel et le sélecteur d’images Android.

Remplacés : login/signup Room, anciennes sessions booléennes, formulaire géographique
codé en dur, champs divergents du contrat, photo unique et grille inactive.

Supprimés des sources actives : anciens modèles `User`/`AuthResult`/`RegistrationData`,
ancien `RegistrationViewModel`, `AuthViewModel` et factory, utilitaire de hash local,
classes vides `RegistrationContainerFragment` et `RegistrationPagerAdapter`.

L’administration expérimentale reste hors du graphe et sans capacité locale de
privilège. `SessionManager.getAccountType()` ne fournit aucun rôle local. Le rôle
ADMIN serveur n’est jamais une autorisation d’administration native : cette fonction
reste réservée au Web. `MainActivity` est seulement une entrée de compatibilité
redirigeant vers le NavHost.

## 4. Kotlin, XML et navigation

Classes principales nouvelles ou refondues sous `mobile/app/src/main/java/com/iset/fastcolloc/` :

- `data/remote/Contracts.kt`, `FastColocApi.kt`, `ApiClient.kt`.
- `data/AuthRepository.kt`, `SessionStore.kt`, `SessionManager.kt`, `SecureStore.kt`.
- `data/OAuthCoordinator.kt`, `AppContainer.kt`, `local/ProfileCache.kt`.
- `viewmodel/Sprint1ViewModel.kt`.
- `ui/auth/BoundFragment.kt`, `AuthActivity.kt`, `LoginFragment.kt`.
- `ui/auth/RegisterStep1Fragment.kt` à `RegisterStep5Fragment.kt`.
- `ui/auth/EmailFragment.kt`, `ResetFragment.kt`, `HomeFragment.kt`, `ProfileFragment.kt`.
- `ui/auth/PhotoAdapter.kt`, `JsonFields.kt`.

XML développés/adaptés : `activity_auth.xml`, `fragment_login.xml`, les cinq layouts
d’étape, `fragment_email.xml`, `fragment_reset.xml`, `fragment_home.xml`,
`fragment_profile.xml`, `item_photo.xml` et `navigation/nav_auth.xml`.

Le même NavController gère Auth, étapes, accueil et profil. Les sorties Auth/reset
nettoient la pile ; la modification d’une section revient au profil. Navigation Up
est liée à la toolbar. Les insets système et la fermeture du clavier au démarrage
d’une opération sont gérés par l’Activity.

## 5. Inscription et profil synchronisés

1. Identité : prénom, nom, genre facultatif, âge 0–130. E-mail et mot de passe
   seulement avant Auth ; ils ne sont pas redemandés à un utilisateur Google.
2. Adresse : pays/gouvernorat/localité chargés depuis Spring, rue et code postal.
3. Informations personnelles : activités, loisirs, nationalité, langues, religion,
   tabac nullable, propreté et présentation. Les informations facultatives le restent.
4. Coordonnées : mobile, résidentiel, disponibilités, MSN, Skype et site Web.
5. Photos : upload multipart, aperçu, grille RecyclerView, description, suppression,
   limites de cinq photos/5 MiB/JPEG-PNG-WebP, URL privées renouvelées, complétion serveur.

Les DTO correspondent à `StepRequests` ; les champs ne déterminent ni rôle ni statut.
Le mot de passe respecte le minimum de 12 caractères et la limite de 72 octets UTF-8.
La finalisation appelle l’étape 5 puis `/onboarding/complete`.

Après confirmation, `/users/sync` puis `/users/me/details` restaurent les étapes déjà
enregistrées. Les champs non sauvegardés ne sont pas annoncés comme persistants.
Le profil permet de rééditer chaque section et les préférences (budgets, date,
localités recherchées). Les localités proposées pour les préférences sont celles
du gouvernorat courant, comme dans le client Web existant.

## 6. Retrofit réellement utilisé

`FastColocApi` déclare les endpoints Auth, session, sync, détails, cinq étapes,
géographie, préférences et photos. `Contracts.kt` contient les DTO et les mappings
`@SerializedName` nécessaires pour les champs snake_case.

OkHttp applique des timeouts et ajoute le Bearer uniquement aux routes privées de
l’API. Il ne journalise aucun corps ou header Auth et ne suit pas les redirections
de ces requêtes. Les photos signées sont chargées séparément par Coil.

Les caches mémoire et disque de Coil sont désactivés pour les photos privées.
Les erreurs 400/401/403/409/413/429 et réseau sont traduites en feedback français.
Un compte bloqué purge la session ; un résumé Room n’accorde jamais un accès serveur.

## 7. Room réellement utilisé et migration

`ProfileCache` contient `ProfileSummary` (UUID, noms, état de complétion), sans token,
contact, mot de passe ou rôle d’autorité. `ProfileCacheDao.observe()` expose une
LiveData, `replace()` est transactionnel et le logout efface le cache.

La base de développement `fastcolloc_database` passe de 2 à 3 lorsqu’elle existe.
`MIGRATION_2_3` conserve les champs de profil et les identifiants, retire les colonnes
`passwordHash`/`passwordSalt`, et recrée l’index unique d’e-mail. Aucun fallback
destructif. La migration 1 → 2 est conservée pour les installations plus anciennes.
Le seed administrateur et ses identifiants codés en dur ont été retirés.

Une ancienne identité SQLite ne devient pas automatiquement une identité Supabase.
Les données de développement sont conservées comme telles, sans fusion d’identité
sur la seule base d’un e-mail.

## 8. Google OAuth : état exact

Le backend accepte `client=android` pour signup/recovery/resend/Google, avec un
callback natif fixe. Le comportement Web par défaut est conservé. Google Android
exige un state aléatoire ; le verifier/challenge PKCE S256 et le state sont conservés
chiffrés pendant au maximum dix minutes. Un code reçu est échangé via `/auth/exchange`.

L’application vérifie strictement scheme, host, path, port, state et durée du flux.
Les callbacks faux/expirés sont refusés avant l’échange.

L’utilisateur a confirmé l’ajout de `fastcoloc://auth/**` dans les Redirect URLs
Supabase, sans retrait des URL Web. Google Cloud conserve son callback HTTPS Supabase
existant ; aucun client secret Google n’est requis côté Android.

**Limite importante :** le callback utilise un custom scheme, pas une Android
Verified App Link avec preuve de propriété d’un domaine. Une telle association HTTPS
demande un domaine et `assetlinks.json`, non disponibles dans cette mission.

L’initiation réelle jusqu’à `accounts.google.com`, PKCE et les refus de callbacks
sont testés. Après installation sur son Samsung SM-F946B relié en USB, l’utilisateur
a confirmé que la connexion Google fonctionne. Cette confirmation humaine est
distincte de la preuve automatisée de redirection.

L’utilisateur a ensuite signalé un retour Web après confirmation e-mail. Un test
de capture de la requête Auth a reproduit la cause : `redirect_to` était encodé
deux fois par `RestClient.uri(String)`. `SupabaseGateway` utilise maintenant une URI
déjà construite ; un test couvre signup, récupération et renvoi pour Web et Android.
Les liens déjà reçus conservent leur ancienne destination : un nouveau lien est
nécessaire pour vérifier le correctif. Un compte déjà confirmé reste utilisable
par connexion normale ; aucune réinitialisation du compte n’est nécessaire.

## 9. Design

Palette conservée : #7138D8, #FF7954, #FFB9D6, #FFF6EF, #251532.
Baloo 2 ExtraBold 800 et DM Sans sont embarquées en TTF, avec leurs licences OFL.
Les fonts variables de référence ont été instanciées pour éviter une dépendance au
support des variations sur les anciennes versions Android.

Les styles, assets et les en-têtes du prototype ont été réutilisés. L’accueil reprend
son hero et affiche le vrai prénom ; il propose profil/reprise/logout, sans annonces
fictives. ScrollView, tailles minimales des boutons, labels, feedback accessible et
insets sont présents. Le rendu a été inspecté sur l’émulateur ; aucune fidélité
pixel par pixel sur toutes les résolutions n’est revendiquée.

## 10. Réseau et outillage

Port Spring : 8001. Debug configurable par `fastcolocApiUrl`.
Default émulateur : `http://10.0.2.2:8001/api/v1/`.
La recette Windows utilise `adb reverse tcp:8001 tcp:8001` avec
`-PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/`, après constat d’échec de la route
directe. Aucun changement de pare-feu.

Release : HTTPS seulement et propriété `fastcolocReleaseApiUrl` à fournir. Aucun
backend de production n’est inventé. Le manifest Release interdit le cleartext.

Le daemon Gradle utilise un JDK complet 21 : le JRE de l’extension VS Code sélectionné
auparavant ne possédait pas `jlink`. Le chemin SDK local a été normalisé pour corriger
l’erreur lint `PropertyEscape` ; `local.properties` reste ignoré par Git.

## 11. Tests et preuves

Résultats déjà obtenus pendant cette mission :

| Vérification | Résultat |
|---|---|
| Maven backend | 50 tests, zéro échec/erreur/ignoré |
| Angular Vitest | 14 tests réussis |
| Angular TypeScript | Réussi |
| Angular build production | Réussi |
| Playwright services réels | 6 scénarios réussis, dont administration et initiation Google |
| Android Debug et tests unitaires | Compilation réussie ; 7 tests, zéro échec |
| Android Lint | Réussi, zéro erreur ; avertissements résiduels non masqués |
| Recette instrumentée élargie | 5 tests réussis : parcours réel, récupération/reset, photos, migration, chiffrement et OAuth |
| Keystore et migration sur appareil | Deux tests réussis |
| OAuth natif | Tests de refus et redirection réelle fournisseur réussis |

La recette élargie comprend aussi modification de description, suppression et nouvel
upload de photo, refus sans JWT, retour accueil/profil, reset via un vrai hash Auth,
connexion Web avec le nouveau mot de passe et refus de l’ancien. Les deux fixtures
de chaque exécution sont nettoyées, photos comprises.

La recette élargie et son compteur final sont consignés dans `mobile/build/device-recipe.log`
et `backend/target/mobile-cross-report.json`. Le script exécutable
`mobile/tools/test-sprint1-device.ps1` exige explicitement un appareil, construit les
APK, lance les cinq tests instrumentés et nettoie ses fixtures en finally.

La confirmation automatisée génère, via Auth Admin, un lien pour la seule fixture
créée par le formulaire Android. Elle suit réellement `/verify`, vérifie la réponse
de redirection vers `fastcoloc://auth/callback`, puis fait traiter la session retournée
par le callback Android. Le rapport enregistre `emailVerificationRedirectsToAndroid=true`.
L’identité préconfirmation du même e-mail est conservée sans écraser une étape déjà
validée. Le reset utilise un vrai hash de récupération généré pour cette fixture.
Ces contrôles ne prouvent pas la réception SMTP dans une boîte humaine.
Les secrets opérateur restent côté Node sur le PC, en mémoire ; le bridge
est lié à 127.0.0.1 et son port temporaire passe par ADB.

Après la correction des liens e-mail, la recette complète a de nouveau réussi :
50 tests backend, 6 scénarios Web et 5 tests instrumentés. La redirection native
du lien de confirmation est attestée par le rapport de fixture. Après reconnexion
du Samsung SM-F946B, l’APK final a été installé avec succès et lancé sur le téléphone.
Le tunnel ADB `tcp:8001` a été rétabli ; le backend répond UP. Le build précédent
y avait permis la validation Google par l’utilisateur. Le clic humain sur un
nouveau lien e-mail reste à confirmer séparément.

Les tests backend de blocage, RLS et autorisations utilisent PostgreSQL/JWKS locaux.
Le compte ADMIN initial n’est pas modifié par la recette mobile. Le test Web existant
n’agit administrativement que sur sa propre fixture.

Tests unitaires Android : concurrence de refresh, token tournant, retry borné,
refresh refusé, blocage, limites UTF-8 et contrats JSON.
Tests appareil : migration conservatrice, chiffrement/altération, parcours natif
sur backend réel, refus de callbacks et initiation Google.

## 12. Compatibilité et tests non revendiqués

La recette ouvre un vrai navigateur Chrome isolé sur Angular. Elle y lit le profil
saisi sur Android, modifie le nom avec le formulaire Web et vérifie son rechargement
sur Android. Elle se connecte aussi dans Angular avec le compte créé sur mobile,
puis avec le nouveau mot de passe défini sur mobile. Les UUID retournés sont comparés.
Le client Angular conserve en plus ses six scénarios E2E réels.

Non revendiqués : réception humaine et validation du nouveau lien e-mail après le
correctif de redirection, déploiement Release, certification sur tous les appareils,
test de destruction forcée du processus à chaque état, audit universel des secrets.

Les avertissements lint résiduels concernent principalement ressources héritées
inutilisées, surdessin et versions/catalogue de dépendances. Aucun baseline ne masque
les erreurs. Le scan `mobile/tools/check-secrets.py` compare les valeurs serveur
configurées aux sources et aux entrées décompressées de l’APK, sans afficher ces valeurs.
Dernier résultat : 0 erreur lint, 112 avertissements ; 1 204 entrées source/APK
contrôlées sans valeur de secret serveur configuré détectée.

## 13. Matrice pédagogique

| Notion du cours/TP | Fichier réel | Usage |
|---|---|---|
| Kotlin natif | `data/remote/Contracts.kt` | Data classes et DTO typés |
| Activity/cycle de vie | `ui/auth/AuthActivity.kt` | NavHost, callbacks, insets, clavier |
| XML/ConstraintLayout | `res/layout/activity_auth.xml` | Contraintes toolbar/progress/host |
| LinearLayout/ressources | Cinq layouts d’étape, `strings_sprint1.xml` | Formulaires, labels, styles |
| Fragment | `RegisterStep1Fragment` à `RegisterStep5Fragment` | Une vue par étape |
| ViewBinding | `BoundFragment.kt` et bindings générés | Init/libération suivant le cycle de la vue |
| Navigation | `res/navigation/nav_auth.xml`, `AuthActivity` | Destinations, actions, Up et pile |
| ViewModel partagé | `viewmodel/Sprint1ViewModel.kt` | `activityViewModels`, état indépendant des vues |
| LiveData | `Sprint1ViewModel`, `ProfileCacheDao.observe()` | Données, erreurs, chargement et cache observables |
| Repository | `data/AuthRepository.kt` | Coordination réseau/session/cache |
| Room/SQLite | `data/local/ProfileCache.kt`, `AppDatabase.kt` | Cache et migration non destructive des profils |
| Entity | `ProfileSummary`, `UserEntity` | Tables locales sans credentials |
| DAO | `ProfileCacheDao`, `UserDao` | Requêtes suspendues et LiveData |
| Coroutines | `Sprint1ViewModel`, `AuthRepository` | `viewModelScope`, `suspend`, IO, Mutex |
| Retrofit/Gson | `FastColocApi.kt`, `ApiClient.kt` | Endpoints REST et sérialisation |
| RecyclerView/Adapter/ViewHolder | `ui/auth/PhotoAdapter.kt` | Liste dynamique de photos privées |
| GridLayoutManager | `RegisterStep5Fragment.kt` | Grille de deux colonnes |

Références pédagogiques réellement consultées : passages des chapitres 5, 6 et 7,
TP6 SQLite/Room et TP7 Retrofit, présents dans `reference/`. Les autres chapitres
ont été inventoriés et leurs passages pertinents examinés par extraction. Les exemples
de journalisation complète des TP ne sont pas repris pour les corps d’authentification.

## 14. Lancement et livrables

Voir `mobile/README.md` pour Android Studio, JDK, réseau et commandes.

- APK : `mobile/app/build/outputs/apk/debug/app-debug.apk`.
- Tests : `mobile/app/src/test/` et `mobile/app/src/androidTest/`.
- Rapports Android : `mobile/app/build/reports/` et `mobile/build/`.
- Rapport de fixtures : `backend/target/mobile-cross-report.json`.
- Preuves Web : `frontend/test-results/results.json`.
- Preuves backend : `backend/target/surefire-reports/`.

## 15. Interventions externes

Redirect URL Android et Google sur téléphone : confirmés par l’utilisateur. Restent
la recette d’un nouveau lien e-mail avec une adresse contrôlée et, pour production,
URL HTTPS du backend, association App Links vérifiée et paramètres SMTP/production.
Aucun affaiblissement RLS, reset Supabase ou migration distante effectué.

## 16. Références techniques vérifiées

- [Android ViewBinding](https://developer.android.com/topic/libraries/view-binding).
- [Android cryptographie et Keystore](https://developer.android.com/privacy-and-security/cryptography).
- [Supabase PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow).
- [Supabase mobile deep linking](https://supabase.com/docs/guides/auth/native-mobile-deep-linking).

Le changelog Supabase a été consulté avant l’intégration. Les appels des services
privés restent derrière Spring ; aucun accès direct aux tables depuis Android.
