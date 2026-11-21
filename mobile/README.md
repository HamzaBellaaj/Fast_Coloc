# FastColoc Android — Sprints 1 et 2

Depuis l'accueil, « Trouver ma coloc » ouvre le catalogue commun. Le menu propose
recherche, filtres, annonces personnelles et favoris. Création/modification en trois
étapes, galerie, publication/renouvellement et suppression passent par Spring Boot.
Room garde uniquement les copies téléchargées, isolées par compte et effacées au logout.
La carte Android relève du Sprint 3 dans le backlog validé.

Recette : **10 tests JVM et 6 tests instrumentés** exécutés sur émulateur, dont les
5 régressions Sprint 1. Commande : `tools/test-sprint2-device.ps1` avec backend et
Angular actifs ; le pont de recette reste sur loopback et nettoie ses propres comptes.
Voir [rapport et matrice pédagogique](../docs/17-SPRINT2-ANNONCES-RECHERCHE.md) et
[inventaire des cours/TP](../docs/16-SPRINT2-REFERENCES-ANDROID.md).

Kotlin natif, XML et ViewBinding, Fragments/Navigation Component, MVVM/LiveData,
Retrofit/Gson/OkHttp et Room. Les comptes sont ceux de Supabase Auth, via Spring Boot.
La base locale du prototype ne permet plus de se connecter.

## Ouvrir et lancer

Ouvrir **ce dossier `mobile`** dans Android Studio. Utiliser un **JDK complet 21**
(avec `jlink`), le SDK 37 et laisser Gradle synchroniser les dépendances. Le wrapper
est fourni. Ne pas sélectionner le JRE embarqué de l’extension Java de VS Code.

Depuis `backend`, démarrer le backend déjà configuré :

```powershell
.\tools\start-backend.ps1 -Background
```

Ne pas écraser `backend/.env.local`. Aucun secret Supabase n’est requis dans Android.

Configuration Debug par défaut : `http://10.0.2.2:8001/api/v1/` pour l’émulateur.
Dans cet environnement Windows, la recette a utilisé le tunnel ADB, car l’accès
direct à `10.0.2.2` n’aboutissait pas. Aucun pare-feu n’a été assoupli.

Pour le tunnel, depuis `mobile` :

```powershell
adb -s emulator-5554 reverse tcp:8001 tcp:8001
.\gradlew.bat :app:assembleDebug -PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/
```

Pour un téléphone USB, remplacer le numéro de série par celui de `adb devices`.
Le même tunnel fonctionne sans adresse IP personnelle dans le code. Pour Android
Studio, mettre `fastcolocApiUrl=http://127.0.0.1:8001/api/v1/` dans les propriétés
Gradle locales, puis synchroniser, et garder le tunnel ADB actif.

Alternative Wi-Fi : fournir `-PfastcolocApiUrl=http://ADRESSE_DU_PC:8001/api/v1/`
avec une adresse joignable et une configuration réseau appropriée.

Release : définir `-PfastcolocReleaseApiUrl=https://VOTRE_BACKEND/api/v1/`.
L’URL Release par défaut est volontairement un domaine `.invalid` ; aucun backend
public de production n’a été déployé. HTTP est désactivé dans le manifest Release.

APK Debug : `app/build/outputs/apk/debug/app-debug.apk`.

## Parcours

- Connexion au même compte que sur Angular.
- Inscription e-mail, attente de confirmation, puis connexion et reprise des étapes.
- Google dans le navigateur système, PKCE et retour natif.
- Cinq étapes conformes aux DTO Spring : identité, adresse, informations personnelles,
  coordonnées, photos.
- Accueil, profil, modification de chaque section, préférences et photos privées.
- Réinitialisation par lien Auth vérifié ; déconnexion et refresh sérialisé.

Les champs enregistrés sont rechargés depuis Spring. Les champs non enregistrés,
notamment l’identité avant confirmation, ne constituent pas un brouillon serveur.
Les mots de passe ne sont pas sauvegardés par Android, Room ou SharedPreferences.

## Google et liens e-mail

Supabase doit autoriser `fastcoloc://auth/**` dans ses Redirect URLs, en conservant
les redirections Web. Cette opération a été confirmée par l’utilisateur pendant
la mission. Google Cloud continue de rediriger vers le callback HTTPS Supabase
existant ; aucun secret Google n’est embarqué dans l’APK.

Les routes Auth acceptent `client=android` ; sans paramètre, le contrat Web reste
inchangé. Le backend choisit un callback fixe : aucun `redirect_uri` arbitraire
n’est accepté. Google exige également un `state` aléatoire.

Le callback natif vérifie scheme/host/path, state, âge du flux et verifier PKCE.
Il utilise un custom scheme. Ce n’est **pas** une Android Verified App Link liée
à un domaine HTTPS. Un domaine et son `assetlinks.json` devront être configurés
pour une association HTTPS vérifiée en production.

L’initiation vers Google et les refus de callbacks sont testables automatiquement.
Le choix d’un compte et le consentement Google ont été confirmés par l’utilisateur
sur son téléphone. Le retour e-mail vers le Web a ensuite été corrigé côté Spring
(double encodage de `redirect_to`) et vérifié avec un vrai lien Supabase sur fixture.
Les anciens mails gardent leur ancienne destination ; un nouveau lien est nécessaire
pour retester depuis une messagerie. Un compte déjà confirmé peut se connecter
directement. La réception d’un nouveau mail et son ouverture manuelle restent
distinctes de la recette automatique.

## Sécurité et Room

Les sessions et le flux OAuth sont chiffrés avec AES-GCM et une clé Android Keystore,
dans `noBackupFilesDir`. Les sauvegardes de l’application sont désactivées.
Aucun token n’est stocké dans Room. Les requêtes et corps Auth ne sont pas journalisés.

`ProfileCache` contient uniquement un résumé de profil provenant du serveur,
observable avec LiveData. Ce résumé n’accorde aucun droit hors ligne.

La migration locale 2 → 3 conserve les données de profil du prototype et retire
les colonnes de hash/sel. Elle ne transforme pas les anciens comptes en comptes
Supabase, ne réinitialise pas les données et n’utilise aucun fallback destructif.
Le compte administrateur prédéfini n’est plus créé. L’administration expérimentale
conservée dans les sources n’est plus accessible dans la navigation et ne peut
obtenir de privilège local via `SessionManager`.

## Vérifier

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Recette instrumentée réelle, backend et Angular démarrés, configuration serveur locale
disponible, uniquement sur l’appareil explicitement sélectionné :

```powershell
.\tools\test-sprint1-device.ps1 -DeviceSerial emulator-5554
```

Cette recette crée des fixtures identifiables dans Supabase. Le bridge opérateur
reste sur la boucle locale du PC ; ses identifiants ne passent pas dans les arguments
de commande ni dans des fichiers de fixture. Les données de connexion temporaires
restent en mémoire. Le script nettoie uniquement ses comptes et leurs photos.
Le compte ADMIN initial n’est jamais ciblé. La recette utilise aussi un navigateur
Chrome isolé pour lire dans Angular les données saisies sur Android, modifier le
profil Web et vérifier les connexions du compte créé sur mobile.

Preuves : `build/device-recipe.log`, `build/sprint1-verification.log`,
`app/build/reports/`, `../backend/target/mobile-cross-report.json`.
Les tests live sont conditionnés à `fastcolocLive=true` pour empêcher une mutation
distante lors d’une exécution de tests ordinaires.

Rapport détaillé et matrice pédagogique : `../docs/14-SPRINT1-ANDROID-INTEGRATION.md`.

## US25 — Estimation du loyer

Le formulaire XML de création/modification d’une offre propose « ✨ Estimer mon
loyer avec l’IA » aux étapes loyer et équipements/photos. Le sélecteur permet
d’ajouter plusieurs photos à la fois, jusqu’à 10 au total, même avant
l’enregistrement. Les aperçus en attente peuvent être supprimés. Le ViewModel lit les URI `content://` via
`ContentResolver`, puis Retrofit envoie toutes les photos et les caractéristiques
à `POST ai/rent-estimate` sur Spring Boot, avec l’authentification existante.
Les photos en attente sont ajoutées successivement au prochain enregistrement.
Pour une annonce déjà enregistrée, l’ajout multiple déclenche leur téléversement.
Après chaque réussite, la photo quitte la file : une reprise après erreur ne la
duplique pas. L’estimation inclut les photos déjà enregistrées et celles en attente.
Spring réunit toutes les vues dans une planche numérotée, car le workflow existant
analyse `file0`. Aucune image sélectionnée n’est silencieusement ignorée.
« Utiliser ce prix » remplit le loyer ; « Ignorer » conserve le montant actuel.

Vérifications US25 : `gradlew.bat :app:assembleDebug :app:lintDebug` réussies.
Le parcours US25 n’a pas été exécuté sur téléphone/émulateur pendant cette recette.
L’appel réel Spring → n8n a réussi avec plusieurs vues en environ 37 secondes.
Le test JVM `RentEstimateMultipartTest` vérifie l’envoi de trois photos en une
seule requête Retrofit. Aucun résultat fictif n’est utilisé dans l’application.
Pour le téléphone USB avec tunnel déjà présent :
`gradlew.bat :app:assembleDebug -PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/`.

## US26 — Pour toi ✨

L’entrée Pour toi est disponible dans la barre inférieure et le menu latéral.
`RecommendationsFragment` utilise `RecommendationViewModel`, le Repository et
Retrofit vers Spring Boot. Le classement reste en mémoire pendant la visite :
rotation et retour des détails ne relancent pas l’IA. Actualiser déclenche un
nouveau calcul ; les clics multiples sont bloqués. Aucun polling ni tâche de fond.

`ListingAdapter` et son layout sont réutilisés, avec score/raison visibles sur
cet écran, consultation et favoris. Les favoris modifiés dans les détails sont
répercutés au retour. Un profil incomplet reçoit la sélection possible et un lien
discret vers le profil. Chargement, erreur avec reprise et liste vide sont prévus.

Compilation US26 réussie avec `gradlew.bat :app:assembleDebug
-PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/`. Le parcours n’a pas été exécuté
sur appareil pendant cette recette. L’appel réel de l’endpoint Spring vers n8n
a réussi (voir le rapport dans `../backend/target/recommendations-live-report.json`).
