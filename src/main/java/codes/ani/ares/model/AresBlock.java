package codes.ani.ares.model;

import lombok.Builder;

import java.util.Map;
import java.util.UUID;

/**
 * The fundamental unit of 'Truth' in the Ares Vault.
 * Designed for institutional memory and architectural traceability.
 */
@Builder(toBuilder = true) // Allow immutable updates for status/linking
public record AresBlock(
        UUID id,             // Primary identifier in pgvector
        UUID parentId,       // Connective link (e.g., Requirement -> Code implementation)
        UUID jobId,          // The specific mission (dig) that created this block
        AresBlockType type,  // CODE, REQUIREMENT, ARCHITECTURAL_DECISION
        String content,      // The refined, atomic truth (text or code snippet)
        String filePath,     // Relative path within the repo (essential for Graphify)
        String sourceUri,    // The original source (e.g., GitHub Repo URL)
        String projectId,    // High-level organizational grouping (Sabre Project)
        String teamId,       // Ownership grouping
        Map<String, Object> metadata, // Extensible: status (PROPOSED/REJECTED), authors, line numbers
        String hash          // For rapid delta-syncing detection in future PRs
) {
}