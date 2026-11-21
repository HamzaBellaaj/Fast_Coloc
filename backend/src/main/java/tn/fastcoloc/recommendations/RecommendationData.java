package tn.fastcoloc.recommendations;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.listings.ListingService;
import tn.fastcoloc.profiles.ProfileDataService;
import tn.fastcoloc.security.AccountAuthorizationService;
import tn.fastcoloc.security.DatabaseIdentity;

/** Short database transactions, never held open while the local model is running. */
@Service
public class RecommendationData {
    private final ProfileDataService data;
    private final ListingService listings;
    private final DatabaseIdentity identity;
    private final AccountAuthorizationService access;

    public RecommendationData(ProfileDataService data, ListingService listings, DatabaseIdentity identity,
                              AccountAuthorizationService access) {
        this.data = data; this.listings = listings; this.identity = identity; this.access = access;
    }

    public record Snapshot(Map<String,Object> payload, Set<UUID> candidateIds, boolean profileIncomplete) {}

    @Transactional
    public Snapshot snapshot(UUID actor) {
        identity.bind(actor); access.role(actor);
        var profile = new LinkedHashMap<String,Object>();
        var preferences = data.rows("select min_budget,max_budget,move_in_date from fastcoloc.roommate_preferences where user_id=?", actor);
        if (!preferences.isEmpty()) {
            var p = preferences.getFirst();
            put(profile, "budget_min_tnd", p.get("min_budget"));
            put(profile, "budget_max_tnd", p.get("max_budget"));
            put(profile, "date_emmenagement", p.get("move_in_date"));
        }
        var cities = data.rows("select l.name as ville,g.name as gouvernorat from fastcoloc.preferred_localities p join fastcoloc.localities l on l.id=p.locality_id join fastcoloc.governorates g on g.id=l.governorate_id where p.user_id=? order by g.name,l.name limit 30", actor);
        if (!cities.isEmpty()) profile.put("villes_preferees", cities.stream().map(c -> c.get("ville") + ", " + c.get("gouvernorat")).toList());
        boolean incomplete = profile.isEmpty() || data.rows("select completed_at from fastcoloc.profiles where id=? and completed_at is null", actor).size() > 0;

        var candidates = listings.recommendationCandidates(actor);
        var ids = new LinkedHashSet<UUID>();
        var annonces = new ArrayList<Map<String,Object>>();
        for (var row : candidates) {
            var id = (UUID) row.get("id"); ids.add(id);
            var annonce = new LinkedHashMap<String,Object>();
            annonce.put("id", id.toString());
            annonce.put("titre", safeText(row.get("title")));
            annonce.put("ville", row.get("locality") + ", " + row.get("governorate"));
            annonce.put("loyer_tnd", row.get("rent"));
            annonce.put("type", safeText(row.get("property_type")));
            annonce.put("capacite", row.get("capacity"));
            annonce.put("disponible_des", row.get("available_from"));
            annonce.put("equipements", Arrays.stream((Object[]) row.get("equipment")).map(RecommendationData::safeText).toList());
            annonce.put("favori", row.get("favorite"));
            annonces.add(annonce);
        }
        // Deliberate allowlist: no contacts, biography, address, photos, tokens or demographic data.
        return new Snapshot(Map.of("user_id", actor.toString(), "profil", profile, "annonces", annonces), ids, incomplete);
    }

    public Map<UUID,Map<String,Object>> currentListings(UUID actor, Collection<UUID> ids) {
        var result = new LinkedHashMap<UUID,Map<String,Object>>();
        for (var id : ids) {
            try {
                var row = listings.detail(actor, id);
                if ("PUBLISHED".equals(row.get("status")) && "OFFER".equals(row.get("kind"))
                        && !Boolean.TRUE.equals(row.get("is_hidden")) && !actor.equals(row.get("author_id"))
                        && row.get("expires_at") instanceof Instant expiry && expiry.isAfter(Instant.now()))
                    result.put(id, row);
            } catch (ApiException e) {
                if (e.status().value() != 404) throw e;
            }
        }
        return result;
    }

    private static void put(Map<String,Object> profile, String key, Object value) {
        if (value != null) profile.put(key, value);
    }
    private static String safeText(Object value) {
        if (value == null) return "";
        return value.toString().replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[contact masqué]")
            .replaceAll("(?<![\\p{L}\\d])\\+?\\d[\\d .()-]{6,}\\d(?![\\p{L}\\d])", "[contact masqué]");
    }
}
