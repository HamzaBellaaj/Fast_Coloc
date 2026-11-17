# Éditeur commun, parcours OFFER / REQUEST

- Deux cartes de choix puis quatre étapes adaptées, sur Angular et Kotlin/XML.
- REQUEST : rent reste le montant mensuel de recherche, localityId la localité souhaitée, capacity le nombre de personnes, availableFrom la date souhaitée. Pas de nouveau champ métier ni de prix fictif.
- La position et l’adresse du logement restent obligatoires pour OFFER. REQUEST conserve sa localité obligatoire, mais accepte une adresse vide et un couple GPS absent. Si une position est fournie, les deux coordonnées et les bornes tunisiennes restent contrôlées.
- Migration appliquée une seule fois : 20261117062435_listing_request_location.sql. Aucun historique rejoué ni enregistrement existant modifié. Les contraintes de dates, prix, durée, version, statut et les autorisations sont conservées.
- Les champs et photos sont conservés lors des changements de type. REQUEST ne sollicite pas de photos. Récapitulatif avant publication ; sauvegarde de la version courante puis publication ; en cas d’échec, le brouillon enregistré reste modifiable.
- US29 garde son interface existante et ses actions.

Vérifications : compilation Angular et APK ; test HTTP réel Spring Boot/PostgreSQL isolé couvrant création/publication OFFER et REQUEST sans GPS, refus d’une OFFER incomplète, des dates inversées et d’une position partielle ; test Web de conservation après échec de publication ; tests Kotlin de validation (dont REQUEST sans adresse/GPS).
