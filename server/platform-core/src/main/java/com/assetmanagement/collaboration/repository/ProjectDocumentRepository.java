package com.assetmanagement.collaboration.repository;
import com.assetmanagement.collaboration.domain.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {
    List<ProjectDocument> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<ProjectDocument> findByIdAndProjectId(UUID id, UUID projectId);
}
