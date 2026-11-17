# Configuration Supabase à réaliser manuellement

Aucune opération distante exécutée. Aucun secret Immovia copié. Ne pas fournir de mot de passe ou clé serveur dans une conversation ni dans Git.

1. Créer un projet Supabase dédié FastColoc; relever son URL et sa clé publishable publique. Choisir région, conserver mot de passe DB dans un gestionnaire de secrets et relever les paramètres du pooler/JDBC SSL.
2. Relever issuer et JWKS du NOUVEAU projet depuis la configuration/documentation Auth; vérifier son algorithme asymétrique de signature et la rotation. Le backend devra échouer au démarrage si la configuration manque, sans fallback Immovia.
3. Activer l'authentification e-mail et la confirmation obligatoire. Configurer SMTP réel avant recette e-mail, expéditeur, modèles confirmation/reset, limites et durée des liens. Ne pas désactiver la confirmation pour contourner un problème SMTP.
4. Configurer Site URL et les redirections exactes : développement Web `http://localhost:4200/auth/callback`; proposition Android `tn.fastcoloc://auth/callback` à valider avec applicationId et intent-filter. Production : domaine réel à fournir; préférer App Links HTTPS vérifiés à terme. Pas de wildcard de production.
5. Google Cloud : créer les clients OAuth nécessaires, écran de consentement et testeurs. Autoriser le callback Supabase indiqué par le dashboard. Le retour Supabase vers Angular/Android est un second callback distinct. Client secret uniquement dans Supabase; pas dans Android/Angular. Un futur login Google natif nécessiterait configuration Android/package/SHA et un parcours à valider séparément.
6. Relire le SQL brouillon, confirmer les limites ouvertes du cahier des charges. Le SQL n'est pas encore une migration exécutable automatiquement par Flyway. Le tester sur Supabase local ou PostgreSQL avec schéma Auth simulé, puis effectuer la promotion vers db/migration après revue.
7. Préparer comptes DB migration/runtime séparés. Définir explicitement grants et politiques pour le compte runtime (schéma fastcoloc + tables + séquences + Auth en lecture strictement nécessaire). Le brouillon protège les rôles clients mais ne crée PAS ce compte et ne rend pas l'API opérationnelle.
8. Créer un bucket PRIVÉ `profile-photos` après validation, limites MIME JPEG/PNG/WebP, taille/quota à confirmer; ne pas créer de bucket public par défaut. Stratégie proposée : transferts via backend avec secret dédié serveur et chemins UUID utilisateur/UUID photo.ext, URL signée courte. Sans politique client Storage, aucun accès direct client; ne jamais modifier directement storage.objects via JPA.
9. Initialiser les référentiels tunisiens depuis une source approuvée; aucun nom/code géographique inventé dans le SQL. Définir la procédure d'attribution du premier ADMIN hors inscription.
10. Vérifier localement les permissions anon/authenticated, compte bloqué et autorisations DB/JPA avant d'autoriser le Sprint 1 métier.

## Paramètres à communiquer pour débloquer le développement

Configuration PUBLIQUE : URL du nouveau projet, clé publishable, issuer/JWKS, callback Web/Mobile retenus, état activation Google/confirmation, cahier des charges et cinq étapes. Configuration PRIVÉE à renseigner localement : DB_PASSWORD, identité JDBC dédiée, secret Storage/Admin si utilisé. Ne pas transférer une clé service_role d'Immovia.

Noms prévus : SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY côté clients; SUPABASE_JWT_ISSUER, SUPABASE_JWKS_URI, DB_HOST/PORT/NAME/USERNAME/PASSWORD, FRONTEND_ORIGIN côté serveur. Le secret serveur ne doit jamais être compilé dans les bundles Angular/APK ni accessible à n8n côté client.

Sur le Fold physique, localhost désigne le téléphone : employer l'IP LAN du PC (même réseau) ou adb reverse pour le port de développement. HTTP éventuellement autorisé uniquement dans la configuration Android debug et pour la cible locale; release en HTTPS. Aucun réglage réseau global ni pare-feu changé ici.
## Alignement après lecture des sources officielles du projet

Le cahier des charges et Sprint 0 sont maintenant présents et lus dans docs, avec la répartition Word. Toute mention antérieure d'absence ou d'étapes provisoires est remplacée par docs/06-MODELE-DONNEES.md : 1 identité, 2 géographie, 3 informations personnelles, 4 coordonnées, 5 images. Budget et recherche ne constituent pas les étapes 3 et 4.

Sprint 1 officiel : US01–US07 + US32, 38 points / deux semaines. L'administration Angular inclut liste paginée, modification de coordonnées, blocage/déblocage ET suppression. Prévoir GET /admin/users, PATCH /admin/users/{id}/contacts, PATCH /admin/users/{id}/status et DELETE /admin/users/{id}; autorisation ADMIN et journal pour chaque mutation. Suppression : bloquer d'abord, puis nettoyer Storage/Auth via API officielle avec reprise sur échec; aucune clé serveur dans les clients.

Le mobile démarre au Sprint 1 avec MVVM/Retrofit/Room/session. Swipe sur annonces seulement (gauche passer/droite intéressé), pas de swipe de recommandations ni matching complexe. Vidéo : lecture possible Android, expérimentation de génération Web; aucune 3D Android. Messagerie GET/POST avec refresh, sans WebSocket. Leaflet/OpenStreetMap Web et carte Android simple. Spring Boot unique modulaire; IA/n8n/paiement isolés derrière interfaces. Vidéo/3D hors acceptation des trois sprints.

Les règles photos (nombre/taille), âge minimum, taxonomies neutres et remplacement éventuel MSN/Skype restent à confirmer. Nouveau Supabase et paramètres indépendants restent requis avant implémentation métier.
