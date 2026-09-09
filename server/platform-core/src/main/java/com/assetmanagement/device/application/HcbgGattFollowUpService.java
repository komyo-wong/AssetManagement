package com.assetmanagement.device.application;

import com.assetmanagement.device.HcbgDownlinkMessages;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.domain.HcbgGattOp;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.device.repository.HcbgGattOpRepository;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.domain.OutboundCommandStatus;
import com.assetmanagement.mqtt.parser.HcbgJsonParser;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import com.assetmanagement.shared.util.MacAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HcbgGattFollowUpService {

    private static final Logger log = LoggerFactory.getLogger(HcbgGattFollowUpService.class);
    private static final String DOWNLINK_TOPIC = "SrvData";
    private static final Duration PENDING_TTL = Duration.ofSeconds(120);
    /** User-facing chirp spacing: one {@code 01} write every 2 seconds. */
    private static final Duration PULSE_INTERVAL = Duration.ofSeconds(2);

    private final HcbgGattOpRepository gattOpRepository;
    private final GatewayRepository gatewayRepository;
    private final MqttOutboundCommandRepository outboundCommandRepository;

    public HcbgGattFollowUpService(
            HcbgGattOpRepository gattOpRepository,
            GatewayRepository gatewayRepository,
            MqttOutboundCommandRepository outboundCommandRepository
    ) {
        this.gattOpRepository = gattOpRepository;
        this.gatewayRepository = gatewayRepository;
        this.outboundCommandRepository = outboundCommandRepository;
    }

    @Transactional
    public void onUplink(UUID projectId, String payload) {
        HcbgJsonParser.tryParseDataSent(payload).ifPresent(event -> {
            if (!event.toDevice() || event.addr() == null) {
                return;
            }
            HcbgGattOp op = findPending(projectId, event.gatewayMac(), event.addr());
            if (op == null) {
                return;
            }
            log.info("HCBG sta_data_sent gw={} beacon={} pulses={}/{} bytes={}",
                    event.gatewayMac(), event.addr(), op.getPulsesDone(), op.getPulseCount(), event.dataSent());
        });
        HcbgJsonParser.tryParseConnState(payload).ifPresent(event -> {
            if (!event.readyForDiscovery() || event.deviceMac() == null || event.gatewayMac() == null) {
                return;
            }
            HcbgGattOp op = findPending(projectId, event.gatewayMac(), event.deviceMac());
            if (op == null) {
                return;
            }
            if (op.getCharHandle() != null) {
                // First 01 is in the connect send_infos. Start the 2s clock only after ready,
                // otherwise a second conn_addr_request aborts the link before it beeps.
                if (op.getPulsesDone() == 0) {
                    if ("BUZZ_STOP".equals(op.getKind())) {
                        op.markBurstComplete(Instant.now().plus(PULSE_INTERVAL));
                    } else {
                        op.markPulseQueued(Instant.now().plus(PULSE_INTERVAL));
                    }
                    gattOpRepository.save(op);
                }
                log.info("HCBG link ready gw={} beacon={} handle={} pulses={}/{}",
                        event.gatewayMac(), event.deviceMac(), op.getCharHandle(),
                        op.getPulsesDone(), op.getPulseCount());
                return;
            }
            if (op.getLastOutboundId() != null) {
                return;
            }
            UUID discoveryId = enqueue(op, HcbgDownlinkMessages.connTriggerBuzzerDiscovery(
                    event.gatewayMac(), event.deviceMac()));
            op.rememberOutbound(discoveryId);
            gattOpRepository.save(op);
        });
        HcbgJsonParser.tryParseDiscovery(payload).ifPresent(event -> {
            if (event.deviceMac() == null || event.gatewayMac() == null) {
                return;
            }
            HcbgGattOp op = findPending(projectId, event.gatewayMac(), event.deviceMac());
            if (op == null || op.getCharHandle() != null) {
                return;
            }
            if (event.timedOut()) {
                log.info("HCBG discovery timed-out, waiting for completed gw={} beacon={}",
                        event.gatewayMac(), event.deviceMac());
                return;
            }
            if (!event.completed()) {
                return;
            }
            if (event.firstHandle() == null || event.firstHandle() <= 0) {
                op.markFailed();
                gattOpRepository.save(op);
                enqueue(op, HcbgDownlinkMessages.connAddrDisconn(event.gatewayMac(), event.deviceMac()));
                log.warn("HCBG discovery completed without handle gw={} beacon={}", event.gatewayMac(), event.deviceMac());
                return;
            }
            boolean stop = "BUZZ_STOP".equals(op.getKind());
            UUID outboundId = enqueue(op, HcbgDownlinkMessages.connAddrRequestBuzzer(
                    event.gatewayMac(), event.deviceMac(), 90, event.firstHandle(), op.getPulseCount(), stop));
            op.assignHandle(event.firstHandle());
            op.rememberOutbound(outboundId);
            op.markAwaitingLink(Instant.now().plus(PENDING_TTL));
            gattOpRepository.save(op);
            log.info("HCBG reconnect-with-write handle={} gw={} beacon={}",
                    event.firstHandle(), event.gatewayMac(), event.deviceMac());
        });
    }

    @Transactional
    public void pollDueOps() {
        Instant now = Instant.now();
        List<HcbgGattOp> due = gattOpRepository
                .findTop20ByStatusAndCharHandleIsNotNullAndNextActionAtLessThanEqualOrderByNextActionAtAsc(
                        HcbgGattOp.STATUS_PENDING, now);
        for (HcbgGattOp op : due) {
            advancePulse(op, now);
        }
        List<HcbgGattOp> stale = gattOpRepository
                .findTop20ByStatusAndCharHandleIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        HcbgGattOp.STATUS_PENDING, now.minus(PENDING_TTL));
        for (HcbgGattOp op : stale) {
            Gateway gateway = gatewayRepository.findById(op.getGatewayId()).orElse(null);
            op.markFailed();
            gattOpRepository.save(op);
            if (gateway != null && gateway.getMacAddress() != null) {
                enqueue(op, HcbgDownlinkMessages.connAddrDisconn(gateway.getMacAddress(), op.getBeaconMac()));
            }
            log.warn("HCBG buzzer timed out waiting for discovery beacon={}", op.getBeaconMac());
        }
    }

    private void advancePulse(HcbgGattOp op, Instant now) {
        Gateway gateway = gatewayRepository.findById(op.getGatewayId()).orElse(null);
        if (gateway == null || gateway.getMacAddress() == null || op.getCharHandle() == null) {
            return;
        }
        String gw = gateway.getMacAddress();
        String mac = op.getBeaconMac();
        int handle = op.getCharHandle();
        if (op.getLastOutboundId() != null) {
            MqttOutboundCommand last = outboundCommandRepository.findById(op.getLastOutboundId()).orElse(null);
            if (last == null || last.getStatus() == OutboundCommandStatus.PENDING) {
                return;
            }
            if (last.getStatus() == OutboundCommandStatus.FAILED) {
                op.markFailed();
                gattOpRepository.save(op);
                enqueue(op, HcbgDownlinkMessages.connAddrDisconn(gw, mac));
                log.warn("HCBG buzzer write failed beacon={} : {}", mac, last.getErrorMessage());
                return;
            }
            Instant sent = last.getSentAt() == null ? now : last.getSentAt();
            Instant due = op.getNextActionAt() != null ? op.getNextActionAt() : sent.plus(PULSE_INTERVAL);
            if (due.isAfter(now)) {
                return;
            }
            if (op.getPulsesDone() == 0) {
                op.markFailed();
                gattOpRepository.save(op);
                enqueue(op, HcbgDownlinkMessages.connAddrDisconn(gw, mac));
                log.warn("HCBG buzzer timed out waiting for ready beacon={}", mac);
                return;
            }
            if ("BUZZ_STOP".equals(op.getKind()) || op.getPulsesDone() >= op.getPulseCount()) {
                enqueue(op, HcbgDownlinkMessages.connAddrDisconn(gw, mac));
                op.markCompleted();
                gattOpRepository.save(op);
                return;
            }
        }
        UUID onId = enqueue(op, HcbgDownlinkMessages.connSendData(gw, mac, handle, "01"));
        op.markPulseQueued(now.plus(PULSE_INTERVAL));
        op.rememberOutbound(onId);
        gattOpRepository.save(op);
    }

    private HcbgGattOp findPending(UUID projectId, String gatewayMac, String deviceMac) {
        String compact = MacAddresses.compact(deviceMac);
        if (compact.isEmpty()) {
            return null;
        }
        List<HcbgGattOp> pending = gattOpRepository
                .findByProjectIdAndBeaconMacAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        projectId,
                        compact,
                        HcbgGattOp.STATUS_PENDING,
                        Instant.now().minus(PENDING_TTL)
                );
        for (HcbgGattOp op : pending) {
            Gateway gateway = gatewayRepository.findById(op.getGatewayId()).orElse(null);
            if (gateway == null
                    || "ARCHIVED".equalsIgnoreCase(gateway.getStatus())
                    || !gateway.isHcbg()) {
                continue;
            }
            if (gatewayMac == null
                    || gatewayMac.isBlank()
                    || MacAddresses.equalsIgnoreFormat(gateway.getMacAddress(), gatewayMac)) {
                return op;
            }
        }
        return null;
    }

    private UUID enqueue(HcbgGattOp op, String payload) {
        MqttOutboundCommand command = new MqttOutboundCommand(
                op.getTenantId(),
                op.getProjectId(),
                op.getConnectionId(),
                DOWNLINK_TOPIC,
                payload,
                0,
                false
        );
        outboundCommandRepository.saveAndFlush(command);
        return command.getId();
    }
}
