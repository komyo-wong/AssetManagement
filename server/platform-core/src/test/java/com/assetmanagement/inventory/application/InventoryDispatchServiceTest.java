package com.assetmanagement.inventory.application;

import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.inventory.domain.InventorySession;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryDispatchServiceTest {

    @Mock
    private GatewayRepository gatewayRepository;
    @Mock
    private MqttConnectionRepository mqttConnectionRepository;
    @Mock
    private MqttOutboundCommandRepository outboundCommandRepository;
    @Mock
    private MqttConnection connection;
    @Mock
    private InventorySession session;

    private InventoryDispatchService service;
    private UUID tenantId;
    private UUID projectId;
    private UUID sessionId;
    private UUID connectionId;

    @BeforeEach
    void setUp() {
        service = new InventoryDispatchService(gatewayRepository, mqttConnectionRepository, outboundCommandRepository);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        when(connection.getId()).thenReturn(connectionId);
        when(connection.isEnabled()).thenReturn(true);
        when(connection.getScope()).thenReturn(MqttConnectionScope.PROJECT);
        when(connection.getEndpointRole()).thenReturn(MqttEndpointRole.PRIMARY);
        when(connection.getPriority()).thenReturn(1);
        when(mqttConnectionRepository.findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(projectId))
                .thenReturn(List.of(connection));
        when(session.getId()).thenReturn(sessionId);
        when(session.getTenantId()).thenReturn(tenantId);
        when(session.getProjectId()).thenReturn(projectId);
        when(session.getWindowSeconds()).thenReturn(120);
    }

    @Test
    void startDispatchesHcbgScanCommandsInsteadOfInventoryStart() {
        Gateway hcbg = new Gateway(tenantId, projectId, "0002", "HCBG-1");
        hcbg.setVendor(Gateway.VENDOR_HCBG);
        hcbg.update("HCBG-1", "f3bd12dc3b6d", null, null, null, null, null, "ONLINE");

        service.dispatchStart(session, List.of(hcbg), List.of("aabbccddeeff"), List.of());

        ArgumentCaptor<MqttOutboundCommand> captor = ArgumentCaptor.forClass(MqttOutboundCommand.class);
        verify(outboundCommandRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        List<MqttOutboundCommand> commands = captor.getAllValues();
        List<String> payloads = commands.stream().map(MqttOutboundCommand::getPayload).toList();
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"cmd\":\"scan_report_onoff\"") && p.contains("\"enable\":true")));
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"cmd\":\"scan_request_onoff\"") && p.contains("\"enable\":true")));
        assertTrue(payloads.stream().noneMatch(p -> p.contains("inventory_start")));
        assertTrue(payloads.stream().allMatch(p -> p.contains("\"pkt_type\":\"command\"") && p.contains("\"gw_addr\":\"f3bd12dc3b6d\"")));
        assertTrue(commands.stream().allMatch(c -> "SrvData".equals(c.getTopic())));
    }

    @Test
    void startKeepsNativeInventoryStart() {
        Gateway nativeGw = new Gateway(tenantId, projectId, "0001", "Native");
        nativeGw.setVendor(Gateway.VENDOR_NATIVE);
        nativeGw.update("Native", "aabbccddeeff", null, null, null, null, null, "ONLINE");

        service.dispatchStart(session, List.of(nativeGw), List.of("112233445566"), List.of());

        ArgumentCaptor<MqttOutboundCommand> captor = ArgumentCaptor.forClass(MqttOutboundCommand.class);
        verify(outboundCommandRepository).saveAndFlush(captor.capture());
        String payload = captor.getValue().getPayload();
        assertTrue(payload.contains("\"cmd\":\"inventory_start\""));
        assertTrue(payload.contains("aabbccddeeff"));
        assertTrue(payload.contains(sessionId.toString()));
    }

    @Test
    void stopSkipsHcbgSoRealtimeScanContinues() {
        UUID gatewayId = UUID.randomUUID();
        Gateway hcbg = new Gateway(tenantId, projectId, "0002", "HCBG-1");
        hcbg.setVendor(Gateway.VENDOR_HCBG);
        hcbg.update("HCBG-1", "f3bd12dc3b6d", null, null, null, null, null, "ONLINE");
        when(session.getGatewayJson()).thenReturn(gatewayId.toString());
        when(gatewayRepository.findByIdAndProjectId(gatewayId, projectId)).thenReturn(Optional.of(hcbg));

        service.dispatchStop(session);

        verify(outboundCommandRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
