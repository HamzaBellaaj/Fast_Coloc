# US29 — Administration Web des annonces

## Fonctionnement

Intégration à l’administration existante, avec sa navigation, ses cartes et ses styles FastColoc. Aucune administration Android ajoutée et aucune autre fonctionnalité du Sprint 3.

- `/admin/annonces` : toutes les offres et demandes, recherche titre/auteur/localisation, filtres type/statut/création, calendriers natifs interactifs, tri et pagination de 20 résultats.
- `/admin/annonces/:id` : informations métier complètes, photos privées signées, actions avec motif et confirmation, historique avant/après. Le même lien présente l’historique après suppression administrative.
- `/admin/annonces/:id/modifier` : réutilisation du formulaire Sprint 2, motif obligatoire, validation et version concurrente conservées.

Le masquage est indépendant de la publication. Une annonce masquée disparaît du catalogue, de la carte et des favoris, y compris ceux de son auteur. Celui-ci peut encore la consulter et la corriger ; il ne peut modifier le masquage ni publier/renouveler. Lever le masquage ne modifie jamais les dates de publication et d’expiration. La publication immédiate existante est conservée.

La suppression retire les objets Storage de l’annonce puis supprime l’annonce et ses relations en cascade (photos et favoris). L’historique conserve l’identifiant, le titre et l’administrateur sans FK susceptible de le supprimer en cascade. Les URL photo déjà émises restent valables jusqu’à leur expiration habituelle (300 secondes).

## API et autorisations

Préfixe `/api/v1/admin/listings`, JWT et ADMIN actif vérifiés par Spring Security et par les fonctions PostgreSQL privées :

| Méthode | Chemin | Fonction |
|---|---|---|
| GET | `/` | Liste : `q`, `kind`, `status`, `from`, `to`, `sort`, `page`, `size` |
| GET | `/{id}` | Détail et photos signées |
| GET | `/{id}/history` | Historique, également après suppression |
| PUT | `/{id}` | Correction `{listing: ListingWrite, reason}` |
| POST | `/{id}/hide` | Masquage `{reason, version}` |
| POST | `/{id}/restore` | Réactivation `{reason, version}` |
| DELETE | `/{id}` | Suppression confirmée `{reason, version}` |

Tri : `NEWEST`, `OLDEST`, `RENT_ASC`, `RENT_DESC`. Statuts affichés : `DRAFT`, `PUBLISHED`, `EXPIRED`, `HIDDEN`. Les bornes de création et heures administratives sont en UTC. Erreurs : 400 validation, 401 session, 403 droits/compte bloqué, 404 annonce absente, 409 version ou état périmé. Swagger inclut le groupe US29.

Contrôleur → service transactionnel → repository → capacités SQL. Les écritures prennent le verrou existant de l’auteur et celui de l’annonce, puis vérifient la version. Les contrôles des parcours Sprint 2 ont seulement été complétés pour appliquer le masquage.

## Migration autorisée et appliquée

Fichier : `backend/supabase/migrations/20261116233807_us29_listing_moderation.sql`.
SHA-256 : `50A7988005F40A159DC8AAE20CBED0779DA85859FE472EF3392EB9C823FE8787`.
Projet : FastColoc `nklsbubnyznlfpxpxsrp`, migration distante `20261116235232_us29_listing_moderation` (horodatage MCP différent du fichier local).

Ajouts : `listings.is_hidden` (défaut false), table privée `listing_moderation_events` avec RLS et index d’historique, quatre capacités administratives vérifiant `require_admin()`, trigger empêchant le contournement du masquage par le rôle backend. Deux politiques existantes (lecture annonces et ajout favoris) prennent en compte le masquage.

Vérifications avant/après : les empreintes et effectifs des **17 tables préexistantes sont identiques**, hors nouvelle colonne. Toutes les tables concernées gardent RLS ; aucun accès client `anon`/`authenticated` ajouté ; rôle backend sans superuser, BYPASSRLS ni héritage, sans écriture directe sur `is_hidden` et sans accès direct à l’historique. Les anciennes migrations n’ont pas été rejouées. Immovia n’a pas été touché.

L’avis informatif [RLS sans politique](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy) sur les historiques privés est intentionnel : aucune lecture directe, accès uniquement par fonctions contrôlées. L’avertissement Auth préexistant sur la [protection des mots de passe compromis](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection) demeure hors US29.

## Vérifications essentielles

- `mvn.cmd "-Dtest=Sprint1IntegrationTest#us29*" test -q` : **3 tests réussis**, 0 erreur/échec. PostgreSQL local réel, rôle backend non propriétaire, JWT/JWKS et HTTP Spring réels ; Auth/Storage simulés localement.
- Couverture ciblée : recherche/statuts/dates, masquage/public/carte/favoris, correction auteur sans démasquage, refus publication/renouvellement, restauration sans prolongation et respect de l’expiration ; USER/ADMIN bloqué refusés ; protections SQL directes ; correction auditée/version périmée ; suppression photos/favoris et conservation de l’historique.
- `mvn.cmd -DskipTests package -q` : compilation et packaging réussis.
- `npm.cmd run build` (frontend) : réussi ; avertissement Leaflet CommonJS déjà présent.
- `npx.cmd playwright test e2e/us29.spec.ts --reporter=list` : **1 parcours réussi** (1,6 minute avec préparation/nettoyage). ADMIN réel : recherche avec dates, consultation/photo, correction avec motif, annulation puis confirmation du masquage, absence publique et renouvellement auteur refusé, réactivation, contrôle responsive 390/1440 px, suppression et quatre événements historiques conservés. Un premier passage arrêté sur un sélecteur de test a été corrigé avant ce succès.
- Deux comptes et une annonce temporaires par passage, nettoyés par le mécanisme existant. Contrôle distant final : 4 profils, 1 annonce réelle avec empreinte initiale inchangée, 1 favori réel ; aucune annonce US29 temporaire ni auteur de test restant. Les quatre événements de recette restent volontairement conservés dans l’historique.
- Revue navigateur finale en lecture seule : dates civiles sans décalage de jour, champs conformes aux styles existants, navigation sans étirement, responsive, absence d’erreur JavaScript. Les sept opérations Swagger sont présentes. Captures locales : `frontend/test-results/us29/`.

Les suites complètes Sprints 1/2 et Android n’ont pas été relancées, conformément au périmètre demandé.

## Démarrage

Backend Java 21 : depuis `backend`, `mvn.cmd -DskipTests package`, puis `./tools/start-backend.ps1 -Background` (configuration privée `.env.local` existante). Ne pas relancer un second backend sur le port 8001 et ne pas rejouer les migrations distantes.

Web : depuis `frontend`, `npm.cmd start`. Ouvrir **http://localhost:4200/admin/annonces** avec le compte ADMIN existant. API : http://localhost:8001/api/v1 ; Swagger : http://localhost:8001/swagger-ui/index.html.
