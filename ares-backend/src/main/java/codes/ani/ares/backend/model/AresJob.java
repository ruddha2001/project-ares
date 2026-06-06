package codes.ani.ares.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ares_jobs")
@Data
public class AresJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID jobId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "repo_url")
    private String repoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.INITIALIZED;

    @Column(name = "current_task")
    private String currentTask = "Job provisioned in cluster";

    @Column(name = "task_description", columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "git_diff", columnDefinition = "TEXT")
    private String gitDiff;

    @Column(name = "context_blocks", columnDefinition = "TEXT")
    private String contextBlocks;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
