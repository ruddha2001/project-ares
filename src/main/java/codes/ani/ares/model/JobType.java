package codes.ani.ares.model;

/**
 * Defines the category of the background job.
 */
public enum JobType {
    /**
     * Initial repository/document ingestion into the Ares Vault.
     */
    BASELINE_INGESTION,

    /**
     * Agentic evaluation comparing PRs against requirements.
     */
    ARCHITECTURAL_ANALYSIS
}
