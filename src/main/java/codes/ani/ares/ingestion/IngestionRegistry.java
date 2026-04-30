package codes.ani.ares.ingestion;

import codes.ani.ares.exception.UnsupportedProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Central registry that resolves a source URI to its matching {@link IngestionProvider}.
 *
 * <p>Aggregates all available {@link IngestionProvider} beans injected by Spring and
 * selects the first provider whose {@code supports(String)} predicate matches the
 * requested URI. This service acts as the routing layer between the REST controller
 * and the provider implementations.</p>
 *
 * <p>Registered as a Spring {@link Service} and participates in the default
 * singleton-scope bean lifecycle.</p>
 */
@Service
@RequiredArgsConstructor
public class IngestionRegistry {
    private final List<IngestionProvider> providers;

    /**
     * Resolves a source URI to the first matching {@link IngestionProvider}.
     *
     * @param sourceUri the source URI to resolve
     * @return the matching {@link IngestionProvider}
     * @throws UnsupportedProviderException if no provider supports the given URI
     */
    public IngestionProvider getProvider(String sourceUri) {
        return providers.stream().filter(provider -> provider.supports(sourceUri)).findFirst().orElseThrow(() -> new UnsupportedProviderException(sourceUri));
    }
}
