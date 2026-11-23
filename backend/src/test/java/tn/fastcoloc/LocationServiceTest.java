package tn.fastcoloc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.geography.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationServiceTest {
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="fastcolocGeoLive",matches="true")
    void realProvidersIdentifyTunisLandLakeAndSea() {
        var real = new LocationService(new LocationGateway("https://nominatim.openstreetmap.org", "https://titiler.terrascope.be", "FastColoc/0.1 (https://github.com/HamzaBellaaj/Fast_Coloc)"));
        assertEquals(36.8065, real.validate(36.8065,10.1815).latitude());
        assertEquals("INVALID_POSITION", assertThrows(ApiException.class, () -> real.validate(36.822,10.249)).code());
        assertEquals("INVALID_POSITION", assertThrows(ApiException.class, () -> real.validate(36.88,10.5)).code());
    }
    final ObjectMapper json = new ObjectMapper();
    final LocationGateway gateway = mock(LocationGateway.class);
    final LocationService service = new LocationService(gateway);
    @ParameterizedTest @ValueSource(ints = {80,90,95})
    void waterAndWetlandsAreRejectedBeforeReverseGeocoding(int code) throws Exception {
        when(gateway.landCover(36.8,10.2)).thenReturn(json.readTree("{\"values\":["+code+"]}"));
        var error = assertThrows(ApiException.class, () -> service.validate(36.8,10.2));
        assertEquals("INVALID_POSITION", error.code());
        verify(gateway,never()).reverse(anyDouble(),anyDouble());
    }
    @Test void missingRasterDataIsNotAssumedLand() throws Exception {
        when(gateway.landCover(36.8,10.2)).thenReturn(json.readTree("{\"values\":[null]}"));
        assertEquals(503, assertThrows(ApiException.class, () -> service.validate(36.8,10.2)).status().value());
    }
    @Test void validLandReturnsApproximateAddressAndCachesExactPointOnly() throws Exception {
        when(gateway.landCover(anyDouble(),anyDouble())).thenReturn(json.readTree("{\"values\":[50]}"));
        when(gateway.reverse(anyDouble(),anyDouble())).thenReturn(json.readTree("{\"lat\":\"36.8\",\"lon\":\"10.2\",\"address\":{\"country_code\":\"tn\",\"road\":\"Avenue de test\",\"city\":\"Tunis\"}}"));
        var point = service.validate(36.8,10.2);
        assertEquals("Avenue de test, Tunis", point.address()); assertTrue(point.approximate());
        assertEquals(point, service.validate(36.8,10.2));
        service.validate(36.800001,10.2);
        verify(gateway,times(2)).landCover(anyDouble(),anyDouble());
    }
    @Test void boundsAndCountryAreCheckedIndependentlyOfRaster() throws Exception {
        assertThrows(ApiException.class, () -> service.validate(Double.NaN,10));
        assertThrows(ApiException.class, () -> service.validate(40,10));
        when(gateway.landCover(36.8,10.2)).thenReturn(json.readTree("{\"values\":[50]}"));
        when(gateway.reverse(36.8,10.2)).thenReturn(json.readTree("{\"address\":{\"country_code\":\"dz\"}}"));
        assertEquals("INVALID_POSITION", assertThrows(ApiException.class, () -> service.validate(36.8,10.2)).code());
    }
    @Test void searchReturnsChoicesWithoutValidatingOrSelectingThem() throws Exception {
        when(gateway.search("Tunis")).thenReturn(json.readTree("[{\"display_name\":\"Tunis\",\"lat\":\"36.8\",\"lon\":\"10.2\",\"address\":{\"country_code\":\"tn\"}},{\"lat\":\"oops\",\"lon\":\"10\"}]"));
        assertEquals(1,service.search("Tunis").size());
        verify(gateway,never()).landCover(anyDouble(),anyDouble());
    }
    @Test void serviceFailureNeverCachesAPointAsValid() throws Exception {
        when(gateway.landCover(36.8,10.2)).thenThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"GEOGRAPHY_UNAVAILABLE","Unavailable"));
        assertThrows(ApiException.class, () -> service.validate(36.8,10.2));
        assertThrows(ApiException.class, () -> service.validate(36.8,10.2));
        verify(gateway,times(2)).landCover(36.8,10.2);
    }
}
