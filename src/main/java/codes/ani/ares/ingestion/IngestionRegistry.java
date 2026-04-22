package codes.ani.ares.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionRegistry {
    private final List<IngestionProvider> providers;

    public IngestionProvider getProvider(String sourceUri) {
        return providers.stream().filter(provider -> provider.supports(sourceUri)).findFirst().orElseThrow(() -> new IllegalArgumentException("No ingestion provider found for URI: " + sourceUri));
    }
}
