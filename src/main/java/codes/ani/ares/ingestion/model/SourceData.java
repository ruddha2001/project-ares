package codes.ani.ares.ingestion.model;

import java.util.Map;

public record SourceData(String content, Map<String, Object> metadata, SourceType sourceType, String originalUri) {
}
