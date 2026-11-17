# Sprint 2 : périmètre vérifié

Sources : `Projet 2fastcoloc (1).pdf` (2 pages), `sprint0_pi.pdf` (12 pages,
notamment backlog pages 7–9), répartition DOCX, rapports 12–14 et contrats Sprint 1.

- US08–12 : offres et demandes, création par étapes, durée, édition et renouvellement.
- US13–19 : accueil public, détail connecté, filtres, Smart-Map Web.
- US33–34 : Android CRUD, filtres, favoris et cache Room.
- Favoris Web : répartition DOCX et instruction explicite de cette mission.
- Carte Android : US35, Sprint 3. Le tableau général DOCX prévoit cette fonction
  au total du projet ; le backlog donne son échéance. Aucun écran de carte Android
  ni swipe, messagerie, modération d'annonces, boost ou IA dans ce sprint.
- Rôles conservés : USER et ADMIN. Auteur = UUID du compte ; aucune nouvelle
  autorité « propriétaire ». ADMIN publie dans son espace personnel comme USER,
  sans obtenir le droit d'éditer les annonces d'autrui dans ces endpoints.
- Publication : e-mail confirmé, compte ACTIVE et inscription complète ; contrôle
  serveur et RLS. Brouillons et annonces expirées visibles seulement par leur auteur.
- Le visiteur découvre les annonces publiées ; détail et favoris exigent un compte.

## Choix techniques lorsque les sources ne donnent pas de valeur

Loyer en TND à trois décimales, capacité 1–50, équipements textuels (30 maximum),
coordonnées dans l'enveloppe tunisienne (latitude 30–38, longitude 7–12).
Durée choisie par l'auteur, borne technique configurable côté serveur (1–365 jours),
photos JPEG/PNG/WebP jusqu'à 5 MiB et 10 par annonce. Ces limites de protection ne
sont pas présentées comme des exigences chiffrées du cahier des charges.
Le renouvellement remet la date de publication à maintenant et recalcule l'expiration.
La modification normale ne renouvelle pas implicitement la visibilité.

`maquette` contient uniquement Sprint 1 : composants, typographies, couleurs et
motifs existants seront réutilisés pour les nouveaux parcours. Aucune maquette
Sprint 2 absente ne sera présentée comme fournie ou validée.

Le blocage Sprint 1 retire les opérations authentifiées. Le retrait administratif
des annonces relève d'US29 au Sprint 3 ; il n'est pas ajouté implicitement ici.

## Migration distante

Nouvelle migration additive générée avec Supabase CLI 3.2.0, sans rejeu historique.
Trois nouvelles tables privées avec RLS ; aucun droit Data API anon/authenticated.
Deux politiques SELECT additionnelles permettent au backend de lire les noms
de régions/localités sans identité utilisateur pour l'accueil public. Les anciennes
politiques et les données géographiques restent intactes ; aucun profil privé
ne devient lisible. Cette extension fait partie de la demande d'accord distante.
Les nouveaux droits métier du rôle runtime ont été présentés avec les résultats
des tests locaux, puis approuvés explicitement avant application distante,
conformément à l'instruction de la mission sur les permissions importantes.
Le résultat de cette application et sa vérification figurent dans le rapport 17.
