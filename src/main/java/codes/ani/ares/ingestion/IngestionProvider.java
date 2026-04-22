package codes.ani.ares.ingestion;

import codes.ani.ares.ingestion.model.SourceData;

import java.util.concurrent.CompletableFuture;

/**
 * Defines a source-specific ingestion strategy that can fetch and normalize data
 * from a supported source URI.
 */
public interface IngestionProvider {
    /**
     * Determines whether this provider can ingest data from the given source URI.
     *
     * @param sourceUri source identifier or location to evaluate
     * @return {@code true} if this provider supports the URI, otherwise {@code false}
     */
    boolean supports(String sourceUri);

    /**
     * Asynchronously ingests data from the given source URI.
     *
     * @param sourceUri source identifier or location to ingest
     * @return a future that completes with the ingested {@link SourceData}
     */
    CompletableFuture<SourceData> ingest(String sourceUri);
}
