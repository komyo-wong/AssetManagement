package com.assetmanagement.notification.repository;

import com.assetmanagement.notification.domain.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, UUID> {
    List<NotificationSubscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationSubscription> findAllByProjectIdAndEnabledTrue(UUID projectId);

    Optional<NotificationSubscription> findByIdAndUserId(UUID id, UUID userId);
}
