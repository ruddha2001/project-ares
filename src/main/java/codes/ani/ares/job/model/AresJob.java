package codes.ani.ares.job.model;

import codes.ani.ares.model.IngestionMetrics;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a job managed by Ares.
 *
 * <p>This entity is mapped to the "ares_jobs" table and contains basic
 * information about an ingestion or processing job such as its type,
 * status, progress, target URI and metrics. The class uses Lombok to
 * generate boilerplate (getters, setters, constructors and builder) and
 * Hibernate annotations to persist JSON fields for logs and metrics.
 */
@Entity
@Table(name = "ares_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AresJob {

    /**
     * Primary key for the job record.
     * Stored as a UUID.
     */
    @Id
    private UUID jobId;

    /**
     * The type of the job. Mapped to the database as a string value
     * from the {@code JobType} enum.
     */
    @Enumerated(EnumType.STRING)
    private JobType type;

    /**
     * Current lifecycle status of the job (e.g. PENDING, RUNNING, FAILED,
     * COMPLETED). Persisted as the enum name string.
     */
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    /**
     * Progress of the job as a value between 0.0 and 1.0 (or 0-100 if
     * the application convention is percent). Consumers should consult
     * the surrounding codebase for the exact interpretation.
     */
    private double progress;

    /**
     * Target resource identifier (for example an ingestion source URL or
     * identifier) that this job operates on.
     */
    private String targetUri;

    /**
     * Append-only trail of human-readable log messages produced by the
     * job. Persisted as JSON (Postgres jsonb). The field is initialized
     * with an empty list so builders or JPA can rely on a non-null value.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> logTrail = new ArrayList<>();

    /**
     * Structured ingestion metrics (for example counts, durations,
     * or other telemetry) associated with the job. Stored as JSON/jsonb
     * in the database to allow flexible metric shapes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private IngestionMetrics metrics;

    /**
     * Optional error message describing the failure reason when the job
     * finishes in an error state.
     */
    private String errorMessage;

    /**
     * Record creation timestamp. Set automatically before persisting
     * via {@link #onCreate()}.
     */
    private Instant createdAt;

    /**
     * Last update timestamp. Updated automatically before each update
     * via {@link #onUpdate()} (and set on create as well).
     */
    private Instant updatedAt;

    /**
     * JPA lifecycle callback executed before the entity is persisted for
     * the first time. Initializes {@link #createdAt} and
     * {@link #updatedAt} to the current instant.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * JPA lifecycle callback executed before any entity update. Updates
     * the {@link #updatedAt} timestamp to the current instant.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}