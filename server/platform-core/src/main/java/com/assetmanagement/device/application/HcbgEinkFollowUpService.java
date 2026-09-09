package com.assetmanagement.device.application;

import com.assetmanagement.device.HcbgDownlinkMessages;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.domain.HcbgEinkOp;
import com.assetmanagement.device.repository.EinkPushJobRepository;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.device.repository.HcbgEinkOpRepository;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HCBG e-ink push is not supported. This service only tears down leftover GATT sessions
 * so they cannot steal the single connection from buzzer.
 */
@Service
public class HcbgEinkFollowUpService {

    private static final Logger log = LoggerFactory.getLogger(HcbgEinkFollowUpService.class);
    private static final String DOWNLINK_TOPIC = "SrvData";

    private final HcbgEinkOpRepository einkOpRepository;
    private final EinkPushJobRepository einkPushJobRepository;
    private final GatewayRepository gatewayRepository;
    private final MqttOutboundCommandRepository outboundCommandRepository;

    public HcbgEinkFollowUpService(
            HcbgEinkOpRepository einkOpRepository,
            EinkPushJobRepository einkPushJobRepository,
            GatewayRepository gatewayRepository,
            MqttOutboundCommandRepository outboundCommandRepository
    ) {
        this.einkOpRepository = einkOpRepository;
        this.einkPushJobRepository = einkPushJobRepository;
        this.gatewayRepository = gatewayRepository;
        this.outboundCommandRepository = outboundCommandRepository;
    }

    @Transactional
    public void onUplink(UUID projectId, String payload) {
        abortUnsupportedOps();
    }

    @Transactional
    public void pollDueOps() {
        abortUnsupportedOps();
    }

    private void abortUnsupportedOps() {
        Instant now = Instant.now();
        List<HcbgEinkOp> open = einkOpRepository.findTop20ByStatusInAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                List.of(HcbgEinkOp.STATUS_PENDING, HcbgEinkOp.STATUS_RUNNING),
                now
        );
        for (HcbgEinkOp op : open) {
            Gateway gateway = gatewayRepository.findById(op.getGatewayId()).orElse(null);
            fail(op, gateway == null ? null : gateway.getMacAddress(), "HCBG 网关不支持墨水屏改屏");
        }
    }

    private void fail(HcbgEinkOp op, String gatewayMac, String message) {
        if (gatewayMac != null) {
            outboundCommandRepository.saveAndFlush(new MqttOutboundCommand(
                    op.getTenantId(),
                    op.getProjectId(),
                    op.getConnectionId(),
                    DOWNLINK_TOPIC,
                    HcbgDownlinkMessages.connAddrDisconn(gatewayMac, op.getBeaconMac()),
                    1,
                    false
            ));
        }
        op.markFailed();
        einkOpRepository.save(op);
        if (op.getJobId() != null) {
            einkPushJobRepository.findById(op.getJobId()).ifPresent(job -> {
                job.markFailed(message);
                einkPushJobRepository.save(job);
            });
        }
        log.warn("HCBG eink aborted gw={} beacon={} : {}", gatewayMac, op.getBeaconMac(), message);
    }
}
