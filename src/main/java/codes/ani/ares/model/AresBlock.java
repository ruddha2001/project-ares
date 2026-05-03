package codes.ani.ares.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * JPA entity representing a block of content stored in the Ares system.
 * <p>
 * Each instance maps to a row in the "ares_blocks" table. The class uses
 * Lombok to generate boilerplate (getters/setters, constructors, builder).
 */
@Entity
@Table(name = "ares_blocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AresBlock {

    /**
     * Primary key for the block. Generated automatically as a UUID by JPA.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optional parent block id for hierarchical/nested blocks.
     */
    private UUID parentId;

    /**
     * The id of the ingestion/job that produced this block.
     */
    private UUID jobId;

    /**
     * The raw textual content of the block. Persisted as a TEXT column to
     * support large content values.
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Path to the source file (if applicable) from which this block was
     * extracted.
     */
    private String filePath;

    /**
     * Logical project identifier this block belongs to.
     */
    private String projectId;

    /**
     * Logical team identifier this block belongs to.
     */
    private String teamId;

    /**
     * A checksum or content hash used for deduplication or quick change
     * detection.
     */
    private String hash;

    /**
     * The block type (stored as a String in the database). Use
     * {@link AresBlockType} enum for allowed values.
     */
    @Enumerated(EnumType.STRING)
    private AresBlockType type;

    /**
     * Arbitrary JSON metadata for the block. Stored using Hibernate's
     * {@code JdbcTypeCode(SqlTypes.JSON)} so it persists as a JSON/JSONB
     * column in compatible databases (e.g. PostgreSQL).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    /**
     * Dense vector embedding for the block used for semantic search.
     * Persisted using a database-specific vector column (e.g. Postgres
     * pgvector) with dimension 1536. The field is represented as a float
     * array in Java.
     */
    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;
}