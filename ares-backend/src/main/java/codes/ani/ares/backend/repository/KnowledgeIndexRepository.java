package codes.ani.ares.backend.repository;

import codes.ani.ares.backend.model.KnowledgeIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeIndexRepository extends JpaRepository<KnowledgeIndex, UUID> {
    @Query(value = """
            SELECT * FROM ares_knowledge_indices\s
            WHERE project_id = :projectId\s
            ORDER BY embedding <=> cast(:queryEmbedding as vector)\s
            LIMIT :maxResults
            """, nativeQuery = true)
    List<KnowledgeIndex> findNearestBlocksByProject(
            @Param("projectId") UUID projectId,
            @Param("queryEmbedding") float[] queryEmbedding,
            @Param("maxResults") int maxResults
    );
}
