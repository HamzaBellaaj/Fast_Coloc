package tn.fastcoloc.geography;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.fastcoloc.api.ApiException;

@Service
public class LocationService {
    public record Place(String address, double latitude, double longitude) {}
    public record Position(double latitude, double longitude, String address, boolean approximate, String source) {}
    private record Cached<T>(T value, long expires) {}
    private final LocationGateway gateway;
    private final Map<String, Cached<Position>> positions = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Cached<List<Place>>> searches = Collections.synchronizedMap(new LinkedHashMap<>());
    public LocationService(LocationGateway gateway) { this.gateway = gateway; }
    public List<Place> search(String text) {
        String query = text == null ? "" : text.trim();
        if (query.length() < 3 || query.length() > 250) throw invalid("Saisis une adresse de 3 à 250 caractères.");
        var cached = searches.get(query);
        if (cached != null && cached.expires > System.currentTimeMillis()) return cached.value;
        JsonNode data = gateway.search(query);
        if (!data.isArray()) throw unavailable();
        var places = new ArrayList<Place>();
        for (var item : data) {
            try {
                double lat = Double.parseDouble(item.path("lat").asText()), lon = Double.parseDouble(item.path("lon").asText());
                if (inBounds(lat, lon) && "tn".equals(item.path("address").path("country_code").asText()))
                    places.add(new Place(item.path("display_name").asText(), lat, lon));
            } catch (NumberFormatException ignored) { /* never select a malformed provider entry */ }
        }
        var result = List.copyOf(places);
        put(searches, query, result, 256);
        return result;
    }
    /** Used by preview and all listing writes, including administrative corrections. */
    public Position validate(double latitude, double longitude) {
        if (!inBounds(latitude, longitude)) throw invalid("Choisis une position en Tunisie (latitude 30–38, longitude 7–12).");
        String key = latitude + ":" + longitude; // exact coordinates: no cache cell straddling a shoreline
        var cached = positions.get(key);
        if (cached != null && cached.expires > System.currentTimeMillis()) return cached.value;
        var sample = gateway.landCover(latitude, longitude).path("values");
        if (!sample.isArray() || sample.size() != 1 || !sample.get(0).isNumber()) throw unavailable();
        double value = sample.get(0).asDouble();
        if (value == 80 || value == 90 || value == 95) throw invalid("Cette position se trouve dans l’eau ou une zone humide. Choisis un point terrestre.");
        if (!Set.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 100.0).contains(value)) throw unavailable();
        var location = gateway.reverse(latitude, longitude);
        var address = location.path("address");
        if (!"tn".equals(address.path("country_code").asText())) throw invalid("La position doit être située en Tunisie.");
        String road = first(address, "road", "pedestrian", "residential", "footway");
        String city = first(address, "city", "town", "village", "municipality", "suburb");
        String number = address.path("house_number").asText("");
        boolean approximate = road.isBlank() || number.isBlank();
        try {
            double objectLat = Double.parseDouble(location.path("lat").asText()), objectLon = Double.parseDouble(location.path("lon").asText());
            approximate |= Math.hypot(objectLat - latitude, (objectLon - longitude) * Math.cos(Math.toRadians(latitude))) * 111_320 > 50;
        } catch (NumberFormatException e) { approximate = true; }
        String street = String.join(", ", java.util.stream.Stream.of((number + " " + road).trim(), city).filter(s -> !s.isBlank()).toList());
        if (street.length() > 250) street = street.substring(0, 250);
        var result = new Position(latitude, longitude, street, approximate, "ESA WorldCover 2021 v200 (10 m) + OpenStreetMap");
        put(positions, key, result, 1024);
        return result;
    }
    private static boolean inBounds(double lat, double lon) { return Double.isFinite(lat) && Double.isFinite(lon) && lat >= 30 && lat <= 38 && lon >= 7 && lon <= 12; }
    private static String first(JsonNode node, String... keys) { for (String key : keys) if (!node.path(key).asText("").isBlank()) return node.path(key).asText(); return ""; }
    private static <T> void put(Map<String,Cached<T>> map, String key, T value, int limit) {
        synchronized (map) { if (map.size() >= limit) map.remove(map.keySet().iterator().next()); map.put(key, new Cached<>(value, System.currentTimeMillis() + 900_000)); }
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POSITION", message); }
    private static ApiException unavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOGRAPHY_UNAVAILABLE", "Impossible de confirmer que ce point est terrestre. Choisis un autre point ou réessaie plus tard."); }
}
