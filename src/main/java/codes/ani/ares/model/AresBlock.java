package codes.ani.ares.model;

import lombok.Builder;

import java.util.Map;

/**
 * Represents a block of content in the Ares system.
 * <p>
 * This immutable record encapsulates a unit of ingested content with metadata
 * about its source, organization, and type. It supports builder pattern
 * construction via Lombok's @Builder annotation.
 * </p>
 */
@Builder
public record AresBlock(
        String id,
        String content,
        AresBlockType type,
        String sourceUri,
        String projectId,
        String teamId,
        Map<String, Object> metadata,
        String hash
) {
}
