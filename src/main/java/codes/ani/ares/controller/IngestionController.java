package codes.ani.ares.controller;

import codes.ani.ares.dto.request.IngestionRequest;
import codes.ani.ares.ingestion.IngestionRegistry;
import codes.ani.ares.ingestion.model.SourceData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {
    private final IngestionRegistry registry;

    @PostMapping("/ingest")
    public CompletableFuture<ResponseEntity<SourceData>> ingest(
            @Valid @RequestBody IngestionRequest request
    ) {
        log.info("Received ingestion request for URI: {}", request.getSourceUri());

        var provider = registry.getProvider(request.getSourceUri());

        return provider.ingest(request.getSourceUri())
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    log.error("Ingestion failed for URI: {}", request.getSourceUri(), ex);
                    return ResponseEntity.status(500).build();
                });
    }
}
