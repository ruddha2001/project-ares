package codes.ani.ares.backend.service;

import codes.ani.ares.backend.model.KnowledgeIndex;
import codes.ani.ares.backend.repository.KnowledgeIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningOrchestrationService {

    private final KnowledgeIndexRepository indexRepository;
    private final RestClient inferenceWorkerClient;
    private final RestClient.Builder restClientBuilder;
    private RestClient ollamaClient;

    @Value("${COPILOT_EMBEDDING_MODEL:}")
    private String copilotEmbeddingModel;

    @Value("${COPILOT_LLM_MODEL:}")
    private String copilotLlmModel;

    @Value("${GITHUB_PAT:}")
    private String githubPat;

    @Value("${INFERENCE_URL:http://inference-sidecar:11434}")
    private String inferenceUrl;

    @PostConstruct
    public void init() {
        this.ollamaClient = restClientBuilder.baseUrl(inferenceUrl).build();
    }

    public String getEmbedding(String textPrompt, boolean isCode) {
        return getEmbedding(textPrompt, null, null, isCode);
    }

    @SuppressWarnings("unchecked")
    public String getEmbedding(String textPrompt, String effectiveGithubToken, String effectiveCopilotEmbeddingModel, boolean isCode) {
        String responseStr;
        String finalModel = (effectiveCopilotEmbeddingModel != null && !effectiveCopilotEmbeddingModel.trim().isEmpty())
                ? effectiveCopilotEmbeddingModel
                : this.copilotEmbeddingModel;
        String finalToken = (effectiveGithubToken != null && !effectiveGithubToken.trim().isEmpty())
                ? effectiveGithubToken
                : this.githubPat;

        log.info("Requesting vector embedding extraction (isCode={}) from inference-worker...", isCode);
        responseStr = inferenceWorkerClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "prompt", textPrompt,
                        "copilot_embedding_model", finalModel != null ? finalModel : "",
                        "github_token", finalToken,
                        "is_code", isCode))
                .retrieve()
                .body(String.class);

        Map<String, Object> response;
        try {
            response = org.springframework.boot.json.JsonParserFactory.getJsonParser().parseMap(responseStr);
        } catch (Exception e) {
            log.error("Failed to parse embeddings response JSON: {}", responseStr, e);
            throw new IllegalStateException("Failed to harvest vector representation due to JSON parse error.");
        }

        if (response == null || !response.containsKey("embedding")) {
            throw new IllegalStateException("Failed to harvest vector representation.");
        }

        List<Double> embeddingList = (List<Double>) response.get("embedding");
        return "[" + embeddingList.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
    }

    public String compileLibrarianContextPayload(UUID projectId, String textPrompt) {
        return compileLibrarianContextPayload(projectId, textPrompt, null, null);
    }

    public String compileLibrarianContextPayload(UUID projectId, String textPrompt, String effectiveGithubToken,
            String effectiveCopilotEmbeddingModel) {
        // 1. Generate real prompt embedding vectors
        String codebaseVectorString = getEmbedding(textPrompt, effectiveGithubToken, effectiveCopilotEmbeddingModel, true);
        String docVectorString = getEmbedding(textPrompt, effectiveGithubToken, effectiveCopilotEmbeddingModel, false);

        // 2. Perform parallelized two-tiered searches
        List<KnowledgeIndex> codebaseMatches = indexRepository.searchCodebase(projectId, codebaseVectorString, 5);
        List<KnowledgeIndex> documentMatches = indexRepository.searchDocumentation(projectId, docVectorString, 5);

        // 3. Format the context wrappers neatly for the LLM
        StringBuilder contextBuilder = new StringBuilder();

        contextBuilder.append("=== TARGET SYSTEM REQUIREMENT / FEATURE REQUEST ===\n");
        contextBuilder.append(textPrompt).append("\n\n");

        contextBuilder.append("=== TIER 1: MATCHING CODEBASE FRAGMENTS (LOCAL_CODEBASE) ===\n");
        for (KnowledgeIndex match : codebaseMatches) {
            contextBuilder.append("File: ").append(match.getBlockTitle()).append("\n");
            contextBuilder.append("Content Snippet:\n").append(match.getBlockContent()).append("\n");
            contextBuilder.append("--------------------------------------------------\n");
        }

        contextBuilder.append("\n=== TIER 2: MATCHING SPECIFICATIONS & DOCUMENTATION ===\n");
        for (KnowledgeIndex match : documentMatches) {
            contextBuilder.append("Source reference: ").append(match.getSourceUrl()).append("\n");
            contextBuilder.append("Content Details:\n").append(match.getBlockContent()).append("\n");
            contextBuilder.append("--------------------------------------------------\n");
        }

        return contextBuilder.toString();
    }

    public Map<String, Object> executePlanning(UUID projectId, String textPrompt, String effectiveGithubToken,
            String effectiveCopilotEmbeddingModel) {
        String codebaseVectorString = getEmbedding(textPrompt, effectiveGithubToken, effectiveCopilotEmbeddingModel, true);
        String docVectorString = getEmbedding(textPrompt, effectiveGithubToken, effectiveCopilotEmbeddingModel, false);
        List<KnowledgeIndex> codebaseMatches = indexRepository.searchCodebase(projectId, codebaseVectorString, 5);
        List<KnowledgeIndex> documentMatches = indexRepository.searchDocumentation(projectId, docVectorString, 5);

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("=== TARGET SYSTEM REQUIREMENT / FEATURE REQUEST ===\n");
        contextBuilder.append(textPrompt).append("\n\n");

        contextBuilder.append("=== TIER 1: MATCHING CODEBASE FRAGMENTS (LOCAL_CODEBASE) ===\n");
        for (KnowledgeIndex match : codebaseMatches) {
            contextBuilder.append("File: ").append(match.getBlockTitle()).append("\n");
            contextBuilder.append("Content Snippet:\n").append(match.getBlockContent()).append("\n");
            contextBuilder.append("--------------------------------------------------\n");
        }

        contextBuilder.append("\n=== TIER 2: MATCHING SPECIFICATIONS & DOCUMENTATION ===\n");
        for (KnowledgeIndex match : documentMatches) {
            contextBuilder.append("Source reference: ").append(match.getSourceUrl()).append("\n");
            contextBuilder.append("Content Details:\n").append(match.getBlockContent()).append("\n");
            contextBuilder.append("--------------------------------------------------\n");
        }

        return Map.of(
                "contextPayload", contextBuilder.toString(),
                "codebaseMatches", codebaseMatches,
                "documentMatches", documentMatches);
    }

    public Map<String, Object> executeVerification(UUID projectId, String gitDiff, String effectiveGithubToken,
            String effectiveCopilotEmbeddingModel) {
        String codebaseVectorString = getEmbedding(gitDiff, effectiveGithubToken, effectiveCopilotEmbeddingModel, true);
        String docVectorString = getEmbedding(gitDiff, effectiveGithubToken, effectiveCopilotEmbeddingModel, false);
        List<KnowledgeIndex> codebaseMatches = indexRepository.searchCodebase(projectId, codebaseVectorString, 5);
        List<KnowledgeIndex> documentMatches = indexRepository.searchDocumentation(projectId, docVectorString, 5);

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("=== TIER 1: CODEBASE REFERENCE CONTEXT ===\n");
        for (KnowledgeIndex match : codebaseMatches) {
            contextBuilder.append("File: ").append(match.getBlockTitle()).append("\n");
            contextBuilder.append("Content Snippet:\n").append(match.getBlockContent()).append("\n");
            contextBuilder.append("--------------------------------------------------\n");
        }

        contextBuilder.append("\n=== TIER 2: SPECIFICATION & COMPLIANCE POLICIES ===\n");
        for (KnowledgeIndex match : documentMatches) {
            contextBuilder.append("Source reference: ").append(match.getSourceUrl()).append("\n");
            contextBuilder.append("Content Details:\n").append(match.getBlockContent()).append("\n");
            contextBuilder.append("--------------------------------------------------\n");
        }

        return Map.of(
                "contextPayload", contextBuilder.toString(),
                "codebaseMatches", codebaseMatches,
                "documentMatches", documentMatches);
    }

    public String generateText(String prompt, String effectiveGithubToken, String effectiveCopilotLlmModel) {
        String responseStr;
        String finalModel = (effectiveCopilotLlmModel != null && !effectiveCopilotLlmModel.trim().isEmpty())
                ? effectiveCopilotLlmModel
                : this.copilotLlmModel;
        String finalToken = (effectiveGithubToken != null && !effectiveGithubToken.trim().isEmpty())
                ? effectiveGithubToken
                : this.githubPat;

        log.info("Requesting completion generation from inference-worker...");
        responseStr = inferenceWorkerClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "prompt", prompt,
                        "copilot_llm_model", finalModel != null ? finalModel : "",
                        "github_token", finalToken))
                .retrieve()
                .body(String.class);

        Map<String, Object> response;
        try {
            response = org.springframework.boot.json.JsonParserFactory.getJsonParser().parseMap(responseStr);
        } catch (Exception e) {
            log.error("Failed to parse generation response JSON: {}", responseStr, e);
            throw new IllegalStateException("Failed to harvest completion response due to JSON parse error.");
        }

        if (response == null || !response.containsKey("response")) {
            throw new IllegalStateException("Failed to harvest completion response.");
        }

        return (String) response.get("response");
    }
}