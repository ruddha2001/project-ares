package codes.ani.ares.backend.repository;

import codes.ani.ares.backend.model.AresJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AresJobRepository extends JpaRepository<AresJob, UUID> {
}
