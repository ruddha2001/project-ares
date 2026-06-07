package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.dto.ManualDocIngestionDTO;
import codes.ani.ares.backend.dto.ProjectRegistrationDTO;
import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.model.Project;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.ProjectRepository;
import codes.ani.ares.backend.service.IngestionWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/baseline")
@RequiredArgsConstructor
public class BaselineController {
    private final ProjectRepository projectRepository;
    private final IngestionWebhookService ingestionWebhookService;
    private final AresJobRepository jobRepository;

    @PostMapping("/project")
    public ResponseEntity<Map<String, String>> registerProject(@Valid @RequestBody ProjectRegistrationDTO projectDTO){
        Project project = Project.builder()
                .name(projectDTO.name())
                .repoUrl(projectDTO.repoUrl())
                .defaultBranch(projectDTO.defaultBranch())
                .build();
        Project savedProject = projectRepository.save(project);

        AresJob job = AresJob.builder()
                .projectId(savedProject.getId())
                .status(JobStatus.INITIALIZED)
                .currentTask("Project baseline initialization")
                .repoUrl(savedProject.getRepoUrl())
                .build();
        AresJob savedJob = jobRepository.save(job);

        ingestionWebhookService.triggerCodebaseIngestion(savedJob.getJobId(), savedProject);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", savedJob.getStatus().toString(),
                "project_id", savedProject.getId().toString(),
                "job_id", savedJob.getJobId().toString()
        ));
    }

    @PostMapping("/doc")
    public ResponseEntity<Map<String, String>> injectDocument(@Valid @RequestBody ManualDocIngestionDTO docDTO) {
        if (!projectRepository.existsById(docDTO.projectId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Target project ID " + docDTO.projectId() + " does not exist."
            ));
        }

        AresJob job = AresJob.builder()
                .projectId(docDTO.projectId())
                .status(JobStatus.INITIALIZED)
                .currentTask("Document baseline initialization")
                .docUrl(docDTO.sourceUrl())
                .build();
        AresJob savedJob = jobRepository.save(job);

        ingestionWebhookService.triggerDocumentIngestion(savedJob.getJobId(), docDTO);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", savedJob.getStatus().toString(),
                "project_id", docDTO.projectId().toString(),
                "job_id", savedJob.getJobId().toString()
        ));
    }
}
