# Plan d'authentification commun Web / Android

Proposition de contrat, aucun endpoint implémenté. Auth via Supabase uniquement. Un même utilisateur possède le même UUID sur les deux clients, mais des sessions distinctes.

## Parcours

| Cas | Clients et Supabase | Spring |
|---|---|---|
| Register | Formulaire cinq étapes conforme au cahier des charges, validation locale, signUp e-mail/mot de passe; metadata descriptive minimale | Profil créé/synchronisé idempotemment après token valide et identité confirmée; données métier via DTO d'étape |
| Confirmation | Écran attente, renvoi avec délai/rate limit; callback approuvé, lien invalide/expiré explicite | Ne pas confondre JWT signé, e-mail confirmé et onboarding terminé |
| Login | signInWithPassword; erreurs génériques; récupération de session | JWT + contrôle compte; profil et progression renvoyés |
| Google | OAuth PKCE, state, navigateur; callback puis onboarding des champs manquants | Même UUID Supabase; pas de fusion manuelle sur un e-mail fourni par le client |
| Forgot | resetPasswordForEmail; même réponse apparente si compte inconnu | Pas de endpoint révélant l'existence d'un compte |
| Reset | Session de récupération validée avec Auth avant updateUser; jamais booléen local seul | Les tokens reçus restent gérés par Auth; contrôler la politique de révocation des autres sessions |
| Refresh | SDK Web / SessionManager Android unique; rotation atomique et retry borné | Bearer uniquement; ne reçoit aucun refresh token |
| Logout | Supabase signOut, préciser local/appareil ou global; vider session et cache privé | JWT stateless peut rester valide jusqu'à exp; consulter état serveur pour suspension immédiate |
| Profil | Charger /me; changer les champs autorisés | Identité dérivée de sub, transactions, validation complète |
| Blocage | Message lisible, purge locale sur 403 ACCOUNT_BLOCKED | Contrôle status DB à chaque requête privée avant effets; interdire sync/upsert qui réactive un compte |

Pas de compte local fantôme pour un utilisateur non confirmé. Les brouillons préconfirmation contenant des données sensibles restent en mémoire; pour reprise après fermeture, redemander les champs ou valider un mécanisme sécurisé. Ne jamais stocker le mot de passe du formulaire. Google/confirmation depuis un autre appareil ne garantit pas l'accès au verifier PKCE d'origine : prévoir retour connexion ou OTP explicitement conçu, pas de contournement implicite automatique.

## API métier proposée /api/v1

- GET /health public.
- POST /profiles/sync : utilisateur authentifié confirmé, idempotent; USER/ACTIVE affectés uniquement pour une nouvelle ligne, jamais réactivation ni promotion.
- GET /profiles/me : profil privé et progression, même incomplet.
- PUT /onboarding/steps/{1..5} : DTO spécifique à l'étape, reprise et validation serveur. Les routes des premières étapes restent utilisables par un compte confirmé mais incomplet.
- POST /onboarding/complete : transaction vérifiant tous les champs et objets requis; timestamp serveur, pas de booléen client.
- PUT /profiles/me : liste fermée de champs; pas d'id, rôle, statut ni progression acceptés.
- GET /geography/governorates et /localities?governorateId=... : référentiels publics en lecture.
- POST /profile-photos/upload et DELETE /profile-photos/{id} : propriétaire vérifié, taille/type contrôlés; quota et taille limite à valider.
- PATCH /admin/users/{id}/status : administrateur autorisé, journal et motif obligatoire; aucun accès anonyme ou USER.

401 token absent/invalide; 403 compte bloqué ou droit insuffisant; 409 onboarding incomplet pour fonctionnalités nécessitant complétion; 400 champs invalides; 429 limitation. Distinguer onboarding incomplet et suspension afin d'éviter une boucle de connexion.

## Autorisation

JWT signature, issuer, dates, audience et claims attendus; subject UUID. Algorithme à configurer sur le nouveau projet (ES256 recommandé par continuité de l'architecture observée, ne pas supposer son activation). Rôles fonctionnels USER/ADMIN en table serveur; claim technique Supabase role=authenticated n'est pas ADMIN. Aucun privilège dans user_metadata. La promotion admin est une opération initiale contrôlée, jamais une option d'inscription.

RLS côté Supabase n'est pas un remplacement de l'autorisation JPA. Les clients n'accèdent pas aux tables métier directement. Les URLs signées de photos ont une durée courte : un lien déjà délivré reste lisible jusqu'à expiration après blocage. Durée à valider; ne pas promettre révocation immédiate du média signé.

## Références officielles consultées

- https://supabase.com/docs/guides/auth/users (metadata éditables par utilisateur)
- https://supabase.com/docs/guides/auth/managing-user-data (UUID Auth et limites JWT stateless)
- https://supabase.com/docs/guides/auth/sessions/pkce-flow
- https://supabase.com/docs/guides/auth/native-mobile-deep-linking
- https://supabase.com/docs/guides/database/postgres/row-level-security
## Alignement après lecture des sources officielles du projet

Le cahier des charges et Sprint 0 sont maintenant présents et lus dans docs, avec la répartition Word. Toute mention antérieure d'absence ou d'étapes provisoires est remplacée par docs/06-MODELE-DONNEES.md : 1 identité, 2 géographie, 3 informations personnelles, 4 coordonnées, 5 images. Budget et recherche ne constituent pas les étapes 3 et 4.

Sprint 1 officiel : US01–US07 + US32, 38 points / deux semaines. L'administration Angular inclut liste paginée, modification de coordonnées, blocage/déblocage ET suppression. Prévoir GET /admin/users, PATCH /admin/users/{id}/contacts, PATCH /admin/users/{id}/status et DELETE /admin/users/{id}; autorisation ADMIN et journal pour chaque mutation. Suppression : bloquer d'abord, puis nettoyer Storage/Auth via API officielle avec reprise sur échec; aucune clé serveur dans les clients.

Le mobile démarre au Sprint 1 avec MVVM/Retrofit/Room/session. Swipe sur annonces seulement (gauche passer/droite intéressé), pas de swipe de recommandations ni matching complexe. Vidéo : lecture possible Android, expérimentation de génération Web; aucune 3D Android. Messagerie GET/POST avec refresh, sans WebSocket. Leaflet/OpenStreetMap Web et carte Android simple. Spring Boot unique modulaire; IA/n8n/paiement isolés derrière interfaces. Vidéo/3D hors acceptation des trois sprints.

Les règles photos (nombre/taille), âge minimum, taxonomies neutres et remplacement éventuel MSN/Skype restent à confirmer. Nouveau Supabase et paramètres indépendants restent requis avant implémentation métier.
