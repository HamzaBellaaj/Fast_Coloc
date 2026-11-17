> État au 17 novembre 2026 : Sprints 1 et 2 intégrés sur Spring Boot, Angular et Android natif. Offres/demandes, publication, recherche, photos et favoris utilisent les mêmes comptes et API. [Rapport Sprint 2 et démarrage](docs/17-SPRINT2-ANNONCES-RECHERCHE.md), [périmètre](docs/15-SPRINT2-PERIMETRE.md), [cours Android et conformité](docs/16-SPRINT2-REFERENCES-ANDROID.md). L'intégration Sprint 1 est conservée dans le [rapport Android initial](docs/14-SPRINT1-ANDROID-INTEGRATION.md).
> US29 est également intégrée sur le Web : gestion administrative des annonces, correction, masquage/réactivation, suppression et historique. [Documentation US29](docs/18-US29-GESTION-ADMIN-ANNONCES.md). La correction des contacts ADMIN charge désormais les valeurs existantes dans un dialogue responsive.
>
> État historique au 12 novembre 2026 : backend opérationnel et application Angular Sprint 1 implémentée. Lancement et recette : [frontend/README.md](frontend/README.md), [rapport Web](docs/13-SPRINT1-WEB-FINALISATION.md) et [rapport backend](docs/12-SPRINT1-OPERATIONNEL.md). Le cadrage ci-dessous est historique ; ne pas rejouer les migrations locales.
# Archive du cadrage initial — Sprint 1

État au 11 novembre 2026 : audit et architecture préparés; aucune implémentation métier, aucun déploiement, aucune connexion distante. Immovia sert exclusivement de référence en lecture seule. Aucun secret ni identifiant de son infrastructure n'est copié.

## Dossiers

- `backend/` : API Spring Boot Java 21; future organisation par modules auth/profiles/geography/preferences/media/admin et couches controller/service/repository/dto/security.
- `frontend/` : Angular 21 standalone, formulaires réactifs, core/shared/features et design SOCIAL POP.
- `mobile/` : Kotlin XML/ViewBinding, Fragments + Navigation, MVVM strict; les sous-dossiers de couches seront créés avec les premiers modules.
- `docs/` : décisions, audit, contrats proposés, configuration et recette.
- `FastColocBackSpring/`, `FastColocFrontWeb/`, `FastColocFrontMobile/` : dossiers préexistants constatés vides, conservés tels quels. Les dossiers normalisés ci-dessus sont les cibles proposées; ne pas créer deux applications concurrentes.

Ces dossiers sont des structures de préparation, pas des applications compilables. Aucun package.json, pom.xml ou projet Gradle métier n'a été généré. Les dossiers vides ne seront pas suivis par Git; chaque composant contient un README de cadrage.

## Documents

1. [Audit Immovia et réutilisation](docs/01-AUDIT-IMMOVIA.md)
2. [Architecture et dépendances](docs/02-ARCHITECTURE.md)
3. [Authentification et contrats](docs/03-AUTHENTIFICATION.md)
4. [Configuration Supabase](docs/04-SUPABASE.md)
5. [Plan et recette Sprint 1](docs/05-SPRINT1.md)
6. [Modèle et décisions ouvertes](docs/06-MODELE-DONNEES.md)

Le fichier SQL `backend/src/main/resources/db/drafts/V001__sprint1_proposal.sql` est un **brouillon à relire**, volontairement hors de `db/migration`. Il n'a pas été exécuté ni validé sur PostgreSQL.

## Conditions avant implémentation

- Cahier des charges lu dans docs; confirmer les limites encore ouvertes.
- Créer le nouveau projet Supabase indépendant et fournir sa configuration publique.
- Valider le modèle SQL, les droits et le parcours Google/confirmation multiclient.
- Les mots de passe DB et secrets serveur restent uniquement dans une configuration locale ignorée.
## Alignement après lecture des sources officielles du projet

Le cahier des charges et Sprint 0 sont maintenant présents et lus dans docs, avec la répartition Word. Toute mention antérieure d'absence ou d'étapes provisoires est remplacée par docs/06-MODELE-DONNEES.md : 1 identité, 2 géographie, 3 informations personnelles, 4 coordonnées, 5 images. Budget et recherche ne constituent pas les étapes 3 et 4.

Sprint 1 officiel : US01–US07 + US32, 38 points / deux semaines. L'administration Angular inclut liste paginée, modification de coordonnées, blocage/déblocage ET suppression. Prévoir GET /admin/users, PATCH /admin/users/{id}/contacts, PATCH /admin/users/{id}/status et DELETE /admin/users/{id}; autorisation ADMIN et journal pour chaque mutation. Suppression : bloquer d'abord, puis nettoyer Storage/Auth via API officielle avec reprise sur échec; aucune clé serveur dans les clients.

Le mobile démarre au Sprint 1 avec MVVM/Retrofit/Room/session. Swipe sur annonces seulement (gauche passer/droite intéressé), pas de swipe de recommandations ni matching complexe. Vidéo : lecture possible Android, expérimentation de génération Web; aucune 3D Android. Messagerie GET/POST avec refresh, sans WebSocket. Leaflet/OpenStreetMap Web et carte Android simple. Spring Boot unique modulaire; IA/n8n/paiement isolés derrière interfaces. Vidéo/3D hors acceptation des trois sprints.

Les règles photos (nombre/taille), âge minimum, taxonomies neutres et remplacement éventuel MSN/Skype restent à confirmer. Nouveau Supabase et paramètres indépendants restent requis avant implémentation métier.
