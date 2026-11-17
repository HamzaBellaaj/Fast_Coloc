# Audit de la référence Immovia

Audit statique réel effectué le 11 novembre 2026. Aucun test relancé : Maven/npm pourraient écrire dans ce projet exigé en lecture seule. Les chiffres historiques du rapport Immovia ne sont pas présentés comme de nouveaux résultats.

## Architecture observée

Angular standalone 21.2.x, TypeScript ~5.9.2, RxJS ~7.8, Supabase JS ^2.119.0. `frontend/package.json` contient génération de configuration, build et tests navigateur Playwright; pas de script de tests unitaires Angular observé. `app.routes.ts`, guards, interceptor, services Auth/Profile, DTO TypeScript et composants partagés séparent navigation, état et HTTP. Formulaires réactifs pour inscription/login/recovery/profil.

Spring Boot 3.5.16, Java 21, Security Resource Server, JPA/PostgreSQL, Bean Validation et springdoc 2.10.0 dans `backend/pom.xml`. Contrôleurs, services transactionnels, repositories, DTO, exceptions et configuration séparés. Il s'agit des versions observées dans la référence, pas d'une obligation de recopier ses versions sans vérifier le support avant initialisation.

## Comportements réellement lus

| Domaine | Preuve dans Immovia | Réutilisation / adaptation FastColoc |
|---|---|---|
| Auth/session Angular | `frontend/src/app/services/auth.service.ts` : SDK chargé paresseusement, signals, session persistée, refresh automatique | Approche à réécrire avec configuration FastColoc uniquement |
| Inscription | `signUp` transmet prénom/nom/téléphone en metadata et attend confirmation; éventuelle session fermée | Remplacer le formulaire simple par onboarding 5 étapes, données métier via API après authentification |
| Google | `signInWithOAuth`, scopes openid/email/profile, callback Angular | Nouveaux clients OAuth; Android Custom Tabs + PKCE + deep link; Google peut fournir des données partielles |
| Callback | échange code PKCE; compatibilité hash/token_hash; URL nettoyée immédiatement; erreurs explicites | Préférer PKCE, liste fermée des callbacks et aucune redirection arbitraire; ne pas copier automatiquement les anciens modes de compatibilité |
| Confirmation | parcours attente e-mail et contrôle `email_confirmed_at` côté client | Ajouter vérification serveur de l'identité confirmée selon claims réellement émis / Admin Auth; un guard n'est pas un contrôle d'API |
| Recovery | contexte utilisateur + expiration en sessionStorage, PASSWORD_RECOVERY, getUser avant updateUser | Conserver l'intention, adapter Android et tester OTP expiré, mauvais utilisateur, lien réutilisé et changement d'appareil |
| Bearer HTTP | `interceptors/auth.interceptor.ts` ne joint JWT qu'à l'origine API et `/api/profile/` | Étendre explicitement aux routes FastColoc autorisées; jamais aux URL externes |
| JWT | SecurityConfig : Nimbus JWKS + ES256, issuer et dates; SupabaseAccessTokenValidator : UUID sub/session_id, audience, rôle authenticated, non-anonyme, iat | Réutiliser la stratégie, configurer issuer/JWKS FastColoc et algorithme réellement choisi; vérifier rotation et révocation/blocage |
| Sécurité HTTP | API stateless, CSRF désactivé pour Bearer, CORS origine explicite, défaut denyAll, Swagger dev, erreurs JSON | Ajouter routes onboarding, filtre de blocage, autorisation admin et matrice d'accès |
| Profil | ProfileController : POST sync, GET/PUT me, POST become-owner | UUID de sub conservé; supprimer logique propriétaire qui n'est pas un rôle FastColoc |
| Service/repository | insert ON CONFLICT, verrou pessimiste et transactions; noms Google complétés sans écraser des champs déjà édités | Réutiliser idempotence/concurrence; enrichir profil géographie/préférences/photos/étapes |
| DTO | champs autorisés bornés; unknown JSON rejeté; identifiant dérivé de sub | Ajouter DTO par étape et validation croisée, jamais rôle/status modifiable par DTO utilisateur |
| PostgreSQL | profil Supabase : SSL requis, Hikari, JPA ddl-auto=validate, import externe secrets local | Nouveau projet/base/compte DB; migrations explicites, aucun défaut pointant sur Immovia |
| Interface | layouts Auth/Dashboard, FormField, Feedback et styles | Réutiliser accessibilité/états; refaire identité SOCIAL POP, aucun écran admin générique côté public |
| Tests | ApiSecurityTest, ProfileServiceTest, HttpSmokeTest et tests Swagger; sprint1.browser.cjs | Adapter scénarios plutôt que recopier assertions et fixtures d'identité |

## Tests inspectés et limites

ApiSecurityTest couvre tokens absents, claims/signature/algorithme invalides, mass assignment, DTO, CORS et erreurs. ProfileServiceTest couvre identité, noms Google manquants, idempotence, conservation des champs édités et accès au profil du sujet. Tests HTTP démarrent un vrai serveur avec repository substitué; ils ne prouvent pas une intégration PostgreSQL distante. Les tests navigateur simulent Auth/API et couvrent connexion, Google, profils, erreurs et récupération; ils ne prouvent pas le fonctionnement d'un compte Google réel.

Le rapport `Immovia/docs/SPRINT1-VERIFICATION.md` rapporte des vérifications passées, mais le code a ensuite évolué : ne pas reprendre son nombre de tests comme état courant. La base distante, les RLS et la délivrabilité e-mail ne peuvent pas être déduites du code source.

## Écarts majeurs

Aucun modèle 5 étapes, blocage immédiat, administration, stockage photos ou MVVM Android dans cette référence. La vérification cryptographique JWT seule ne suffit pas à bloquer immédiatement un compte dont le token n'a pas expiré. Les métadonnées utilisateur ne doivent attribuer aucun privilège. Les identifiants/clés/URLs/fixtures Immovia ne seront jamais transférés.