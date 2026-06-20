package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.dto.JobInitializationRequest;
import codes.ani.ares.backend.enums.PipelineStage;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@ActiveProfiles("test")
public class JobControllerTest {

    private MockMvc mockMvc;

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

        mockMvc = MockMvcBuilders.standaloneSetup(jobController).build();
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
                        PipelineStage.EMBEDDING_GENERATION, "ollama",
                        PipelineStage.KNOWLEDGE_RETRIEVAL_RANKING, "ollama",
                        PipelineStage.COMPLIANCE_EVALUATION, "gemini-flash-3.5",
                        PipelineStage.PR_SYNTHESIS, "claude-opus-4.6"
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

    @Test
    public void testJobInitializationWithMockMvcValidRouting() throws Exception {
        String validPayload = String.format("""
                {
                    "projectId": "%s",
                    "repositoryUrl": "git@github.com:ruddha2001/ares.git",
                    "featureSpecUrl": "https://app.notion.com/p/ruddha2001/Feature-Spec",
                    "rawSpecificationText": "Cleaned Markdown Context",
                    "localGitDiff": "diff --git a/...",
                    "routingConfiguration": {
                        "EMBEDDING_GENERATION": "ollama",
                        "KNOWLEDGE_RETRIEVAL_RANKING": "ollama",
                        "COMPLIANCE_EVALUATION": "gemini-flash-3.5",
                        "PR_SYNTHESIS": "claude-opus-4.6"
                    }
                }
                """, testProject.getId());

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repoUrl").value("git@github.com:ruddha2001/ares.git"))
                .andExpect(jsonPath("$.taskDescription").value("Cleaned Markdown Context"));

        // Verify it was correctly stored in the DB
        AresJob job = jobRepository.findAll().stream()
                .filter(j -> "Cleaned Markdown Context".equals(j.getTaskDescription()))
                .findFirst()
                .orElseThrow();
        assertNotNull(job.getAuditMetadata());
        assertTrue(job.getAuditMetadata().contains("gemini-flash-3.5"));
        assertTrue(job.getAuditMetadata().contains("EMBEDDING_GENERATION"));
    }

    @Test
    public void testJobInitializationWithMockMvcInvalidRouting() throws Exception {
        String invalidPayload = String.format("""
                {
                    "projectId": "%s",
                    "repositoryUrl": "git@github.com:ruddha2001/ares.git",
                    "routingConfiguration": {
                        "INVALID_STAGE": "ollama"
                    }
                }
                """, testProject.getId());

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testJobInitializationWithMockMvcNullRouting() throws Exception {
        String nullPayload = String.format("""
                {
                    "projectId": "%s",
                    "repositoryUrl": "git@github.com:ruddha2001/ares.git",
                    "featureSpecUrl": "https://app.notion.com/p/ruddha2001/Feature-Spec",
                    "rawSpecificationText": "Missing Routing Spec",
                    "localGitDiff": "diff --git a/..."
                }
                """, testProject.getId());

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(nullPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskDescription").value("Missing Routing Spec"));

        // The record's compact constructor should default the null map to an empty map (or at least avoid NullPointerException)
        AresJob job = jobRepository.findAll().stream()
                .filter(j -> "Missing Routing Spec".equals(j.getTaskDescription()))
                .findFirst()
                .orElseThrow();
        // Since it's default Map.of(), routingConfiguration won't be null
        // JobController: if (request.routingConfiguration() != null) is true (it is an empty map)
        // job.setAuditMetadata(mapper.writeValueAsString(request.routingConfiguration())) which serializes as "{}"
        assertNotNull(job.getAuditMetadata());
        assertEquals("{}", job.getAuditMetadata());
    }
}
