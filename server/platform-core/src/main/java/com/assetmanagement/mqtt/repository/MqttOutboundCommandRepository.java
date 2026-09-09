package com.assetmanagement.mqtt.repository;

import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.domain.OutboundCommandStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MqttOutboundCommandRepository extends JpaRepository<MqttOutboundCommand, UUID> {

    List<MqttOutboundCommand> findTop50ByStatusOrderByCreatedAtAsc(OutboundCommandStatus status);

    Page<MqttOutboundCommand> findAllByTenantIdAndProjectIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID projectId,
            Pageable pageable
    );
}
