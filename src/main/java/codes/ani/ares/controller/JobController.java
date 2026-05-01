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

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobRegistry jobRegistry;

    @GetMapping("/{jobId}")
    public ResponseEntity<AresJob> getJobStatus(@PathVariable UUID jobId) {
        return jobRegistry.findById(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
