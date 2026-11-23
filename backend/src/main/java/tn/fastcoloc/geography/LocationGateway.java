package tn.fastcoloc.geography;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tn.fastcoloc.api.ApiException;
import org.springframework.http.HttpStatus;

/** Provider URLs are server selected, never supplied by a client. */
@Component
public class LocationGateway {
    private final RestClient client;
    private final String geocoder, raster;
    private long nextGeocode;
    public LocationGateway(@Value("${fastcoloc.geo.geocoder-url:https://nominatim.openstreetmap.org}") String geocoder,
            @Value("${fastcoloc.geo.raster-url:https://titiler.terrascope.be}") String raster,
            @Value("${fastcoloc.geo.user-agent:FastColoc/0.1 (https://github.com/HamzaBellaaj/Fast_Coloc)}") String agent) {
        this.geocoder = geocoder; this.raster = raster;
        var http = java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(18));
        client = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", agent).build();
    }
    public JsonNode landCover(double latitude, double longitude) {
        int south = (int)Math.floor(latitude / 3) * 3, west = (int)Math.floor(longitude / 3) * 3;
        String tile = "https://esa-worldcover.s3.eu-central-1.amazonaws.com/v200/2021/map/"
            + String.format(java.util.Locale.ROOT, "ESA_WorldCover_10m_2021_v200_N%02dE%03d_Map.tif", south, west);
        return get(UriComponentsBuilder.fromUriString(raster + "/cog/point/" + longitude + "," + latitude).queryParam("url", tile));
    }
    public JsonNode reverse(double latitude, double longitude) {
        geocodeSlot();
        return get(UriComponentsBuilder.fromUriString(geocoder + "/reverse").queryParam("format", "jsonv2")
            .queryParam("lat", latitude).queryParam("lon", longitude).queryParam("addressdetails", 1).queryParam("accept-language", "fr"));
    }
    public JsonNode search(String query) {
        geocodeSlot();
        return get(UriComponentsBuilder.fromUriString(geocoder + "/search").queryParam("format", "jsonv2")
            .queryParam("q", query).queryParam("countrycodes", "tn").queryParam("limit", 5)
            .queryParam("addressdetails", 1).queryParam("accept-language", "fr"));
    }
    // Public Nominatim: <=1 request/second for the whole application, no autocomplete.
    private synchronized void geocodeSlot() {
        long now = System.nanoTime();
        if (now < nextGeocode) {
            try { java.util.concurrent.TimeUnit.NANOSECONDS.sleep(nextGeocode - now); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        }
        nextGeocode = System.nanoTime() + 1_100_000_000L;
    }
    private JsonNode get(UriComponentsBuilder uri) {
        try {
            var result = client.get().uri(uri.build().encode().toUri()).retrieve().body(JsonNode.class);
            if (result == null || result.has("error")) throw unavailable();
            return result;
        } catch (org.springframework.web.client.RestClientException e) {
            var failure = unavailable(); failure.initCause(e); throw failure;
        }
    }
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOGRAPHY_UNAVAILABLE", "La vérification géographique est indisponible. La position précédente est conservée. Réessaie.");
    }
}
