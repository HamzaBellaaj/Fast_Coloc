# Annonces de démonstration

Le script `backend/tools/seed-demo-listings.cjs` prépare 24 annonces publiées :
12 offres de logements déjà occupés cherchant des colocataires et 12 demandes
de personnes cherchant un logement. Huit profils fictifs se partagent les annonces.
Les budgets, capacités, équipements et localités varient ; les offres contiennent
trois photos et les demandes deux illustrations du logement recherché.

Avec le backend actif sur le port 8001, depuis la racine du projet :

```powershell
node backend/tools/seed-demo-listings.cjs
```

Le script passe par les API réelles : création de comptes de démonstration,
onboarding, création des annonces, upload privé des photos et publication.
Il ne modifie pas les comptes ou annonces préexistants.
Les annonces précisent leur nature fictive et les photos leur nature illustrative.
Les coordonnées des offres sont des positions approximatives dans les villes,
pas des adresses de logements réels.

La progression et les identifiants des comptes de démonstration restent dans
`backend/target/demo-listings/progress.json`, ignoré par Git. Conserver ce fichier
permet de reprendre une exécution interrompue sans recréer les annonces.
Ne pas partager ce fichier ni l'intégrer aux clients Web ou Android.
Les annonces expirent 90 jours après publication ; le script ne renouvelle pas
automatiquement des annonces déjà publiées.

Le rapport `backend/target/demo-listings/report.json` est écrit après vérification
du statut, de l'auteur, du nombre de photos et de l'accès à la première image
de chaque annonce via son URL signée.

Les images proviennent d'Unsplash (`images.unsplash.com`) ; les identifiants sources
figurent dans le script. Elles sont téléchargées puis envoyées au stockage de
l'application, sans dépendance à un service d'images aléatoires à l'affichage.
Voir la [licence Unsplash](https://unsplash.com/license).
