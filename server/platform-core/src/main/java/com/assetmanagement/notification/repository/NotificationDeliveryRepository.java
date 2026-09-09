package com.assetmanagement.notification.repository;

import com.assetmanagement.notification.domain.NotificationDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    Page<NotificationDelivery> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<NotificationDelivery> findFirstBySubscriptionIdAndAlertEventIdAndEventKindAndStatusOrderByCreatedAtDesc(
            UUID subscriptionId,
            UUID alertEventId,
            String eventKind,
            String status
    );

    boolean existsByAlertEventId(UUID alertEventId);
}
