package codes.ani.ares.backend.dto;

import codes.ani.ares.backend.enums.PipelineStage;
import java.util.Map;
import java.util.UUID;

public record JobInitializationRequest(
    UUID projectId,
    String repositoryUrl,
    String featureSpecUrl,
    String rawSpecificationText,
    String localGitDiff,
    Map<PipelineStage, String> routingConfiguration
) {
    public JobInitializationRequest {
        if (routingConfiguration == null) {
            routingConfiguration = Map.of();
        }
    }
}
