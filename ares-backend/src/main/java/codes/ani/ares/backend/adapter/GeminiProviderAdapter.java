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
public class GeminiProviderAdapter implements ModelProviderAdapter {

    private final RestClient restClient;
    private final String defaultModel;
    private final String apiKey;

    public GeminiProviderAdapter(RestClient.Builder restClientBuilder,
                                 @Value("${ares.provider.gemini.url:https://generativelanguage.googleapis.com}") String baseUrl,
                                 @Value("${ares.provider.gemini.default-model:gemini-1.5-flash}") String defaultModel,
                                 @Value("${ares.provider.gemini.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.defaultModel = defaultModel;
        this.apiKey = apiKey;
    }

    @Override
    public boolean supports(String providerSignature) {
        if (providerSignature == null) {
            return false;
        }
        return providerSignature.trim().toLowerCase().contains("gemini");
    }

    @Override
    public ProviderResponse dispatch(ProviderRequest request) {
        log.info("Dispatching task to Gemini enterprise cloud...");
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = defaultModel;
        }

        String uri = String.format("/v1beta/models/%s:generateContent", model);
        if (apiKey != null && !apiKey.isBlank()) {
            uri += "?key=" + apiKey;
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of(
                                "text", request.prompt()
                        ))
                ))
        );

        try {
            Map<String, Object> responseMap = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (responseMap == null) {
                throw new IllegalStateException("Response body from Gemini was null");
            }

            String content = extractContent(responseMap);
            return new ProviderResponse(content);
        } catch (Exception e) {
            log.error("Failed to execute Gemini enterprise dispatch", e);
            throw new IllegalStateException("Gemini execution failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> responseMap) {
        try {
            if (responseMap.containsKey("candidates")) {
                List<?> candidates = (List<?>) responseMap.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
                    Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                    List<?> parts = (List<?>) contentMap.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map<String, Object> part = (Map<String, Object>) parts.get(0);
                        return (String) part.get("text");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse standard Gemini response format, falling back: {}", e.getMessage());
        }
        if (responseMap.containsKey("response")) {
            return (String) responseMap.get("response");
        }
        return responseMap.toString();
    }
}
