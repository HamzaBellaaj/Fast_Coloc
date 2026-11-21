package tn.fastcoloc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import tn.fastcoloc.recommendations.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecommendationServiceTest {
    @Test void validatesIdsPreservesAiOrderAndRealListingsAndCapsAtTen() throws Exception {
        var data = mock(RecommendationData.class);
        var gateway = mock(RecommendationGateway.class);
        var actor = UUID.randomUUID();
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 12; i++) ids.add(UUID.randomUUID());
        var payload = Map.<String,Object>of("profil", Map.of("budget_max_tnd",600), "annonces", List.of());
        when(data.snapshot(actor)).thenReturn(new RecommendationData.Snapshot(payload, new LinkedHashSet<>(ids), false));
        var json = new ObjectMapper(); var response = json.createObjectNode();
        response.put("success", true); var ranking = response.putArray("recommendations");
        ranking.addObject().put("annonce_id", UUID.randomUUID().toString()).put("score",100).put("raison","ID inventé");
        Collections.swap(ids, 0, 1);
        for (int i = 0; i < ids.size(); i++) {
            ranking.addObject().put("annonce_id",ids.get(i).toString()).put("score",i == 0 ? 40 : 120).put("raison","Raison IA").put("titre","Titre inventé");
        }
        ranking.insert(2, ranking.get(1).deepCopy());
        when(gateway.recommend(payload)).thenReturn(response);
        when(data.currentListings(eq(actor),anyCollection())).thenAnswer(call -> {
            Collection<UUID> selected = call.getArgument(1);
            assertEquals(10, selected.size());
            var rows = new HashMap<UUID,Map<String,Object>>();
            for (var id : selected) if (!id.equals(ids.get(2))) rows.put(id,Map.of("id",id,"title","Titre réel","rent",450));
            return rows;
        });
        var result = new RecommendationService(data,gateway).recommend(actor);
        assertEquals(9,result.recommendations().size()); // An ad was removed during the AI calculation.
        assertEquals(ids.get(0),result.recommendations().get(0).listing().get("id"));
        assertEquals(ids.get(1),result.recommendations().get(1).listing().get("id"));
        assertEquals(40,result.recommendations().get(0).score()); // No extra sorting by the backend.
        assertEquals(100,result.recommendations().get(1).score());
        assertTrue(result.recommendations().stream().allMatch(item -> item.listing().get("title").equals("Titre réel")));
        verify(gateway,times(1)).recommend(payload);
    }

    @Test void noCandidatesSkipsAiAndInvalidResponsesAreRetryable() throws Exception {
        var data = mock(RecommendationData.class); var gateway = mock(RecommendationGateway.class);
        var actor = UUID.randomUUID();
        when(data.snapshot(actor)).thenReturn(new RecommendationData.Snapshot(Map.of(), Set.of(), true));
        var service = new RecommendationService(data,gateway);
        assertTrue(service.recommend(actor).recommendations().isEmpty());
        verifyNoInteractions(gateway);
        when(data.snapshot(actor)).thenReturn(new RecommendationData.Snapshot(Map.of(), Set.of(UUID.randomUUID()), true));
        when(gateway.recommend(anyMap())).thenReturn(new ObjectMapper().readTree("{\"success\":false}"));
        var error = assertThrows(tn.fastcoloc.api.ApiException.class,() -> service.recommend(actor));
        assertEquals(502,error.status().value());
    }
}
