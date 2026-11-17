# FastColoc — refonte UI/UX, 17 novembre 2026

La palette officielle, les SVG de marque, Baloo 2 et DM Sans sont conservés. Aucune migration ni modification du backend ou des règles métier.

## Interface

- Accueil Angular reconstruit : composition photographique éditoriale, recherche région/budget/type reliée aux paramètres Sprint 2, trois annonces récentes réelles, favoris, skeletons, erreur avec réessai et état vide.
- Composant ListingCard partagé par accueil, catalogue, favoris et Mes annonces. Les photos éditoriales ne sont jamais utilisées comme photos de logements.
- Navigation Web responsive, menu personnel, surfaces, formulaires, calendrier natif, tableaux et dialogues communs. US29 garde ses routes, contrôles serveur, actions et historique.
- Android XML : DrawerLayout, NavigationView, Toolbar et AppBarConfiguration sur le NavController existant. Accueil, Explorer, favoris, Mes annonces, publication, profil et déconnexion. Aucune administration mobile.
- Accueil natif avec annonces réelles, RecyclerView/DiffUtil et accès rapides. Profil à sections dépliables ; confirmation après publication ; sélecteurs cohérents.
- MaterialDatePicker : affichage français, champs non saisissables manuellement, dates ISO pour les DTO, bornes entre début/fin et effacement des dates facultatives.

## Parcours et fluidité

- Retour Web à la destination protégée après connexion/OAuth, en conservant les critères. Inscription incomplète prioritaire ; reprise de destination après finalisation. Les destinations externes et les routes Auth sont refusées.
- Retour des fiches au catalogue d’origine ; restauration du défilement par le routeur. Conservation en mémoire des champs non enregistrés de l’éditeur, par compte et annonce, sans écraser une version serveur plus récente.
- Android : états propres aux listes, restauration de leur navigation, fermeture du Drawer après sélection, bouton Retour natif, suppression vers Mes annonces et confirmation après publication.
- La landing ne charge plus le catalogue complet ni Leaflet. Images de cartes différées, requête limitée à trois annonces, abonnements nettoyés, protection contre les doubles clics favoris.
- Coil réutilise les photos d’annonces en mémoire ; cache disque des URL privées toujours désactivé, mémoire vidée à la déconnexion. Les localités déjà chargées ne sont pas redemandées dans le même ViewModel.
- Les insets système/clavier sont appliqués à un FrameLayout extérieur : DrawerLayout ne respecte pas le padding comme un conteneur standard. Correction vérifiée sur téléphone pliable.

## Vérifications

- Angular : build production réussi ; 16 tests Web réussis.
- Android : assembleDebug réussi ; 10 tests unitaires existants réussis.
- Chrome : accueil à 320, 390, 768, 1024 et 1440 px sans débordement ; recherche transmise au catalogue ; menu mobile ; routes privées refusées aux visiteurs ; aucune erreur JavaScript.
- Téléphone réel : connexion effectuée par l’utilisateur, accueil, Drawer, sélection active, exploration, flèche de fiche, retour catalogue, profil dépliable et calendrier « Novembre 2026 » avec Annuler/Confirmer. Aucune publication, modification, suppression ou mutation ADMIN exécutée sur les données existantes.
- Avertissements de build Web non bloquants : style de la landing au-dessus du seuil d’avertissement de 4 kB (sous la limite de 8 kB), dépendance Leaflet CommonJS déjà présente.

## Démarrer depuis la racine

~~~powershell
.\backend\tools\start-backend.ps1 -Background
npm --prefix frontend start
~~~

Web : http://localhost:4200/ ; API : http://localhost:8001/api/v1/health

Android émulateur : compilation standard dans Android Studio, API par défaut http://10.0.2.2:8001/api/v1/.

Téléphone branché en USB :

~~~powershell
.\mobile\gradlew.bat -p mobile :app:assembleDebug -PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s RFCW7168JFP reverse tcp:8001 tcp:8001
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s RFCW7168JFP install -r mobile/app/build/outputs/apk/debug/app-debug.apk
~~~

Le maintien temporaire de l’écran éveillé a été désactivé après la recette ; la session Android existante et la redirection USB 8001 sont conservées.

Les sessions, mots de passe et secrets ne figurent pas dans les captures ou dans ce document. Les captures Android montrent uniquement les écrans de l’application et l’état système visible du téléphone.
