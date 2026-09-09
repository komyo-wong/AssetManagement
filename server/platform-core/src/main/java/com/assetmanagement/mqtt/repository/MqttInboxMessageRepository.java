package com.assetmanagement.mqtt.repository;

import com.assetmanagement.mqtt.domain.MqttInboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MqttInboxMessageRepository extends JpaRepository<MqttInboxMessage, UUID> {
    boolean existsByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey);

    Slice<MqttInboxMessage> findAllByProjectId(UUID projectId, Pageable pageable);

    Slice<MqttInboxMessage> findAllByProjectIdAndParseStatus(
            UUID projectId,
            String parseStatus,
            Pageable pageable
    );

    /**
     * Match the reporting gateway only. A payload substring search on the MAC is wrong:
     * native scan reports embed other devices' addresses (including another gateway's BLE MAC).
     */
    @Query("""
            SELECT m FROM MqttInboxMessage m
             WHERE m.projectId = :projectId
               AND (:parseStatus IS NULL OR m.parseStatus = :parseStatus)
               AND (
                    LOWER(m.payloadText) LIKE CONCAT('%"gw_addr":"', :compactMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%"gw_addr": "', :compactMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%"gw_addr":"', :colonMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%"gw":"', :compactMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%"gw": "', :compactMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%"gw":"', :colonMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%"gw": "', :colonMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%网关：', :compactMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%网关:', :compactMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%网关：', :colonMac, '%')
                 OR LOWER(m.payloadText) LIKE CONCAT('%网关:', :colonMac, '%')
               )
            """)
    Slice<MqttInboxMessage> findByProjectIdAndGatewayMac(
            @Param("projectId") UUID projectId,
            @Param("parseStatus") String parseStatus,
            @Param("compactMac") String compactMac,
            @Param("colonMac") String colonMac,
            Pageable pageable
    );

    @Query(value = "SELECT CAST(COALESCE(reltuples, 0) AS bigint) FROM pg_class WHERE oid = 'mqtt_inbox'::regclass", nativeQuery = true)
    Long estimateTotalRows();
}
