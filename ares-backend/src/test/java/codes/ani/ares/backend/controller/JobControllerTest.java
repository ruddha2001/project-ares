package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.dto.JobPlanRequest;
import codes.ani.ares.backend.dto.JobPlanResponse;
import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.ProjectRepository;
import codes.ani.ares.backend.service.JobPlanningService;
import codes.ani.ares.backend.service.PlanningOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class JobControllerTest {

    private JobController jobController;

    @Mock
    private AresJobRepository jobRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private PlanningOrchestrationService planningOrchestrationService;

    @Mock
    private JobPlanningService jobPlanningService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jobController = new JobController(
                jobRepository,
                projectRepository,
                planningOrchestrationService,
                jobPlanningService
        );
    }

    @Test
    void testTriggerJobPlanning_Success() {
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String prompt = "Implement the new planning loop API.";

        when(projectRepository.existsById(projectId)).thenReturn(true);

        AresJob savedJob = AresJob.builder()
                .jobId(jobId)
                .projectId(projectId)
                .status(JobStatus.PROCESSING)
                .build();
        when(jobRepository.save(any(AresJob.class))).thenReturn(savedJob);

        JobPlanRequest request = new JobPlanRequest(prompt);

        ResponseEntity<JobPlanResponse> response = jobController.triggerJobPlanning(
                projectId,
                request,
                "dummy-github-pat",
                "dummy-notion-token",
                "auto"
        );

        assertNotNull(response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(projectId, response.getBody().projectId());
        assertEquals(jobId, response.getBody().jobId());
        assertEquals("PROCESSING", response.getBody().status());

        Mockito.verify(jobPlanningService).runPlanningOrchestration(
                Mockito.eq(jobId),
                Mockito.eq(projectId),
                Mockito.eq(prompt),
                Mockito.eq("dummy-github-pat"),
                Mockito.eq("dummy-notion-token"),
                Mockito.eq("auto")
        );
    }

    @Test
    void testTriggerJobPlanning_ProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        JobPlanRequest request = new JobPlanRequest("Some prompt");

        when(projectRepository.existsById(projectId)).thenReturn(false);

        ResponseEntity<JobPlanResponse> response = jobController.triggerJobPlanning(
                projectId,
                request,
                "dummy-github-pat",
                "dummy-notion-token",
                "auto"
        );

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
