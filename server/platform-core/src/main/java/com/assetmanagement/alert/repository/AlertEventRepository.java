package com.assetmanagement.alert.repository;

import com.assetmanagement.alert.domain.AlertEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    Page<AlertEvent> findAllByProjectIdOrderByOpenedAtDesc(UUID projectId, Pageable pageable);

    Optional<AlertEvent> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, String status);

    Optional<AlertEvent> findFirstByProjectIdAndResourceTypeAndResourceIdAndStatusOrderByOpenedAtDesc(
            UUID projectId, String resourceType, UUID resourceId, String status);

    Optional<AlertEvent> findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusOrderByOpenedAtDesc(
            UUID projectId, UUID ruleId, String resourceType, UUID resourceId, String status);

    Optional<AlertEvent> findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusInOrderByOpenedAtDesc(
            UUID projectId, UUID ruleId, String resourceType, UUID resourceId, Collection<String> statuses);

    List<AlertEvent> findAllByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusIn(
            UUID projectId, UUID ruleId, String resourceType, UUID resourceId, Collection<String> statuses);

    Optional<AlertEvent> findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusOrderByResolvedAtDesc(
            UUID projectId, UUID ruleId, String resourceType, UUID resourceId, String status);

    List<AlertEvent> findAllByProjectIdAndStatus(UUID projectId, String status);
}
