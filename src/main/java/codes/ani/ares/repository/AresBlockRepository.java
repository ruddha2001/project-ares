package codes.ani.ares.repository;

import codes.ani.ares.model.AresBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AresBlockRepository extends JpaRepository<AresBlock, UUID> {
    List<AresBlock> findByJobId(UUID jobId);

    @Query(value = "SELECT * FROM ares_blocks b " +
            "WHERE b.project_id = :projectId " +
            "ORDER BY b.embedding <=> CAST(:queryVector as vector) " +
            "LIMIT :limit", nativeQuery = true)
    List<AresBlock> findNearestNeighbours(
            @Param("projectId") String projectId,
            @Param("queryVector") float[] queryVector,
            @Param("limit") int limit
    );
}
