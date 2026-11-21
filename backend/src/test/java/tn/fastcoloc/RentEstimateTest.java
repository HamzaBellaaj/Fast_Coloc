package tn.fastcoloc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.fastcoloc.ai.*;
import tn.fastcoloc.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RentEstimateTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RentEstimateRequest logement = new RentEstimateRequest("Ariana", "S+2", "Centre",
        "Appartement de 85 m², meublé, lumineux et entretenu", 2, List.of("Climatisation", "WiFi"));

    @Test void endpointForwardsOnePhotoAndDescriptionAndRejectsInvalidResponses() throws Exception {
        var received = new AtomicReference<String>();
        var receivedBytes = new AtomicReference<byte[]>();
        var upgrade = new AtomicReference<String>();
        var response = new AtomicReference<>("""
            {"success":true,"estimation":{"disponible":true,"loyer_min_tnd":1100,
            "loyer_max_tnd":1400,"loyer_conseille_tnd":1250,"fiabilite":"moyenne","explication":"Test"}}
            """);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            receivedBytes.set(exchange.getRequestBody().readAllBytes());
            received.set(new String(receivedBytes.get(), StandardCharsets.UTF_8));
            var bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var service = new RentEstimateService(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook", 2);
            var mvc = MockMvcBuilders.standaloneSetup(new RentEstimateController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
            var data = new MockMultipartFile("logement", "", "application/json", mapper.writeValueAsBytes(logement));
            var photo = new MockMultipartFile("photo", "room.jpg", "image/jpeg", new byte[]{1,2,3});
            mvc.perform(multipart("/api/v1/ai/rent-estimate").file(data).file(photo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estimation.loyer_conseille_tnd").value(1250));
            assertTrue(received.get().contains("name=\"message\""));
            assertTrue(received.get().contains("name=\"message\"\r\n\r\n"),
                "The message must be a text field, not n8n's first binary file");
            assertNull(upgrade.get(), "n8n multipart calls must not request an h2c upgrade");
            assertTrue(received.get().contains("name=\"photo\""));
            assertTrue(received.get().contains("85 m²"));
            assertFalse(received.get().contains("name=\"file0\""));
            mvc.perform(multipart("/api/v1/ai/rent-estimate").file(data)
                    .file(coloredPhoto(java.awt.Color.RED)).file(coloredPhoto(java.awt.Color.BLUE)))
                .andExpect(status().isOk());
            assertTrue(received.get().contains("les 2 photos numérotées"));
            // The existing file0-only workflow receives both views in the single photo part.
            String raw = new String(receivedBytes.get(), StandardCharsets.ISO_8859_1);
            int start = raw.indexOf("Content-Type: image/jpeg\r\n\r\n") + "Content-Type: image/jpeg\r\n\r\n".length();
            int end = raw.indexOf("\r\n--FastColoc", start);
            var overview = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
                raw.substring(start, end).getBytes(StandardCharsets.ISO_8859_1)));
            assertEquals(1280, overview.getWidth());
            assertTrue(new java.awt.Color(overview.getRGB(320, 240)).getRed() > 240);
            assertTrue(new java.awt.Color(overview.getRGB(960, 240)).getBlue() > 240);
            mvc.perform(multipart("/api/v1/ai/rent-estimate").file(data)).andExpect(status().isBadRequest());
            response.set("{\"success\":false}");
            mvc.perform(multipart("/api/v1/ai/rent-estimate").file(data).file(photo))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("ESTIMATE_UNAVAILABLE"));
            response.set("not JSON");
            assertThrows(ApiException.class, () -> service.estimate(logement, List.of(photo)));
        } finally { server.stop(0); }
    }

    private MockMultipartFile coloredPhoto(java.awt.Color color) throws Exception {
        var image = new java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color); graphics.fillRect(0, 0, 80, 60); graphics.dispose();
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("photo", "room.png", "image/png", bytes.toByteArray());
    }

    @Test @EnabledIfSystemProperty(named="rent.live", matches="true")
    void existingWorkflowReturnsAnActualEstimate() throws Exception {
        var photo = new MockMultipartFile("photo", "logement.jpg", "image/jpeg",
            Files.readAllBytes(Path.of("../frontend/public/assets/coloc-interior.jpg")));
        var service = new RentEstimateService(mapper,
            "http://localhost:32768/webhook/49bec6bd-ad64-4cee-ae90-d0567511a05d", 180);
        var second = new MockMultipartFile("photo", "autre-vue.png", "image/png",
            Files.readAllBytes(Path.of("../frontend/public/assets/apartment-poster.png")));
        var result = service.estimate(logement, List.of(photo, second));
        assertTrue(result.success());
        assertTrue(result.estimation().disponible());
        Files.writeString(Path.of("target/rent-estimate-live.json"), mapper.writeValueAsString(result));
    }
}
