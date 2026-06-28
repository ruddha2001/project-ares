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
public class OllamaProviderAdapter implements ModelProviderAdapter {

    private final RestClient restClient;

    public OllamaProviderAdapter(RestClient.Builder restClientBuilder,
                                 @Value("${OLLAMA_API_URL:http://localhost:11434}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public boolean supports(String providerSignature) {
        if (providerSignature == null) {
            return false;
        }
        return providerSignature.trim().toLowerCase().contains("ollama");
    }

    @Override
    public ProviderResponse dispatch(ProviderRequest request) {
        log.info("Dispatching task to Ollama at /api/v1/chat...");
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = "llama3";
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", request.prompt())),
                "stream", false
        );

        try {
            Map<String, Object> responseMap = restClient.post()
                    .uri("/api/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (responseMap == null) {
                throw new IllegalStateException("Response body from Ollama was null");
            }

            String content = extractContent(responseMap);
            return new ProviderResponse(content);
        } catch (Exception e) {
            log.error("Failed to execute Ollama inference dispatch", e);
            throw new IllegalStateException("Ollama inference execution failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> responseMap) {
        // Handle Ollama chat response: {"message": {"content": "..."}}
        if (responseMap.containsKey("message")) {
            Object messageObj = responseMap.get("message");
            if (messageObj instanceof Map) {
                Map<String, Object> messageMap = (Map<String, Object>) messageObj;
                if (messageMap.containsKey("content")) {
                    return (String) messageMap.get("content");
                }
            }
        }
        // Handle OpenAI format: {"choices": [{"message": {"content": "..."}}]}
        if (responseMap.containsKey("choices")) {
            Object choicesObj = responseMap.get("choices");
            if (choicesObj instanceof List && !((List<?>) choicesObj).isEmpty()) {
                Object choiceObj = ((List<?>) choicesObj).get(0);
                if (choiceObj instanceof Map) {
                    Map<String, Object> choiceMap = (Map<String, Object>) choiceObj;
                    if (choiceMap.containsKey("message")) {
                        Object msgObj = choiceMap.get("message");
                        if (msgObj instanceof Map) {
                            Map<String, Object> msgMap = (Map<String, Object>) msgObj;
                            if (msgMap.containsKey("content")) {
                                return (String) msgMap.get("content");
                            }
                        }
                    }
                }
            }
        }
        if (responseMap.containsKey("response")) {
            return (String) responseMap.get("response");
        }
        return responseMap.toString();
    }
}
