package codes.ani.ares.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ares_knowledge_indices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_origin", nullable = false, length = 50)
    private SourceOrigin sourceOrigin;

    @Column(name = "source_url", nullable = false, length = 2048, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "block_title", nullable = false)
    private String blockTitle;

    @Column(name = "block_content", nullable = false, columnDefinition = "TEXT")
    private String blockContent;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
