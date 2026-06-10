package codes.ani.ares.backend.service;

import codes.ani.ares.backend.dto.ManualDocIngestionDTO;
import codes.ani.ares.backend.model.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionWebhookService {
    private final RestClient inferenceWorkerClient;

    public void triggerCodebaseIngestion(UUID jobId, Project project, String githubToken, String copilotModel) {
        log.info("Dispatching codebase gauntlet webhook for project: {}", project.getId());

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("job_id", jobId.toString());
        payload.put("project_id", project.getId().toString());
        payload.put("repo_url", project.getRepoUrl());
        payload.put("default_branch", project.getDefaultBranch());
        payload.put("github_token", githubToken);
        if (copilotModel != null) {
            payload.put("copilot_model", copilotModel);
        }

        inferenceWorkerClient.post()
                .uri("/api/v1/etl/codebase")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void triggerDocumentIngestion(UUID jobId, ManualDocIngestionDTO docDTO, String documentToken, String githubToken, String copilotModel) {
        log.info("Dispatching document gauntlet webhook for project reference: {}", docDTO.projectId());

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("job_id", jobId.toString());
        payload.put("project_id", docDTO.projectId().toString());
        payload.put("source_origin", docDTO.sourceOrigin().name());
        payload.put("source_url", docDTO.sourceUrl());
        if (documentToken != null) {
            payload.put("document_token", documentToken);
        }
        if (githubToken != null) {
            payload.put("github_token", githubToken);
        }
        if (copilotModel != null) {
            payload.put("copilot_model", copilotModel);
        }

        inferenceWorkerClient.post()
                .uri("/api/v1/etl/document")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
