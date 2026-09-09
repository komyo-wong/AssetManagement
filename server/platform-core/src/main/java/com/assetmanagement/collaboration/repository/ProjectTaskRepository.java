package com.assetmanagement.collaboration.repository;
import com.assetmanagement.collaboration.domain.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {
    List<ProjectTask> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    Optional<ProjectTask> findByIdAndProjectId(UUID id, UUID projectId);
}
