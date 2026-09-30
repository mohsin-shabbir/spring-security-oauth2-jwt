package dev.mohsin.security;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
@RestController
public class CollectionController {
    public record Request(@NotBlank String agreementId, @Positive long amountMinor) {}
    @GetMapping("/health") Map<String,String> health() { return Map.of("status", "UP"); }
    @GetMapping("/api/collections") Map<String,Object> read() {
        return Map.of("items", java.util.List.of(Map.of("agreementId", "DEMO-001", "amountMinor", 12500)));
    }
    @PostMapping("/api/collections") ResponseEntity<Map<String,Object>> create(@Valid @RequestBody Request request) {
        // Authorization demonstration, deliberately no durable payment write.
        return ResponseEntity.accepted().body(Map.of("reference", UUID.randomUUID(), "status", "demo-validated",
                "agreementId", request.agreementId(), "amountMinor", request.amountMinor()));
    }
}

