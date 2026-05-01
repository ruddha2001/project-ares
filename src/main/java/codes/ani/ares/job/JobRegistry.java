package codes.ani.ares.job;

import codes.ani.ares.job.model.AresJob;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for storing and retrieving jobs by their identifier.
 */
@Component
public class JobRegistry {
    private final Map<UUID, AresJob> jobs = new ConcurrentHashMap<>();

    /**
     * Saves or replaces the given job in the registry.
     *
     * @param job the job to store
     */
    public void save(AresJob job) {
        jobs.put(job.jobId(), job);
    }

    /**
     * Finds a job by its identifier.
     *
     * @param jobId the job identifier
     * @return the matching job, if present
     */
    public Optional<AresJob> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * Returns all jobs currently stored in the registry.
     *
     * @return a snapshot list of all jobs
     */
    public List<AresJob> findAll() {
        return new ArrayList<>(jobs.values());
    }
}
