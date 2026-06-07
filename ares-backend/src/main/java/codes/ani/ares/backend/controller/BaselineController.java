package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.dto.ManualDocIngestionDTO;
import codes.ani.ares.backend.dto.ProjectRegistrationDTO;
import codes.ani.ares.backend.model.Project;
import codes.ani.ares.backend.repository.ProjectRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/baseline")
@RequiredArgsConstructor
public class BaselineController {
    private final ProjectRepository projectRepository;

    @PostMapping("/project")
    public ResponseEntity<String> registerProject(@Valid @RequestBody ProjectRegistrationDTO projectDTO){
        Project project = Project.builder()
                .name(projectDTO.name())
                .repoUrl(projectDTO.repoUrl())
                .defaultBranch(projectDTO.defaultBranch())
                .build();

        Project saved = projectRepository.save(project);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Project baseline initialization triggered for: " + saved.getId());
    }

    @PostMapping("/doc")
    public ResponseEntity<String> injectDocument(@Valid @RequestBody ManualDocIngestionDTO docDTO) {
        if (!projectRepository.existsById(docDTO.projectId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Target project ID " + docDTO.projectId() + " does not exist.");
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Document tracking initialized. Processing background ingestion for URL: " + docDTO.sourceUrl());
    }
}
