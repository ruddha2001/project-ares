package codes.ani.ares.backend.service;

import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.model.KnowledgeIndex;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.KnowledgeIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobPlanningServiceTest {

    private JobPlanningService jobPlanningService;

    @Mock
    private AresJobRepository jobRepository;

    @Mock
    private KnowledgeIndexRepository indexRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private PlanningOrchestrationService planningOrchestrationService;

    @Mock
    private TelemetryEventPublisher telemetryPublisher;

    private Executor taskExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        taskExecutor = Runnable::run;

        jobPlanningService = new JobPlanningService(
                jobRepository,
                indexRepository,
                embeddingService,
                planningOrchestrationService,
                telemetryPublisher,
                taskExecutor
        );
    }

    @Test
    void testRunPlanningOrchestration_Success() {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String prompt = "Feature description";
        String dummyEmbedding = "[0.1, 0.2]";
        String dummyPlan = "#### Plan Markdown";

        AresJob job = AresJob.builder()
                .jobId(jobId)
                .projectId(projectId)
                .status(JobStatus.PROCESSING)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(embeddingService.getLocalEmbedding(prompt)).thenReturn(dummyEmbedding);

        KnowledgeIndex codebaseMatch = new KnowledgeIndex();
        codebaseMatch.setBlockTitle("code.java");
        codebaseMatch.setBlockContent("class Code {}");
        when(indexRepository.searchCodebase(eq(projectId), eq(dummyEmbedding), eq(5)))
                .thenReturn(List.of(codebaseMatch));

        KnowledgeIndex docMatch = new KnowledgeIndex();
        docMatch.setSourceUrl("doc.md");
        docMatch.setBlockContent("Doc specs");
        when(indexRepository.searchDocumentation(eq(projectId), eq(dummyEmbedding), eq(5)))
                .thenReturn(List.of(docMatch));

        when(planningOrchestrationService.generateText(any(String.class), eq("dummy-pat"), any()))
                .thenReturn(dummyPlan);

        jobPlanningService.runPlanningOrchestration(
                jobId,
                projectId,
                prompt,
                "dummy-pat",
                "dummy-notion-token",
                "auto"
        );

        ArgumentCaptor<AresJob> jobCaptor = ArgumentCaptor.forClass(AresJob.class);
        verify(jobRepository, atLeastOnce()).saveAndFlush(jobCaptor.capture());

        List<AresJob> savedJobs = jobCaptor.getAllValues();
        AresJob finalJob = savedJobs.get(savedJobs.size() - 1);
        assertEquals(JobStatus.COMPLETED, finalJob.getStatus());
        assertEquals("PLANNING_COMPLETE", finalJob.getCurrentTask());
        assertEquals(dummyPlan, finalJob.getPayload());

        verify(telemetryPublisher).publish(jobId, "ORCHESTRATION_START", "PROCESSING", "Async planning loop initiated");
        verify(telemetryPublisher).publish(jobId, "EMBEDDING_GENERATION", "PROCESSING", "Generating embeddings via sidecar");
        verify(telemetryPublisher).publish(jobId, "EMBEDDING_GENERATION", "COMPLETED", "Embeddings generated successfully");
        verify(telemetryPublisher).publish(jobId, "DATA_RETRIEVAL", "PROCESSING", "Retrieving codebase fragments and documentation");
        verify(telemetryPublisher).publish(jobId, "DATA_RETRIEVAL", "COMPLETED", "Retrieved 1 codebase matches and 1 document matches");
        verify(telemetryPublisher).publish(jobId, "PACING_SLEEP", "COMPLETED", "Sync pacing delay finished");
        verify(telemetryPublisher).publish(jobId, "LIBRARIAN_PLANNING", "COMPLETED", "Compliance plan successfully created");
        verify(telemetryPublisher).publish(jobId, "ORCHESTRATION_END", "COMPLETED", "Orchestration process finished successfully");
    }

    @Test
    void testRunPlanningOrchestration_EmbeddingFailure() {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String prompt = "Feature description";

        AresJob job = AresJob.builder()
                .jobId(jobId)
                .projectId(projectId)
                .status(JobStatus.PROCESSING)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(embeddingService.getLocalEmbedding(prompt)).thenThrow(new IllegalStateException("Ollama down"));

        jobPlanningService.runPlanningOrchestration(
                jobId,
                projectId,
                prompt,
                "dummy-pat",
                "dummy-notion-token",
                "auto"
        );

        ArgumentCaptor<AresJob> jobCaptor = ArgumentCaptor.forClass(AresJob.class);
        verify(jobRepository, atLeastOnce()).saveAndFlush(jobCaptor.capture());

        List<AresJob> savedJobs = jobCaptor.getAllValues();
        AresJob finalJob = savedJobs.get(savedJobs.size() - 1);
        assertEquals(JobStatus.FAILED, finalJob.getStatus());
        assertEquals("ERROR: Ollama down", finalJob.getCurrentTask());

        verify(telemetryPublisher).publish(jobId, "ORCHESTRATION_FAIL", "FAILED", "Error: Ollama down");
    }
}
