package codes.ani.ares.model;

/**
 * Represents the lifecycle stages of an ARES background task.
 */
public enum JobStatus {
    /**
     * Job created but not yet picked up by a virtual thread.
     */
    PENDING,

    /**
     * The Refinery is actively sifting through data.
     */
    RUNNING,

    /**
     * Process finished successfully; metrics and blocks are minted.
     */
    COMPLETED,

    /**
     * An unrecoverable error occurred during ingestion or analysis.
     */
    FAILED
}
