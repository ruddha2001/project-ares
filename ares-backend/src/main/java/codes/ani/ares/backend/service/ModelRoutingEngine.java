package codes.ani.ares.backend.service;

import codes.ani.ares.backend.adapter.ModelProviderAdapter;
import codes.ani.ares.backend.dto.JobInitializationRequest;
import codes.ani.ares.backend.dto.ProviderRequest;
import codes.ani.ares.backend.dto.ProviderResponse;
import codes.ani.ares.backend.enums.PipelineStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelRoutingEngine {

    private final List<ModelProviderAdapter> providerAdapters;

    public ProviderResponse executeStage(PipelineStage stage, JobInitializationRequest context, ProviderRequest request) {
        String targetProvider = context.routingConfiguration().get(stage);
        if (targetProvider == null) {
            targetProvider = "ollama"; // Explicit architectural baseline fallback
        }
        
        String normalizedProvider = targetProvider.trim().toLowerCase();
        return providerAdapters.stream()
                .filter(adapter -> adapter.supports(normalizedProvider))
                .findFirst()
                .map(adapter -> adapter.dispatch(request))
                .orElseThrow(() -> new IllegalStateException(
                        "Designated model provider target unresolvable: " + normalizedProvider));
    }
}
