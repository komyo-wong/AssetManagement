package com.assetmanagement.tracking.repository;
import com.assetmanagement.tracking.domain.RollCallSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RollCallSessionRepository extends JpaRepository<RollCallSession, UUID> {
    List<RollCallSession> findAllByProjectIdOrderByStartedAtDesc(UUID projectId);
    Optional<RollCallSession> findByIdAndProjectId(UUID id, UUID projectId);
}
