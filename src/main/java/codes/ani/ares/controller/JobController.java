package codes.ani.ares.controller;

import codes.ani.ares.job.JobRegistry;
import codes.ani.ares.job.model.AresJob;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing and retrieving job information.
 * Provides endpoints to query the status and details of jobs by their unique identifiers.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {
    /**
     * Registry for managing and retrieving jobs.
     */
    private final JobRegistry jobRegistry;

    /**
     * Retrieves the status of a job by its ID.
     *
     * @param jobId the unique identifier of the job to retrieve
     * @return a {@link ResponseEntity} containing the {@link AresJob} if found, or a 404 Not Found response if not found
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<AresJob> getJobStatus(@PathVariable UUID jobId) {
        return jobRegistry.findById(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
