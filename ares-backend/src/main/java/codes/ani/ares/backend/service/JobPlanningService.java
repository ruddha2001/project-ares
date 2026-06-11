package codes.ani.ares.backend.service;

import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.model.KnowledgeIndex;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.KnowledgeIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobPlanningService {

    private final AresJobRepository jobRepository;
    private final KnowledgeIndexRepository indexRepository;
    private final EmbeddingService embeddingService;
    private final PlanningOrchestrationService planningOrchestrationService;
    private final TelemetryEventPublisher telemetryPublisher;
    private final Executor taskExecutor;

    @Async("taskExecutor")
    public void runPlanningOrchestration(UUID jobId, UUID projectId, String prompt, String githubToken, String notionToken, String copilotModel) {
        telemetryPublisher.publish(jobId, "ORCHESTRATION_START", "PROCESSING", "Async planning loop initiated");
        
        try {
            // 1. Embedding Generation
            updateJobState(jobId, JobStatus.PROCESSING, "GENERATING_EMBEDDINGS", null);
            telemetryPublisher.publish(jobId, "EMBEDDING_GENERATION", "PROCESSING", "Generating embeddings via sidecar");
            
            String vectorString = embeddingService.getLocalEmbedding(prompt);
            
            updateJobState(jobId, JobStatus.PROCESSING, "EMBEDDINGS_GENERATED", null);
            telemetryPublisher.publish(jobId, "EMBEDDING_GENERATION", "COMPLETED", "Embeddings generated successfully");

            // 2. Parallel Knowledge Retrieval
            updateJobState(jobId, JobStatus.PROCESSING, "RETRIEVING_DATA", null);
            telemetryPublisher.publish(jobId, "DATA_RETRIEVAL", "PROCESSING", "Retrieving codebase fragments and documentation");

            CompletableFuture<List<KnowledgeIndex>> codebaseFuture = CompletableFuture.supplyAsync(
                    () -> indexRepository.searchCodebase(projectId, vectorString, 5), taskExecutor);
            CompletableFuture<List<KnowledgeIndex>> documentFuture = CompletableFuture.supplyAsync(
                    () -> indexRepository.searchDocumentation(projectId, vectorString, 5), taskExecutor);

            CompletableFuture.allOf(codebaseFuture, documentFuture).join();
            List<KnowledgeIndex> codebaseMatches = codebaseFuture.join();
            List<KnowledgeIndex> documentMatches = documentFuture.join();

            updateJobState(jobId, JobStatus.PROCESSING, "RETRIEVED_DATA", null);
            telemetryPublisher.publish(jobId, "DATA_RETRIEVAL", "COMPLETED", 
                    String.format("Retrieved %d codebase matches and %d document matches", codebaseMatches.size(), documentMatches.size()));

            // 3. Telemetry Pacing Delay
            updateJobState(jobId, JobStatus.PROCESSING, "PACING_SLEEP", null);
            telemetryPublisher.publish(jobId, "PACING_SLEEP", "PROCESSING", "Sync pacing delay active");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Pacing sleep interrupted", e);
            }
            telemetryPublisher.publish(jobId, "PACING_SLEEP", "COMPLETED", "Sync pacing delay finished");

            // 4. Invoke LLM text generation
            updateJobState(jobId, JobStatus.PROCESSING, "LIBRARIAN_PLANNING", null);
            telemetryPublisher.publish(jobId, "LIBRARIAN_PLANNING", "PROCESSING", "Generating compliance and implementation plan");

            // Construct context payload
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("=== TARGET SYSTEM REQUIREMENT / FEATURE REQUEST ===\n");
            contextBuilder.append(prompt).append("\n\n");

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

            String systemPrompt = String.format("""
                    You are Antigravity, a premium agentic AI coding assistant.
                    Based on the following requirement and context, generate a detailed implementation plan.
                    Use clear markdown formatting, list modified/new files, and outline the steps clearly.

                    %s
                    """, contextBuilder.toString());

            String planResult = planningOrchestrationService.generateText(systemPrompt, githubToken, copilotModel);

            // 5. Complete Execution
            updateJobState(jobId, JobStatus.COMPLETED, "PLANNING_COMPLETE", planResult);
            telemetryPublisher.publish(jobId, "LIBRARIAN_PLANNING", "COMPLETED", "Compliance plan successfully created");
            telemetryPublisher.publish(jobId, "ORCHESTRATION_END", "COMPLETED", "Orchestration process finished successfully");

        } catch (Exception e) {
            log.error("Fatal error inside asynchronous planning loop: {}", e.getMessage(), e);
            updateJobState(jobId, JobStatus.FAILED, "ERROR: " + e.getMessage(), null);
            telemetryPublisher.publish(jobId, "ORCHESTRATION_FAIL", "FAILED", "Error: " + e.getMessage());
        }
    }

    private void updateJobState(UUID jobId, JobStatus status, String currentTask, String reportPayload) {
        try {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(status);
                job.setCurrentTask(currentTask);
                if (reportPayload != null) {
                    job.setPayload(reportPayload);
                }
                jobRepository.saveAndFlush(job);
            });
        } catch (Exception ex) {
            log.error("Failed to commit telemetry update for job {}: {}", jobId, ex.getMessage());
        }
    }
}
