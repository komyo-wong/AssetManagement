package com.assetmanagement.alert.repository;
import com.assetmanagement.alert.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findAllByProjectIdOrderByNameAsc(UUID projectId);
    Optional<AlertRule> findByIdAndProjectId(UUID id, UUID projectId);
    List<AlertRule> findAllByProjectIdAndEnabledTrue(UUID projectId);
}
