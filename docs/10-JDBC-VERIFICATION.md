# Vérification JDBC distante — 11 novembre 2026

Projet exclusif : nklsbubnyznlfpxpxsrp. Aucun accès Immovia.

## Résultat exécuté

Le programme `backend/tools/JdbcReadOnlyProbe.java` a réellement ouvert une
connexion JDBC avec les paramètres fournis par l'utilisateur :

- Hôte : aws-1-eu-north-1.pooler.supabase.com ; port 5432 ; base postgres.
- Login pooler de diagnostic : postgres.nklsbubnyznlfpxpxsrp.
- Pilote PostgreSQL 42.7.13, Java 21.
- `sslmode=verify-full`, certificat CA Supabase explicitement fourni.
- Transaction serveur `transaction_read_only=on`, terminée par rollback.
- Aucun INSERT, UPDATE, DELETE, DDL, GRANT ou changement de configuration distant.

Résultats des seules requêtes SELECT de métadonnées :

```text
database=postgres | role=postgres | read_only=on
PostgreSQL 17.11, aarch64-unknown-linux-gnu
tables=14 | tables_with_rls=14
fastcoloc_backend: aucune ligne dans pg_roles
fastcoloc_policies=0
20261111203735 | sprint1_initial_private_schema
20261111203809 | restrict_rls_event_trigger_function_execution
```

Le compte postgres est autorisé uniquement pour ce diagnostic ; il n'a pas été
configuré comme compte runtime et serait refusé par RuntimeDatabaseGuard.
Le mot de passe a été transmis au programme par variable d'environnement de
processus, effacée à la fin. Il n'est enregistré dans aucun fichier du projet.
Le mot de passe partagé dans la conversation doit être remplacé par l'utilisateur.

## Certificat et portée du TLS

Les magasins de confiance Java et Windows ne reconnaissaient pas initialement
la CA Supabase : erreur PKIX, avant authentification PostgreSQL.
Le certificat public a été téléchargé en HTTPS depuis :
https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt

Copie locale : `backend/config/supabase-prod-ca.crt`.
Sujet : Supabase Root 2021 CA, Supabase Inc ; expiration 26 avril 2031.
SHA-256 du fichier :
`700723581420DD1AC98FD7E9AC529F0EF210EADCAF87FC868A3AD7D114C2F3B7`.
Aucune autorité ajoutée aux magasins système ; aucune désactivation de vérification.

La connexion Java vers le pooler passe avec CA et nom d'hôte vérifiés.
Cependant `pg_stat_ssl` pour pg_backend_pid() retourne :

```text
ssl=false | version=null | cipher=null
```

Cette vue décrit la connexion reçue par PostgreSQL depuis le pooler, pas la
connexion Java vers le pooler. Ce résultat ne prouve donc pas un TLS de bout
en bout ; il indique que cette session PostgreSQL ne reçoit pas de TLS du
pooler. Aucun réglage distant n'a été changé pour ce diagnostic.

Référence officielle pour verify-full et CA :
https://supabase.com/docs/guides/platform/ssl-enforcement

## Configuration préparée et blocage restant

`.env.example` contient désormais l'hôte réel, le chemin de la CA et le login
runtime attendu fastcoloc_backend.nklsbubnyznlfpxpxsrp, sans mot de passe.
Les chemins relatifs supposent un lancement depuis le dossier backend.
Spring ne charge pas automatiquement le fichier .env.

Le rôle fastcoloc_backend n'existe pas au distant ; les policies runtime ne sont
pas appliquées. Le SQL à revoir reste `backend/tools/runtime-role.proposal.sql`,
testé localement et non exécuté au distant. Les instructions de non-modification
distante restent respectées. Il faut provisionner ce rôle et son propre mot de
passe via un canal autorisé avant de démarrer le backend contre Supabase.

Les 24 tests précédents restent des tests métier locaux ; ce diagnostic ajoute
une preuve JDBC réseau/TLS et des métadonnées réelles, sans prétendre valider
les endpoints métier sur le Supabase distant.
