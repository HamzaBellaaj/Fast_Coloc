# Plan Sprint 1 et critères de recette

## Ordre de réalisation

0. Valider cahier des charges, étapes d'inscription, champs/consentements, schéma et nouveau Supabase. Cette préparation est effectuée; les autres étapes ne commencent pas encore.
1. Initialiser les trois applications indépendantes, versions stables compatibles, wrappers et configurations exemples sans secret; CI build/tests et contrat OpenAPI commun.
2. Tester/promouvoir les migrations; référentiels géographiques validés; compte DB runtime; tests PostgreSQL/RLS et absence de droits clients.
3. Socle backend : JWT/JWKS, erreurs, identité, sync idempotent et contrôle de blocage; tests signés avec JWKS local et PostgreSQL local.
4. Socles Angular/Android : AuthService et AuthRepository/SessionManager/ViewModels/Retrofit, login/logout et restauration session. Deux appareils = un UUID mais deux sessions.
5. Inscription cinq étapes : saisie et erreurs par étape, confirmation, reprise après confirmation, validation transactionnelle de complétion. Champs issus du cahier des charges; neutralité et limites photos à valider.
6. Google PKCE, confirmation et récupération : callbacks Web et Android, erreurs, expirations et cas appareil différent; configuration SMTP/Google réelle.
7. Profil, préférences, géographie et photo : DTO, permissions, validations serveur, cache Room limité. Administrateur Web : suspension/réactivation autorisée et audit.
8. Recette croisée, responsive/TalkBack, tests et compilation APK; installation Fold et scénario compte créé Web puis connecté Mobile, puis inversement.

## Matrice minimale

- JWT absent/expiré/faux issuer/signature/audience/UUID et mauvais algorithme → 401; aucune écriture.
- USER ne peut promouvoir son rôle, modifier status, choisir sub/id, consulter ou modifier le profil d'autrui → refus serveur.
- Session encore cryptographiquement valide après suspension → accès métier refusé immédiatement (politique sans cache de blocage initialement).
- Inscription incomplète peut poursuivre onboarding; fonctions métier nécessitant complétion refusées.
- Chaque étape refuse champs inconnus et valeurs invalides; impossible de forcer completed_at; validation finale atomique.
- Google sans téléphone/nom complet, double callback et sync concurrent : aucun doublon, aucun écrasement de données choisies.
- E-mail non confirmé, renvoi limité, lien expiré/réutilisé; inscription/recovery n'énumèrent pas les comptes.
- Refresh concurrent Android : une seule rotation; restauration après redémarrage; logout purge les caches privés.
- Token/refresh/mot de passe absents des logs, sauvegardes, Room et dépôt.
- SQL/RLS : anon et authenticated ne lisent ni n'écrivent les tables; rôle runtime testé; pas de déduction abusive à partir de tests en postgres propriétaire.
- Photos : type/taille/chemin/propriétaire, accès autre utilisateur, URL expirée et blocage; aucune ouverture publique accidentelle.
- Android : ViewBinding, navigation/retour, rotation d'écran, reprise, réseau indisponible; REST vers PC réel et compilation assembleDebug + tests.
- Angular : formulaires réactifs, focus/erreurs, responsive, erreurs HTTP et callbacks; administrateur refusé hors droits.

Commandes cibles APRÈS initialisation : backend `mvn verify`, frontend `npm ci` puis build/tests configurés, Android `gradlew.bat assembleDebug testDebugUnitTest lintDebug`. Elles n'ont pas été exécutées sur cette structure vide.

## Hors Sprint 1

Swipe Android, annonces, messagerie, boosts et modération fonctionnelle à planifier dans les sprints suivants selon backlog validé. Le mécanisme de blocage et ses droits sont préparés au Sprint 1. IA serveur/n8n, vidéo et 3D restent des chantiers ultérieurs indépendants.

## Freins ouverts

Cahier des charges et cinq étapes désormais analysés, nouveau Supabase/configuration indisponibles, règles de photo/âge/genre/préférences et statut juridique des consentements non fournis. Aucun choix de ces champs n'est présenté comme une exigence confirmée. Débogage Android VS Code à valider séparément; import Kotlin Android encore expérimental.
## Alignement après lecture des sources officielles du projet

Le cahier des charges et Sprint 0 sont maintenant présents et lus dans docs, avec la répartition Word. Toute mention antérieure d'absence ou d'étapes provisoires est remplacée par docs/06-MODELE-DONNEES.md : 1 identité, 2 géographie, 3 informations personnelles, 4 coordonnées, 5 images. Budget et recherche ne constituent pas les étapes 3 et 4.

Sprint 1 officiel : US01–US07 + US32, 38 points / deux semaines. L'administration Angular inclut liste paginée, modification de coordonnées, blocage/déblocage ET suppression. Prévoir GET /admin/users, PATCH /admin/users/{id}/contacts, PATCH /admin/users/{id}/status et DELETE /admin/users/{id}; autorisation ADMIN et journal pour chaque mutation. Suppression : bloquer d'abord, puis nettoyer Storage/Auth via API officielle avec reprise sur échec; aucune clé serveur dans les clients.

Le mobile démarre au Sprint 1 avec MVVM/Retrofit/Room/session. Swipe sur annonces seulement (gauche passer/droite intéressé), pas de swipe de recommandations ni matching complexe. Vidéo : lecture possible Android, expérimentation de génération Web; aucune 3D Android. Messagerie GET/POST avec refresh, sans WebSocket. Leaflet/OpenStreetMap Web et carte Android simple. Spring Boot unique modulaire; IA/n8n/paiement isolés derrière interfaces. Vidéo/3D hors acceptation des trois sprints.

Les règles photos (nombre/taille), âge minimum, taxonomies neutres et remplacement éventuel MSN/Skype restent à confirmer. Nouveau Supabase et paramètres indépendants restent requis avant implémentation métier.
