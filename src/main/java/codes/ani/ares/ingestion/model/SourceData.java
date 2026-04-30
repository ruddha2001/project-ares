package codes.ani.ares.ingestion.model;

import java.util.Map;

/**
 * Normalized data payload produced by all ingestion providers.
 *
 * <p>Encapsulates the raw content, provider-agnostic metadata, source type
 * classification, and the original URI that triggered the ingestion. This
 * uniform representation decouples upstream ingestion workflows from the
 * specifics of each source provider (GitHub, Notion, etc.).</p>
 *
 * @param content     raw content fetched from the source; may be {@code null} on failure
 * @param metadata    key-value pairs providing extraction context (timestamp, provider, status)
 * @param sourceType  enumerated classification of the source origin
 * @param originalUri the URI as originally provided to the ingestion endpoint
 */
public record SourceData(String content, Map<String, Object> metadata, SourceType sourceType, String originalUri) {
}
