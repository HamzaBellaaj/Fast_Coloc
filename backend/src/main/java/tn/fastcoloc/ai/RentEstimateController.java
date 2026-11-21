package tn.fastcoloc.ai;

import jakarta.validation.Valid;
import java.util.List;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai")
@SecurityRequirement(name="supabaseBearer")
public class RentEstimateController {
    private final RentEstimateService service;
    public RentEstimateController(RentEstimateService service) { this.service = service; }
    @PostMapping(value="/rent-estimate", consumes="multipart/form-data")
    public RentEstimateResponse estimate(@Valid @RequestPart("logement") RentEstimateRequest logement,
                                         @RequestPart("photo") List<MultipartFile> photos) {
        return service.estimate(logement, photos);
    }
}
