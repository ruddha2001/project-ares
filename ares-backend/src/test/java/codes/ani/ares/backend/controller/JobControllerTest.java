package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.dto.JobInitializationRequest;
import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.model.Project;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class JobControllerTest {

    @Autowired
    private JobController jobController;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AresJobRepository jobRepository;

    private Project testProject;

    @BeforeEach
    public void setup() {
        jobRepository.deleteAll();
        projectRepository.deleteAll();

        testProject = Project.builder()
                .name("ares")
                .repoUrl("git@github.com:ruddha2001/ares.git")
                .defaultBranch("main")
                .build();
        testProject = projectRepository.save(testProject);
    }

    @Test
    public void testJobInitializationWithRoutingConfig() {
        JobInitializationRequest request = new JobInitializationRequest(
                testProject.getId(),
                "git@github.com:ruddha2001/ares.git",
                "https://app.notion.com/p/ruddha2001/Feature-Spec",
                "Cleaned Markdown Context",
                "diff --git a/...",
                Map.of(
                        "EMBEDDING_GENERATION", "ollama",
                        "KNOWLEDGE_RETRIEVAL_RANKING", "ollama",
                        "COMPLIANCE_EVALUATION", "gemini-flash-3.5",
                        "PR_SYNTHESIS", "claude-opus-4.6"
                )
        );

        ResponseEntity<AresJob> response = jobController.createJob(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AresJob job = response.getBody();
        assertNotNull(job);
        assertEquals(testProject.getId(), job.getProjectId());
        assertEquals("git@github.com:ruddha2001/ares.git", job.getRepoUrl());
        assertEquals("Cleaned Markdown Context", job.getTaskDescription());
        assertEquals("diff --git a/...", job.getGitDiff());
        assertEquals("https://app.notion.com/p/ruddha2001/Feature-Spec", job.getDocUrl());
        assertEquals(JobStatus.INITIALIZED, job.getStatus());

        AresJob persisted = jobRepository.findById(job.getJobId()).orElseThrow();
        assertNotNull(persisted.getAuditMetadata());
        assertTrue(persisted.getAuditMetadata().contains("gemini-flash-3.5"));
    }
}
