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

    public void triggerCodebaseIngestion(UUID jobId, Project project) {
        log.info("Dispatching codebase gauntlet webhook for project: {}", project.getId());

        Map<String, String> payload = Map.of(
                "job_id", jobId.toString(),
                "project_id", project.getId().toString(),
                "repo_url", project.getRepoUrl(),
                "default_branch", project.getDefaultBranch()
        );

        inferenceWorkerClient.post()
                .uri("/api/v1/etl/codebase")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void triggerDocumentIngestion(UUID jobId, ManualDocIngestionDTO docDTO) {
        log.info("Dispatching document gauntlet webhook for project reference: {}", docDTO.projectId());

        Map<String, String> payload = Map.of(
                "job_id", jobId.toString(),
                "project_id", docDTO.projectId().toString(),
                "source_origin", docDTO.sourceOrigin().name(),
                "source_url", docDTO.sourceUrl()
        );

        inferenceWorkerClient.post()
                .uri("/api/v1/etl/document")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
