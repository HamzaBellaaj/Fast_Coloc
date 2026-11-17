# Modèle Sprint 1 — corrigé après lecture des documents originaux

Sources : `Projet 2fastcoloc (1).pdf`, p.1 section II.1 et p.2 administration; `sprint0_pi.pdf`, p.5-6 besoins, p.6-9 backlog et p.9-11 répartition/architecture; `FastColoc_repartition_web_mobile_coches.docx`, matrice et décisions. Documents lus intégralement, originaux conservés intacts.

## Les cinq étapes exactes (remplacent les hypothèses initiales)

| Étape | Champs du cahier des charges | Tables proposées |
|---|---|---|
| 1 Informations générales | prénom, nom, sexe, âge, mot de passe; e-mail pour Supabase | profiles; identité e-mail/mot de passe uniquement auth.users |
| 2 Géographie | pays, rue, code postal | profile_addresses + countries/governorates/localities |
| 3 Informations personnelles | activités principales, loisirs, nationalité, langues, religion, fumeur/non, propreté, présentation | personal_details |
| 4 Coordonnées | portable, téléphone résidentiel, disponibilité d'appel, MSN, Skype, site Web | contact_details |
| 5 Images | plusieurs images, nombre/taille limités, descriptions | profile_photos + bucket privé |

Chaque étape a sa page Angular et son Fragment Android. Sexe, religion et informations personnelles proposent une sélection neutre. NULL = non renseigné/préfère ne pas répondre, différent de FALSE pour fumeur. Le cahier ne fixe ni nombre maximal ni taille maximale des photos; décider ces valeurs avant implémentation. MSN/Skype sont explicitement cités : les garder comme texte facultatif dans le modèle, faire confirmer leur remplacement éventuel sans inventer une intégration externe.

Âge en années saisi, garde technique provisoire 0..130; âge minimum à confirmer, aucune date de naissance supplémentaire collectée. Adresse précise, religion et contacts restent privés, absents des futures projections publiques. Les champs sont nullable pendant onboarding; validation finale serveur selon règles confirmées.

## Relations

UUID profiles.id = auth.users.id, FK avec cascade. account_access séparé pour USER/ADMIN, ACTIVE/BLOCKED et motif. Pays → gouvernorat → localité, adresse actuelle distincte des zones recherchées. Tables personnelles/contacts/adresse : 0..1 par profil pendant inscription. Photos multiples avec caption, taille, MIME, rang unique et au plus une principale. onboarding_steps : validation serveur 1..5; completed_at seulement après validation transactionnelle complète. consent_records proposé en supplément, contenu/version à confirmer. account_access_events pour actions admin.

roommate_preferences (budgets TND/disponibilité) et preferred_localities sont des extensions de recherche, PAS les étapes 3 et 4 de l'inscription. Préférences JSON provisoires à typer après règles détaillées; ne pas dupliquer religion/contacts dans ce JSON.

## SQL préparatoire

`backend/src/main/resources/db/drafts/V001__sprint1_proposal.sql` : hors exécution automatique. Schéma privé fastcoloc, RLS activée, accès anon/authenticated révoqué. Les clients passent par Spring. Aucun rôle JDBC runtime, policy Storage, seed ADMIN ou trigger Auth ajouté; ces droits devront être définis et testés avant usage. Aucun SQL exécuté ni testé sur PostgreSQL dans cette mission.

JPA ne reçoit pas automatiquement auth.uid() : contrôle sub/statut/rôle serveur obligatoire et compte JDBC dédié. Tester les droits sous rôle non propriétaire. Les photos doivent être servies par URLs signées courtes; liens déjà émis valides jusqu'à expiration même après blocage. Suppression Auth/Storage/DB nécessite workflow idempotent et reprise, pas de transaction distribuée prétendue.