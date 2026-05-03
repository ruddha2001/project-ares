package codes.ani.ares.model;

/**
 * Enumeration representing different types of blocks in the Ares system.
 * <p>
 * Each block type categorizes information extracted from various sources (Notion, GitHub, etc.)
 * into semantic domains for knowledge management and requirement traceability.
 * The block types support a complete lifecycle from requirements to infrastructure,
 * enabling comprehensive code-to-documentation mapping and audit coverage.
 * </p>
 */
public enum AresBlockType {
    /**
     * Formal business rules, constraints, or bulleted requirements from Notion.
     */
    REQUIREMENT,

    /**
     * High-level rationale, ADRs, or Design Doc summaries (The "Why").
     */
    ARCHITECTURE_DECISION,

    /**
     * Class, Interface, or Record signatures and definitions (The "Container").
     */
    COMPONENT_DEFINITION,

    /**
     * Individual method or function logic extracted via Graphify (The "How")[cite: 2].
     */
    LOGIC_NODE,

    /**
     * CI/CD workflows, Dockerfiles, and build manifests (pom.xml/build.gradle)[cite: 2].
     */
    INFRASTRUCTURE,

    /**
     * READMEs, process guides, and general descriptive text[cite: 2].
     */
    DOCUMENTATION,

    /**
     * Unit or Integration test definitions used for Task 5 coverage auditing[cite: 2].
     */
    TEST_SPEC,

    /**
     * Repository manifests, dependency trees, and raw Graphify structural maps[cite: 2].
     */
    STRUCTURAL_METADATA,

    /**
     * Result of the LLM model analysis
     */
    AUDIT_RESULT
}
