package tn.fastcoloc.geography;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/geography")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="supabaseBearer")
public class LocationController {
    private final LocationService service;
    public LocationController(LocationService service) { this.service = service; }
    public record Coordinates(@NotNull @DecimalMin("30") @DecimalMax("38") Double latitude,
                              @NotNull @DecimalMin("7") @DecimalMax("12") Double longitude) {}
    @GetMapping("/search") public List<LocationService.Place> search(@RequestParam String q) { return service.search(q); }
    @PostMapping("/position") public LocationService.Position position(@Valid @RequestBody Coordinates point) { return service.validate(point.latitude(), point.longitude()); }
}
