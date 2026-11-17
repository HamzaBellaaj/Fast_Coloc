# Accès MCP FastColoc — état corrigé le 11 novembre 2026

Projet ciblé : `nklsbubnyznlfpxpxsrp`.

## Outils effectivement exposés et utilisés

Le serveur MCP fastcoloc expose cinq outils dans cette session :

- `mcp__fastcoloc__execute_sql`
- `mcp__fastcoloc__list_tables`
- `mcp__fastcoloc__list_migrations`
- `mcp__fastcoloc__list_extensions`
- `mcp__fastcoloc__search_docs`

list_tables, list_migrations et execute_sql ont réellement réussi.
Les requêtes de catalogues/historique ont été exécutées dans des transactions
READ ONLY avec le rôle supabase_read_only_user. Aucun enregistrement métier
n'a été nécessaire pour l'audit. search_docs a également été utilisé pour
les contrats Auth/JWT pendant l'implémentation.

L'outil du plugin Supabase `mcp__codex_apps__supabase_list_tables` a aussi
été appelé avec project_id=nklsbubnyznlfpxpxsrp et schemas=[fastcoloc].
Il confirme les mêmes 14 tables et leur RLS.

Les affirmations précédentes « outils absents », « aucune inspection distante »
et « redémarrer la session pour continuer » sont remplacées par ces résultats.
Aucun changement de configuration MCP, d'OAuth ou de réglage global n'a été effectué.

## Résultats et portée

14 tables, 76 colonnes, relations/contraintes/index et ACL inspectés.
Migrations confirmées : 20261111203735 sprint1_initial_private_schema et
20261111203809 restrict_rls_event_trigger_function_execution.
Voir `07-RECONCILIATION-SUPABASE.md` pour la comparaison exacte avec V001.

La lecture HTTP du JWKS public publie une clé EC ES256
(kid observé : 82f3946b-e41a-4a71-bfed-d9dd64ac695e).
Le code résout les clés par JWKS et kid ; aucune clé publique figée dans le dépôt.

MCP opérationnel, JWKS public accessible et connexion JDBC sont trois validations
différentes. JDBC distant est désormais confirmé en READ ONLY via le pooler session,
avec CA Supabase et sslmode=verify-full, sous postgres pour diagnostic uniquement.
Les tests métier/RLS du backend restent locaux : fastcoloc_backend est absent au
distant, qui ne possède aucune policy fastcoloc. Voir 10-JDBC-VERIFICATION.md,
notamment la distinction TLS client/pooler et pg_stat_ssl côté serveur.

## Contraintes maintenues

Aucune base distante modifiée : ni migration, ni table, ni grant, ni policy.
Immovia a uniquement servi de référence de code d'authentification en lecture
seule ; aucun de ses secrets n'a été lu ou copié et aucun fichier n'a été modifié.

Le backend exécutable et ses tests remplacent désormais la structure vide.
Les configurations nécessaires, la proposition de rôle runtime non administrateur,
les endpoints et les limites de la recette externe sont décrits dans
`backend/README.md` et `09-BACKEND-IMPLEMENTATION.md`.
