package com.assetmanagement.notification.repository;

import com.assetmanagement.notification.domain.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, UUID> {
    List<NotificationChannel> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<NotificationChannel> findByIdAndUserId(UUID id, UUID userId);
}
