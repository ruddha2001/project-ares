package codes.ani.ares.backend.adapter;

import codes.ani.ares.backend.dto.ProviderRequest;
import codes.ani.ares.backend.dto.ProviderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ClaudeProviderAdapter implements ModelProviderAdapter {

    private final RestClient restClient;
    private final String defaultModel;
    private final String apiKey;

    public ClaudeProviderAdapter(RestClient.Builder restClientBuilder,
                                 @Value("${ares.provider.claude.url:https://api.anthropic.com}") String baseUrl,
                                 @Value("${ares.provider.claude.default-model:claude-3-5-sonnet-latest}") String defaultModel,
                                 @Value("${ares.provider.claude.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.defaultModel = defaultModel;
        this.apiKey = apiKey;
    }

    @Override
    public boolean supports(String providerSignature) {
        if (providerSignature == null) {
            return false;
        }
        return providerSignature.trim().toLowerCase().contains("claude");
    }

    @Override
    public ProviderResponse dispatch(ProviderRequest request) {
        log.info("Dispatching task to Claude advanced reasoning model...");
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = defaultModel;
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", request.prompt()
                ))
        );

        try {
            Map<String, Object> responseMap = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey != null ? apiKey : "")
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (responseMap == null) {
                throw new IllegalStateException("Response body from Claude was null");
            }

            String content = extractContent(responseMap);
            return new ProviderResponse(content);
        } catch (Exception e) {
            log.error("Failed to execute Claude dispatch", e);
            throw new IllegalStateException("Claude execution failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> responseMap) {
        try {
            if (responseMap.containsKey("content")) {
                Object contentObj = responseMap.get("content");
                if (contentObj instanceof List && !((List<?>) contentObj).isEmpty()) {
                    Map<String, Object> contentMap = (Map<String, Object>) ((List<?>) contentObj).get(0);
                    if (contentMap.containsKey("text")) {
                        return (String) contentMap.get("text");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse standard Claude response format, falling back: {}", e.getMessage());
        }
        if (responseMap.containsKey("response")) {
            return (String) responseMap.get("response");
        }
        return responseMap.toString();
    }
}
