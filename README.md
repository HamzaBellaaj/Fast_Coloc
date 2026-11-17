<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 580 160"><rect width="580" height="160" fill="none"/><text x="22" y="112" font-family="Baloo 2,Arial Rounded MT Bold,sans-serif" font-size="102" font-weight="900" fill="#251532">fast</text><text x="198" y="112" font-family="Baloo 2,Arial Rounded MT Bold,sans-serif" font-size="102" font-weight="900" fill="#FF7954">coloc</text><path d="M285 39 343 6l58 33" stroke="#7138D8" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" fill="none"/><path d="M365 131q74 6 152-5" stroke="#FF7954" stroke-width="9" stroke-linecap="round" fill="none"/></svg>stColoc

### Find your people. Find your place.

**FastColoc** est une plateforme intelligente de colocation qui facilite la recherche de logements partagés et la mise en relation entre colocataires en Tunisie.

Conçue pour les étudiants et les jeunes actifs, elle propose une expérience **Web et Android native**, réunissant publication d'annonces, recherche multicritère, cartographie et gestion des profils au sein d'un même écosystème.

Une seule plateforme. Deux applications. Un même compte.

---

## ✨ Une nouvelle façon de trouver sa coloc

Trouver une colocation ne devrait pas être compliqué.

FastColoc simplifie le parcours en permettant à chaque utilisateur de :

- **Proposer un logement** : publier une chambre, un appartement ou une place en colocation.
- **Rechercher une colocation** : exprimer ses besoins, son budget et ses préférences.
- **Explorer les annonces** : filtrer par localisation, loyer, type de logement et équipements.
- **Découvrir les logements sur une carte** : visualiser géographiquement les offres disponibles.
- **Gérer ses annonces et favoris** : depuis le Web ou Android, avec synchronisation des données.

L'objectif est de rendre la recherche de colocation plus simple, accessible et agréable.

## 🚀 Fonctionnalités

### 🔐 Authentification et profils — Sprint 1

- Inscription guidée en cinq étapes.
- Connexion par e-mail et mot de passe.
- Authentification avec Google.
- Vérification de l'adresse e-mail.
- Récupération et réinitialisation du mot de passe.
- Consultation et modification du profil.
- Gestion des photos de profil.
- Administration des comptes utilisateurs sur le Web.

### 🏠 Annonces et recherche — Sprint 2

- Publication d'offres et de demandes de colocation.
- Sauvegarde des brouillons.
- Ajout et gestion des photos.
- Modification, publication et suppression des annonces.
- Durée de publication et renouvellement.
- Catalogue d'annonces.
- Recherche multicritère et filtres combinables.
- Tri et pagination.
- Gestion des favoris.
- Smart-Map Web avec **Leaflet et OpenStreetMap**.
- Synchronisation des annonces et favoris entre Angular et Android.
- Cache local Android avec Room pour les contenus déjà consultés.

### 🛡️ Administration Web — US29

- Consultation des annonces de tous les utilisateurs.
- Recherche et filtres administratifs.
- Correction des informations d'une annonce.
- Masquage et réactivation.
- Suppression administrative.
- Historique des actions de modération.
- Contrôle d'accès réservé au rôle ADMIN.

L'administration est exclusivement disponible sur le Web.

## 🛠️ Stack technique

| Couche | Technologies |
|---|---|
| Frontend Web | Angular 21, TypeScript, HTML, CSS |
| Application mobile | Kotlin, Android XML, ViewBinding |
| Architecture Android | MVVM, Fragments, Navigation Component |
| Réseau Android | Retrofit, OkHttp, Coroutines |
| Stockage local Android | Room |
| Backend | Java 21, Spring Boot, Spring Security |
| API | REST, OpenAPI / Swagger |
| Base de données | PostgreSQL — Supabase |
| Authentification | Supabase Auth, JWT, Google OAuth |
| Stockage des images | Supabase Storage |
| Cartographie Web | Leaflet, OpenStreetMap |
| Tests et vérification | JUnit, Playwright, Postman, Newman, Gradle |
| Outils | Git, GitHub, Maven, Android SDK |

## 🏗️ Architecture

FastColoc repose sur une architecture client–serveur avec une API centralisée.

```text
                  FASTCOLOC
                      |
          +-----------+-----------+
          |                       |
     Angular Web             Android Kotlin
          |                       |
          +-----------+-----------+
                      |
                 REST API
                      |
            Spring Boot / Java
                      |
          +-----------+-----------+
          |           |           |
      Supabase     Supabase    Supabase
      Auth         PostgreSQL  Storage
```

Les applications Web et Android consomment la même API Spring Boot et partagent les mêmes comptes et données.

Le backend centralise les règles métier, les validations, les autorisations et l'accès aux ressources.

### Organisation du projet

```text
FastColoc/
├── backend/       # API Spring Boot, sécurité, services et données
├── frontend/      # Application Web Angular
├── mobile/        # Application Android Kotlin native
├── docs/          # Architecture, sprints et documentation technique
├── maquette/      # Références UI/UX et identité visuelle
├── references/    # Ressources pédagogiques et techniques
└── README.md
```

## 🎨 Design & identité visuelle

FastColoc adopte une direction artistique **Social Pop** : une identité jeune, chaleureuse et expressive, pensée pour les étudiants et les jeunes actifs.

| Élément | Valeur |
|---|---|
| Violet principal | `#7138D8` |
| Corail | `#FF7954` |
| Rose | `#FFB9D6` |
| Crème | `#FFF6EF` |
| Prune | `#251532` |
| Titres | Baloo 2 ExtraBold |
| Interface | DM Sans |

Les maquettes de référence se trouvent dans :

`maquette/FastColoc_Maquettes_Sprint1/`

L'expérience utilisateur privilégie la lisibilité, les parcours guidés, la cohérence entre les plateformes et une navigation intuitive.

## ⚙️ Installation et démarrage

### Prérequis

- Java 21.
- Maven.
- Node.js et npm compatibles avec Angular 21.
- Android SDK pour l'application mobile.
- Accès à un projet Supabase FastColoc correctement configuré.

### 1. Cloner le projet

```bash
git clone https://github.com/HamzaBellaaj/Fast_Coloc.git
cd Fast_Coloc
```

### 2. Backend Spring Boot

Configurer les variables d'environnement nécessaires dans `backend/.env.local`, sans versionner les identifiants ni les secrets.

Depuis le dossier `backend/` :

```powershell
mvn.cmd spring-boot:run
```

API locale : `http://localhost:8001`

Documentation Swagger : `http://localhost:8001/swagger-ui/index.html`

> Important : les migrations Supabase déjà appliquées ne doivent pas être rejouées automatiquement. Consulter la documentation backend avant toute modification de la base.

### 3. Frontend Angular

```bash
cd frontend
npm ci
npm start
```

Application Web : `http://localhost:4200`

Le frontend utilise la configuration existante pour communiquer avec l'API Spring Boot.

### 4. Application Android

Ouvrir le dossier `mobile/` avec un environnement Android compatible, puis compiler :

```powershell
cd mobile
.\gradlew.bat assembleDebug
```

Pour un émulateur Android, l'API locale utilise généralement :

`http://10.0.2.2:8001/api/v1/`

Pour un téléphone connecté en USB, une configuration de développement et un tunnel ADB peuvent être nécessaires :

```bash
adb reverse tcp:8001 tcp:8001
```

La configuration de production doit utiliser HTTPS.

## 📱 Web & Android : une expérience connectée

Les deux applications utilisent la même infrastructure.

Une annonce créée sur le Web peut être consultée depuis Android. Les modifications, suppressions et favoris sont synchronisés via l'API.

L'application Android suit une architecture native Kotlin avec :

- MVVM et Repository.
- ViewModel et LiveData.
- Retrofit pour l'API.
- Room pour le cache local.
- RecyclerView pour les listes.
- Navigation Component pour les parcours utilisateur.

## 🗺️ Roadmap

| Module | État |
|---|---|
| Authentification et profils | ✅ Implémenté |
| Application Android native | ✅ Sprints 1 et 2 intégrés |
| Publication et gestion d'annonces | ✅ Implémenté |
| Recherche multicritère et favoris | ✅ Implémenté |
| Smart-Map Web | ✅ Implémenté |
| Modération ADMIN des annonces | ✅ Implémenté |
| Messagerie privée | 🔜 Sprint 3 |
| Notifications | 🔜 Sprint 3 |
| Estimation intelligente des loyers | 🔜 Sprint 3 |
| Recommandations personnalisées | 🔜 Sprint 3 |
| Swipe des annonces sur Android | 🔜 Sprint 3 |
| Boost des annonces | 🔜 Sprint 3 |
| Carte Android simplifiée | 🔜 Sprint 3 |
| Génération vidéo IA | 🧪 Expérimentation |
| Visualisation 3D | 🧪 Prototype envisagé |

Les fonctionnalités futures et expérimentales ne sont pas présentées comme disponibles dans la version actuelle.

## 📚 Documentation

### Sprint 1

- [Backend et intégration Supabase](docs/12-SPRINT1-OPERATIONNEL.md)
- [Application Web Angular](docs/13-SPRINT1-WEB-FINALISATION.md)
- [Intégration Android](docs/14-SPRINT1-ANDROID-INTEGRATION.md)

### Sprint 2

- [Périmètre fonctionnel](docs/15-SPRINT2-PERIMETRE.md)
- [Références et conformité Android](docs/16-SPRINT2-REFERENCES-ANDROID.md)
- [Rapport Sprint 2 — annonces et recherche](docs/17-SPRINT2-ANNONCES-RECHERCHE.md)

### Administration

- [US29 — Gestion administrative des annonces](docs/18-US29-GESTION-ADMIN-ANNONCES.md)

## 🔒 Sécurité

FastColoc applique plusieurs mécanismes de protection :

- Authentification avec Supabase Auth.
- Vérification des JWT côté backend.
- Autorisations selon les rôles.
- Row Level Security (RLS) sur PostgreSQL.
- Accès restreint aux ressources privées.
- Photos stockées dans des buckets privés avec URL signées.
- Contrôles d'accès aux annonces et aux opérations administratives.
- Secrets serveur conservés hors du code client.

Les comptes, données et ressources FastColoc sont indépendants des autres projets.

## 📌 État du projet

**Dernière référence fonctionnelle : 17 novembre 2026.**

Les Sprints 1 et 2 sont intégrés sur Spring Boot, Angular et Android. Le module de modération ADMIN US29 est également intégré sur le Web.

Le projet est en développement actif. Les prochaines évolutions concernent les interactions entre colocataires, l'intelligence artificielle et l'amélioration de l'expérience utilisateur.

---

### 💜 FastColoc

**Find your people. Find your place.**

*Built with care, powered by technology, designed for better coliving.*<img width="300" height="83" alt="fastcoloc-wordmark-reference" src="https://github.com/user-attachments/assets/8cde797f-a4a1-49ff-b770-017ca59161d6" />
