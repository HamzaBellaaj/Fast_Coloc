# Revue du provisionnement PostgreSQL FastColoc

11 novembre 2026 — projet exclusif `nklsbubnyznlfpxpxsrp`.
**Revue et préparation locales uniquement : aucune écriture Supabase exécutée.**
Ce document remplace les anciennes descriptions de la proposition runtime dans
les documents 07/09/10 pour la partie provisionnement.

## 1. Revue intégrale de la proposition initiale

| Élément initial | Problème ou justification | Correction livrée |
|---|---|---|
| BEGIN/COMMIT | Atomicité nécessaire | Conservée, verrou de provisionnement et lock_timeout borné |
| REVOKE CREATE public FROM PUBLIC | Effet partagé, pas une opération propre au seul runtime | Retiré ; prérequis contrôlé, échec si PUBLIC possède CREATE |
| CREATE ROLE LOGIN | Échec au deuxième passage ; LOGIN avant secret configuré | Création conditionnelle NOLOGIN, sans secret, marqueur de gestion |
| Flags NOSUPERUSER/NOBYPASSRLS/NOINHERIT | Nécessaires, mais pas suffisants | Maintenus ; refus des memberships, propriétaires, REPLICATION et droits externes inattendus |
| CONNECT + USAGE fastcoloc | Nécessaires | Conservés pour le seul rôle serveur |
| SELECT ON ALL TABLES | Beaucoup de données inutilisées et futures colonnes implicitement accessibles | Remplacé par les colonnes réellement utilisées, tableau ci-dessous |
| INSERT ciblés | Adaptés à sync | Conservés et complétés par les contrôles RLS |
| Politiques own UUID seules | Un BLOCKED restait lisible en SQL direct | Frontières restrictives avec contrôle du statut serveur |
| Bootstrap du profil | account_access n'existe pas encore au premier INSERT | Exception contrôlée pour le seul profil propre avant sa ligne d'accès |
| Lecture account_access | Doit diagnostiquer BLOCKED sans rendre son rôle modifiable | SELECT user_id/app_role/status propre, aucune écriture rôle/statut |
| Référentiels USING(true) | Non utilisés par les endpoints actuels | Aucun droit de base ; lecture ACTIVE seulement dans l'extension optionnelle |
| Aucun UPDATE/DELETE/séquence | Correct pour sync, insuffisant pour les futurs écrans | Base minimale et extension onboarding distincte, désactivée par défaut |
| Pas de reset de droits historiques | Table REVOKE seul laisse des droits de colonnes | Révocation explicite des ACL de colonnes avant réattribution |
| Pas de retour arrière | Révocation improvisée dangereuse | Script de quarantaine idempotent, sans suppression de données |

Les 14 entités ont été lues, y compris les quatre identités bigint, UUID, tableaux,
JSONB et clés composées. Il n'existe qu'un ProfileRepository réellement utilisé.
ProfileService exécute six INSERT ON CONFLICT DO NOTHING, une relecture JPA du
profil et une lecture de step_number. AccountAuthorizationService lit app_role et
status ; DatabaseIdentity fixe le contexte dans la transaction. Aucun service
d'édition des cinq étapes, d'upload, de consentement ou d'administration CRUD n'est
encore implémenté. Le mapping d'une entité n'est pas une justification de GRANT.

## 2. Fichiers SQL à examiner avant toute application

- Migration canonique :
  `backend/supabase/migrations/20261111220503_provision_fastcoloc_backend.sql`.
  Nom généré par **Supabase CLI 3.1.0**, via `migration new` local uniquement.
- `backend/tools/runtime-role.proposal.sql` est désormais un point d'entrée **psql**
  avec `\ir` vers ce fichier ; pas une seconde copie du SQL. Un outil JDBC/MCP ne
  doit pas recevoir les métacommandes psql.
- `backend/tools/onboarding-rights.proposal.sql` est une extension future testée,
  placée hors migrations pour empêcher son activation implicite.
- `backend/tools/runtime-role.rollback.sql` retire les capacités et désactive LOGIN.

La migration refuse un rôle existant sans marqueur `fastcoloc.runtime.v1`, un rôle
élevé/propriétaire, des privilèges directs hors fastcoloc, des default grants vers
le runtime, une membership cliente, un point d'entrée SECURITY DEFINER accessible
dans public/fastcoloc ou un état privé/RLS incohérent. Ce refus appelle une revue ;
elle ne corrige pas silencieusement les droits des autres services.

Le rôle neuf reste NOLOGIN. Une nouvelle exécution conserve un mot de passe et un
état LOGIN déjà provisionnés, remet les droits du rôle au contrat de base et recrée
uniquement les politiques `fc_runtime_*`. Elle n'efface pas les autres politiques.
La réapplication de la base **retire les droits optionnels onboarding** : cet effet
est intentionnel et testé. Ne pas l'utiliser comme une réparation aveugle en production.

Les deux migrations distantes existantes ne sont ni recopiées ni rejouées. Le
dossier local de migrations n'est pas une reconstruction complète de l'historique :
**ne pas lancer db push/reset/repair** sur cette préparation. Après autorisation,
choisir le runner Supabase, vérifier ses transactions et enregistrer uniquement
la nouvelle migration. Le fichier est transactionnel ; éviter de l'imbriquer dans
un runner dont les BEGIN/COMMIT seraient incompatibles sans adaptation revue.

## 3. Matrice des droits exacts de base

S = SELECT, I = INSERT ; aucune opération implicite sur les autres colonnes.

| Table | Droits nécessaires aux services actuels | Limite RLS |
|---|---|---|
| countries | Aucun | Aucun accès |
| governorates | Aucun | Aucun accès |
| localities | Aucun | Aucun accès |
| profiles | S(id,first_name,last_name,gender,age,completed_at,version,created_at,updated_at), I(id,first_name,last_name,gender,age) | UUID propre ; aucun accès si BLOCKED ; bootstrap propre sans account_access permis |
| account_access | S(user_id,app_role,status), I(user_id) | UUID propre ; INSERT seulement USER/ACTIVE, sans blocage ; statut BLOCKED lisible pour diagnostiquer 403 |
| profile_addresses | S(user_id), I(user_id) | UUID propre et ACTIVE |
| personal_details | S(user_id), I(user_id) | UUID propre et ACTIVE |
| contact_details | S(user_id), I(user_id) | UUID propre et ACTIVE |
| roommate_preferences | S(user_id), I(user_id) | UUID propre et ACTIVE ; uniquement la ligne par défaut créée par sync |
| preferred_localities | Aucun | Aucun accès |
| profile_photos | Aucun | Aucun accès |
| onboarding_steps | S(user_id,step_number) | UUID propre et ACTIVE |
| consent_records | Aucun | Aucun accès |
| account_access_events | Aucun | Aucun accès |

Les SELECT(user_id) associés servent au contrat minimal de synchronisation,
notamment à l'identification des conflits. La validation des 14 mappings JPA passe
sous ces droits réduits : aucun SELECT général n'est requis pour les seules métadonnées.
Lire une section non utilisée via EntityManager est maintenant volontairement refusé.

| Séquence existante | USAGE | SELECT | UPDATE | Motif |
|---|---|---|---|---|
| governorates_id_seq | Non | Non | Non | Référentiel géré hors runtime |
| localities_id_seq | Non | Non | Non | Référentiel géré hors runtime |
| consent_records_id_seq | Non | Non | Non | Consentements non implémentés |
| account_access_events_id_seq | Non | Non | Non | Aucune mutation administrative implémentée |

La présence de @GeneratedValue(IDENTITY) ne justifie pas un GRANT tant qu'aucun
INSERT correspondant n'est exécuté. Sync n'utilise aucune séquence ; photos UUID
et étapes à clé composée n'en utilisent pas non plus. Un futur consentement ou
journal devra faire vérifier le comportement de son IDENTITY avec les requêtes
exactes ; ne pas donner par défaut USAGE ON ALL SEQUENCES.

## 4. Préparation des cinq étapes, sans ouverture anticipée

L'extension optionnelle accorde uniquement les capacités suivantes sous UUID
propre et statut ACTIVE. Elle ne crée pas les endpoints et ne constitue pas la
validation métier des données.

| Étape | Droits additionnels préparés |
|---|---|
| 1 identité | UPDATE profiles(first_name,last_name,gender,age,updated_at,version) |
| 2 géographie | SELECT des colonnes géographiques existantes ; SELECT adresse complète ; UPDATE adresse(country_code,locality_id,street,postal_code) |
| 3 informations personnelles | SELECT des colonnes existantes ; UPDATE activités, loisirs, nationalité, langues, religion, smoking, propreté, biographie |
| 4 contacts | SELECT des colonnes existantes ; UPDATE téléphones, disponibilité, MSN, Skype, site |
| 5 photos | SELECT métadonnées existantes ; INSERT métadonnées sans created_at ; UPDATE caption/display_order/is_primary ; DELETE propre |
| Marqueurs 1..5 | SELECT(user_id,step_number,validated_at), INSERT(user_id,step_number), UPDATE(validated_at), DELETE propre pour invalider une validation |

`completed_at`, `created_at`, les UUID propriétaires, rôle et statut ne deviennent
pas modifiables. Les quotas photos, règles finales de complétion et consentements
restent inconnus. Aucun UPDATE(completed_at) ni fonction SECURITY DEFINER n'est
inventé : prévoir un contrat distinct pour la validation finale transactionnelle.
Les marqueurs 1..5 sont écrits par le serveur après validation, jamais à partir
d'une affirmation cliente de complétion.

Les futurs DTO doivent rester des listes fermées. Utiliser des UPDATE ciblés ou
un mapping Hibernate adapté : un save JPA générique peut inclure des colonnes
interdites même inchangées. Les contraintes SQL restent actives ; pays/localité
doivent être vérifiés par le service. Les photos exigent un flux Storage privé,
contrôle des objets, MIME réel, quotas et compensation ; une ligne SQL ne prouve
pas qu'un objet Storage est valide. Pas de privilège SQL sur storage.objects.

## 5. Les quatre frontières de sécurité

1. **Supabase Auth** authentifie l'identité et confirme l'e-mail/Google. Le backend
   n'a aucun droit SQL auth.users/auth.sessions et n'émet pas ses propres JWT.
2. **Spring Security** vérifie signature ES256/JWKS, issuer, audience, dates,
   sub/session_id UUID, role technique authenticated et is_anonymous=false.
   GET Auth /user vérifie l'UUID et email_confirmed_at ; indisponibilité = refus.
3. **Autorisation métier** lit account_access à chaque requête privée. USER et
   ADMIN sont des valeurs fiables de la base, jamais user_metadata ni un DTO.
   BLOCKED provoque 403. /admin/access vérifie un droit, ne modifie aucun compte.
4. **PostgreSQL** utilise un seul rôle serveur non propriétaire/NOBYPASSRLS et les
   GRANT de colonnes + politiques restrictives. ADMIN ne devient jamais un rôle
   SQL privilégié. Aucun GRANT à anon/authenticated, Angular ou Kotlin.

JDBC ne renseigne pas automatiquement auth.uid(). Le serveur fixe
`fastcoloc.user_id` avec un paramètre lié, dans une transaction du même
EntityManager/DataSource que ses requêtes. SET LOCAL disparaît au commit/rollback,
y compris sur le pool ; l'absence de contexte ne donne aucun profil.

**Limite essentielle : le détenteur du secret JDBC peut fixer un autre UUID.**
Le GUC n'est ni signé ni une vérification JWT dans PostgreSQL. Cette RLS protège
contre une requête oubliant son WHERE sous contexte correct ; elle ne garantit
pas l'isolation après compromission du backend, de son secret ou injection SQL
arbitraire. Les tests documentent ce pouvoir du serveur au lieu de le masquer.
Les requêtes applicatives lient leurs valeurs ; les identifiants SQL viennent
de listes constantes et sont cités dans les scripts. Un nom de profil ressemblant
à du SQL reste une donnée et ne provoque aucune exécution.

Le rôle ne peut ni se promouvoir ni débloquer un compte : pas d'INSERT sur
app_role/status, pas d'UPDATE/DELETE sur account_access, pas de TRUNCATE/DDL ni
membership privilégiée. Les politiques restrictives continuent d'imposer UUID et
blocage même si une autre politique permissive est ajoutée. account_access reste
une exception de lecture pour son propre statut, sans accès à ses motifs privés.

Un blocage concurrent est évalué selon le snapshot de la commande PostgreSQL ;
il n'annule pas rétroactivement une requête déjà commencée. Toute future opération
ADMIN sur un compte devra prendre le même verrou transactionnel que sync, vérifier
son acteur côté serveur, journaliser et utiliser une capacité DB dédiée revue.
Ne pas étendre le rôle général avec UPDATE(app_role,status) pour construire l'admin.
Le premier ADMIN doit être attribué par un opérateur autorisé à un UUID Auth
vérifié, après confirmation du compte, jamais lors d'une inscription cliente.

## 6. Secrets, provisionnement et rotation

Le SQL livré ne contient aucun mot de passe. Le rôle commence NOLOGIN. Après
approbation du provisionnement seulement :

1. Vérifier le project ref et sauvegarder les ACL/policies/flags actuels par SELECT.
   Le fingerprint du schéma ne suffit pas à authentifier le projet ciblé.
2. Appliquer uniquement la migration revue avec le compte de migration autorisé.
   Vérifier RLS, droits de colonnes, absence de droits clients et flags runtime.
3. Générer un secret fort dans le gestionnaire de secrets. Utiliser le prompt
   sécurisé `\password fastcoloc_backend` de psql dans une session administrative
   TLS vérifiée, ou le mécanisme équivalent du gestionnaire. Ne pas coller le mot
   de passe dans un SQL sauvegardé, une URL JDBC, un argument CLI ou un log.
4. Activer LOGIN séparément, puis stocker le secret comme
   FASTCOLOC_DB_PASSWORD. Le pooler utilise le login suffixé
   fastcoloc_backend.nklsbubnyznlfpxpxsrp ; current_user doit rester fastcoloc_backend.
5. Faire un test JDBC non administrateur en READ ONLY puis une recette avec un
   compte de test autorisé pour sync/me. Vérifier le rôle réellement reçu et le
   comportement TLS du chemin choisi avant d'ouvrir le trafic.

Spring ne charge pas .env automatiquement. Pour un usage local, un fichier
.env.local ou application-*.local.properties/yml/yaml peut être utilisé par un
chargeur explicitement configuré et doit avoir une ACL OS limitée au compte de
service. En déploiement, préférer l'injection du gestionnaire de secrets et un
compte de service dédié. Les environnements Postman exportés avec tokens sont
ignorés ; seuls les exemples vides/publics restent visibles.

Rotation avec le rôle unique compatible avec le garde actuel :

- Préparer le déploiement et la nouvelle version du secret, conserver un chevauchement
  d'instances et drainer progressivement les requêtes. Une modification du mot de
  passe PostgreSQL n'interrompt pas les connexions déjà authentifiées.
- Changer le secret via canal sécurisé, publier la version au service, puis effectuer
  le redémarrage progressif des instances/pools avec les nouveaux identifiants.
  Un changement de variable d'environnement ne met pas à jour un Hikari déjà lancé.
- Tester de **nouvelles connexions** et sync/me avec un compte de recette ; /health
  est une liveness et ne prouve pas les droits DB. Vérifier le comportement réel
  du pooler et l'éventuelle propagation de son authentification.
- Les anciennes instances qui doivent recréer une connexion pendant la transition
  peuvent échouer : PostgreSQL n'a pas deux mots de passe actifs pour ce rôle.
  Ne pas promettre zéro interruption. Ne pas relancer globalement le pooler.
- Pour une exigence stricte de chevauchement de secrets, préparer ultérieurement
  deux logins et un groupe de capacités NOLOGIN, avec memberships, policies et
  RuntimeDatabaseGuard adaptés et testés. Ce design n'est pas livré ni autorisé ici.

Le mot de passe administrateur partagé antérieurement n'a pas été réutilisé pour
cette revue et doit être remplacé par l'utilisateur. Aucun secret réel n'est copié
dans les fichiers. Un scan de motifs de clés privées, clés sb_secret et URL JDBC
avec mot de passe n'a trouvé aucun fichier correspondant sous FastColoc hors
artefacts générés ; cela reste un contrôle ciblé, pas une preuve universelle.
Les règles .gitignore ont été vérifiées avec **git check-ignore** dans un dépôt
jetable sous target ; le workspace n'a pas de dépôt/index Git permettant un audit
des fichiers effectivement suivis.

## 7. Retour arrière et conséquences

Avant activation, l'arrêt volontaire des nouvelles connexions est attendu car
NOLOGIN est intentionnel. Le rôle ne rend pas les clients capables de lire le
schéma privé. Il n'ajoute aucune table, aucun trigger ou fonction métier.
Les DDL de policies peuvent prendre des verrous et attendre au plus 5 secondes ;
un échec avant COMMIT laisse la transaction inchangée.

Le rollback livré est une **quarantaine sûre**, pas une reconstruction aveugle
de droits antérieurs :

1. Arrêter/drainer les pools applicatifs ; NOLOGIN seul ne coupe pas les sessions.
2. Exécuter le rollback transactionnel revu : NOLOGIN, retrait des droits tables,
   colonnes, schéma, séquences, fonctions et des seules policies fc_runtime_*.
3. Vérifier qu'aucun accès applicatif ne subsiste. La révocation s'applique aux
   commandes suivantes ; une commande déjà lancée peut encore finir. En incident,
   toute terminaison ciblée des sessions exige une action opérateur distincte.
4. Garder les données, le rôle et son marqueur. Ne jamais DROP OWNED/CASCADE,
   restaurer un CREATE PUBLIC partagé ou supprimer les tables pour revenir en arrière.
5. Pour restaurer le service, réexaminer/appliquer la base, les éventuelles capacités
   approuvées puis réactiver LOGIN avec le secret correct. Un retour aux anciennes
   ACL nécessite la sauvegarde préalable et une nouvelle revue explicite.

## 8. Actions réellement exécutées et preuves

- Lecture complète de la proposition initiale, mappings, services et sécurité.
- SELECT distants de métadonnées sous BEGIN READ ONLY : rôle absent, aucune policy
  fastcoloc, quatre séquences privées ; PUBLIC n'a pas CREATE dans public ; une
  fonction public SECURITY DEFINER rls_auto_enable, exécutable postgres/service_role
  uniquement. Aucune ligne métier lue, aucune écriture distante.
- Création du fichier de migration via CLI local, sans link/push/repair/reset.
- Provisionnement, réapplication, droits optionnels et rollback uniquement sur
  PostgreSQL 14.20 local isolé avec rôle runtime non administrateur.
- Ajout de tests de droits SQL directs : BLOCKED, colonnes, quatre séquences,
  ADMIN sans privilège SQL supplémentaire, contextes absents/malformés, policies
  permissives supplémentaires, droits étrangers, memberships clientes, cinq étapes,
  rollback, préservation du mot de passe/LOGIN et retrait des grants parasites.
- Contrôles Git-ignore et scan ciblé de secrets, sans afficher de valeur secrète.

Les résultats définitifs de `mvn.cmd -B -ntp verify` sont dans
`backend/target/surefire-reports` et `backend/target/provision-review-tests.log`.
Résultat exécuté : **BUILD SUCCESS — 35 tests, 0 échec, 0 erreur, 0 ignoré**.
Répartition : BackendIntegrationTest 28, RuntimeDatabaseGuardTest 3,
SupabaseAuthIdentityVerifierTest 4. Les 11 tests ajoutés complètent les 24 tests
initiaux ; le test de lecture JPA inutilisée a été adapté au refus voulu par
les nouveaux droits minimaux. Le JAR exécutable a été reconstruit.
La recette distante sous rôle dédié, la connexion au pooler avec ce rôle, la
validation des identifiants et l'activation du module onboarding restent à faire
après revue et autorisation. Les tests de droits futurs ne sont pas des tests
d'endpoints d'onboarding qui n'existent pas encore.

Le diagnostic JDBC précédent indique TLS Java vers pooler validé mais
pg_stat_ssl=false pour pooler vers PostgreSQL : aucun chiffrement de bout en bout
n'est affirmé. Le provisionnement des privilèges ne change pas ce chemin réseau.

Références primaires consultées :

- https://www.postgresql.org/docs/17/sql-grant.html
- https://www.postgresql.org/docs/17/sql-revoke.html
- https://www.postgresql.org/docs/17/ddl-rowsecurity.html
- https://www.postgresql.org/docs/17/sql-alterrole.html
- https://supabase.com/docs/guides/database/postgres/row-level-security
