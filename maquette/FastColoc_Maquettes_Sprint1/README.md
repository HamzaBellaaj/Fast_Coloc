# FastColoc · Maquettes UI de référence — Sprint 1

**Démarrage :** décompresser l'archive puis ouvrir `index.html` dans un navigateur.

- `web/` : 12 maquettes HTML indépendantes pour Angular.
- `android/` : 12 maquettes HTML de référence de l'application future ; **pas de code Android/Kotlin**.
- `assets/fastcoloc-symbol.svg` : symbole original (deux colocataires sous un toit).
- `assets/fastcoloc-wordmark-reference.svg` : référence vectorielle du mot-symbole, à ajuster au rendu approuvé.

## Direction artistique

- Violet `#7138D8`
- Corail `#FF7954`
- Rose `#FFB9D6`
- Crème `#FFF6EF`
- Prune `#251532`
- Titres : **Baloo 2 ExtraBold** (Google Fonts en ligne, police de repli si hors ligne)
- Interface : **DM Sans** (Google Fonts en ligne, police de repli si hors ligne)

## Contraintes d'implémentation

1. Cette archive sert de **référence visuelle** : faux utilisateurs, faux prix et liens de navigation simulés.
2. Inscription en cinq étapes pendant la création du compte (pas après l'inscription).
3. L'étape photos propose provisoirement 5 photos de 5 Mo ; à valider côté métier et serveur.
4. Les e-mails, Google et la synchronisation du profil doivent être implémentés et testés avec Supabase Auth et Spring Boot.
5. Onboarding doit gérer les brouillons et la confirmation e-mail sans laisser les clients modifier le rôle ou le statut du compte.
6. Administration **Web seulement**.
7. Android est limité à des **maquettes** pour l'instant ; ne pas implémenter l'application Kotlin.
8. Ne jamais utiliser d'identifiant PostgreSQL admin côté client ; secret JDBC dans le backend seulement, avec compte dédié.
9. Ne jamais copier un secret d'Immovia.
10. Le design doit être accessible (contraste, état focus, validation, taille des zones cliquables).

## Écrans Web
- `index.html` — Accueil
- `login.html` — Connexion
- `register-1.html` — Inscription 1/5
- `register-2.html` — Inscription 2/5
- `register-3.html` — Inscription 3/5
- `register-4.html` — Inscription 4/5
- `register-5.html` — Inscription 5/5
- `verify.html` — Vérification e-mail
- `forgot.html` — Mot de passe oublié
- `reset.html` — Réinitialisation
- `profile.html` — Profil utilisateur
- `admin.html` — Administration Web

## Écrans Android
- `index.html` — Bienvenue
- `login.html` — Connexion
- `register-1.html` — Inscription 1/5
- `register-2.html` — Inscription 2/5
- `register-3.html` — Inscription 3/5
- `register-4.html` — Inscription 4/5
- `register-5.html` — Inscription 5/5
- `verify.html` — Vérification e-mail
- `forgot.html` — Mot de passe oublié
- `reset.html` — Réinitialisation
- `profile.html` — Profil
- `home-preview.html` — Accueil futur (aperçu)

## Ordre recommandé

1. Verrouiller les assets de marque et le design system.
2. Finaliser le provisioning PostgreSQL à privilèges minimaux et la configuration Supabase (Auth, Google OAuth, redirects, SMTP).
3. Finaliser Spring Boot + les endpoints et tests.
4. Implémenter le front Angular en reproduisant les maquettes.
5. Recette E2E en navigateur et Postman.
6. Démarrer Kotlin plus tard.
