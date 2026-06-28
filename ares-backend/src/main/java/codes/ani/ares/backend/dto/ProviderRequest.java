package codes.ani.ares.backend.dto;

public record ProviderRequest(
    String prompt,
    String model,
    String githubToken
) {}
