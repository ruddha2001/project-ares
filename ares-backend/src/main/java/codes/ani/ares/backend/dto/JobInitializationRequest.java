package codes.ani.ares.backend.dto;

import java.util.Map;
import java.util.UUID;

public record JobInitializationRequest(
    UUID projectId,
    String repositoryUrl,
    String featureSpecUrl,
    String rawSpecificationText,
    String localGitDiff,
    Map<String, String> routingConfiguration
) {}
