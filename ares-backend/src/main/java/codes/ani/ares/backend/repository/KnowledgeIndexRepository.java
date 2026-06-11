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
                        SELECT * FROM ares_knowledge_indices
                        WHERE project_id = :projectId
                          AND source_origin = 'LOCAL_CODEBASE'
                        ORDER BY embedding <=> cast(:queryEmbedding as vector)
                        LIMIT :limitBound
                        """, nativeQuery = true)
        List<KnowledgeIndex> searchCodebase(
                        @Param("projectId") UUID projectId,
                        @Param("queryEmbedding") String queryEmbedding,
                        @Param("limitBound") int limitBound);

        @Query(value = """
                        SELECT * FROM ares_knowledge_indices
                        WHERE project_id = :projectId
                          AND source_origin != 'LOCAL_CODEBASE'
                        ORDER BY embedding <=> cast(:queryEmbedding as vector)
                        LIMIT :limitBound
                        """, nativeQuery = true)
        List<KnowledgeIndex> searchDocumentation(
                        @Param("projectId") UUID projectId,
                        @Param("queryEmbedding") String queryEmbedding,
                        @Param("limitBound") int limitBound);
}
