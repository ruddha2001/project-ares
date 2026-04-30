package codes.ani.ares.ingestion.model;

/**
 * Enumerates the known source origins supported by the Ares ingestion pipeline.
 *
 * <p>Each constant represents a distinct type of source that the engine can
 * ingest and index. The type is stored in {@link SourceData#sourceType()} to
 * enable downstream processing logic to branch on source origin.</p>
 */
public enum SourceType {
    /**
     * Source fetched from a Notion workspace page.
     */
    NOTION,
    /**
     * Source fetched from a GitHub repository (file listing).
     */
    GITHUB_REPO,
    /**
     * Source fetched from a GitHub pull request diff.
     */
    GITHUB_PR,
    /**
     * Placeholder for Atlassian Jira issue ingestion.
     */
    JIRA,
    /**
     * Placeholder for Rally (CA Agile) artifact ingestion.
     */
    RALLY,
    /**
     * Placeholder for local file system ingestion.
     */
    LOCAL_FILE,
    /**
     * Placeholder for documentation PDF ingestion.
     */
    DOCUMENTATION_PDF
}
