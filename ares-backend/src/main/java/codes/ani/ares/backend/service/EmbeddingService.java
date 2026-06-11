package codes.ani.ares.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {
    private final RestClient.Builder restClientBuilder;
    private RestClient ollamaClient;

    @Value("${INFERENCE_URL:http://inference-sidecar:11434}")
    private String inferenceUrl;

    @PostConstruct
    public void init() {
        this.ollamaClient = restClientBuilder.baseUrl(inferenceUrl).build();
    }

    @SuppressWarnings("unchecked")
    public String getLocalEmbedding(String textPrompt) {
        log.info("Requesting vector embedding extraction from local inference-sidecar (Ollama) using nomic-embed-text...");
        try {
            String responseStr = ollamaClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", "nomic-embed-text", "prompt", textPrompt))
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = org.springframework.boot.json.JsonParserFactory.getJsonParser().parseMap(responseStr);
            if (response == null || !response.containsKey("embedding")) {
                throw new IllegalStateException("Failed to harvest vector representation: response or embedding missing.");
            }

            List<Double> embeddingList = (List<Double>) response.get("embedding");
            return "[" + embeddingList.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
        } catch (Exception e) {
            log.error("Local embedding generation failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to harvest vector representation due to error: " + e.getMessage(), e);
        }
    }
}
