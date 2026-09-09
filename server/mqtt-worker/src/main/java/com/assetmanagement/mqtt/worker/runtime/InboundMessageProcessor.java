package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.asset.repository.AssetBeaconBindingRepository;
import com.assetmanagement.device.application.HcbgEinkFollowUpService;
import com.assetmanagement.device.application.HcbgGattFollowUpService;
import com.assetmanagement.device.EinkCapabilityDetector;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.inventory.application.InventoryDispatchService;
import com.assetmanagement.inventory.domain.InventorySession;
import com.assetmanagement.inventory.repository.InventoryItemRepository;
import com.assetmanagement.inventory.repository.InventorySessionRepository;
import com.assetmanagement.mqtt.MqttPayloadGatewayMac;
import com.assetmanagement.mqtt.domain.MqttInboxMessage;
import com.assetmanagement.mqtt.inbox.MqttRecentInboxRecord;
import com.assetmanagement.mqtt.inbox.MqttRecentInboxStore;
import com.assetmanagement.mqtt.parser.AdvSrpParser;
import com.assetmanagement.mqtt.parser.BatterySignalResolver;
import com.assetmanagement.mqtt.parser.BeaconBatteryParser;
import com.assetmanagement.mqtt.parser.GwStatusParser;
import com.assetmanagement.mqtt.parser.HcbgJsonParser;
import com.assetmanagement.mqtt.repository.MqttInboxMessageRepository;
import com.assetmanagement.shared.util.MacAddresses;
import com.assetmanagement.tracking.ScanDailyStatWriter;
import com.assetmanagement.tracking.ScanIngestStore;
import com.assetmanagement.tracking.domain.ScanEvent;
import com.assetmanagement.tracking.repository.ScanEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class InboundMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageProcessor.class);

    private final WorkerRlsContext rlsContext;
    private final MqttInboxMessageRepository inboxRepository;
    private final MqttRecentInboxStore recentInboxStore;
    private final BeaconRepository beaconRepository;
    private final GatewayRepository gatewayRepository;
    private final ScanEventRepository scanEventRepository;
    private final ScanDailyStatWriter scanDailyStatWriter;
    private final ScanIngestStore scanIngestStore;
    private final AssetBeaconBindingRepository bindingRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryDispatchService inventoryDispatchService;
    private final BeaconPresenceRecorder presenceRecorder;
    private final HcbgGattFollowUpService hcbgGattFollowUpService;
    private final HcbgEinkFollowUpService hcbgEinkFollowUpService;

    public InboundMessageProcessor(
            WorkerRlsContext rlsContext,
            MqttInboxMessageRepository inboxRepository,
            MqttRecentInboxStore recentInboxStore,
            BeaconRepository beaconRepository,
            GatewayRepository gatewayRepository,
            ScanEventRepository scanEventRepository,
            ScanDailyStatWriter scanDailyStatWriter,
            ScanIngestStore scanIngestStore,
            AssetBeaconBindingRepository bindingRepository,
            InventorySessionRepository inventorySessionRepository,
            InventoryItemRepository inventoryItemRepository,
            InventoryDispatchService inventoryDispatchService,
            BeaconPresenceRecorder presenceRecorder,
            HcbgGattFollowUpService hcbgGattFollowUpService,
            HcbgEinkFollowUpService hcbgEinkFollowUpService
    ) {
        this.rlsContext = rlsContext;
        this.inboxRepository = inboxRepository;
        this.recentInboxStore = recentInboxStore;
        this.beaconRepository = beaconRepository;
        this.gatewayRepository = gatewayRepository;
        this.scanEventRepository = scanEventRepository;
        this.scanDailyStatWriter = scanDailyStatWriter;
        this.scanIngestStore = scanIngestStore;
        this.bindingRepository = bindingRepository;
        this.inventorySessionRepository = inventorySessionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryDispatchService = inventoryDispatchService;
        this.presenceRecorder = presenceRecorder;
        this.hcbgGattFollowUpService = hcbgGattFollowUpService;
        this.hcbgEinkFollowUpService = hcbgEinkFollowUpService;
    }

    public void process(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            String topic,
            int qos,
            boolean retained,
            byte[] payload,
            Instant receivedAt
    ) {
        try {
            rlsContext.asPlatform(() -> persist(
                    tenantId, projectId, connectionId, topic, qos, retained, payload, receivedAt));
        } catch (OptimisticLockingFailureException ex) {
            log.warn("Optimistic lock on MQTT persist, retrying once topic={} bytes={}",
                    topic, payload == null ? 0 : payload.length);
            try {
                rlsContext.asPlatform(() -> persist(
                        tenantId, projectId, connectionId, topic, qos, retained, payload, receivedAt));
            } catch (Exception retryEx) {
                log.error("MQTT persist retry failed topic={} snippet={}",
                        topic, snippet(payload), retryEx);
            }
        } catch (Exception ex) {
            log.error("MQTT persist failed topic={} snippet={}", topic, snippet(payload), ex);
        }
    }

    private static String snippet(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        return snippet(new String(payload, StandardCharsets.UTF_8));
    }

    private static String snippet(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    @Transactional
    protected void persist(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            String topic,
            int qos,
            boolean retained,
            byte[] payload,
            Instant receivedAt
    ) {
        String text = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
        String sha = sha256(payload == null ? new byte[0] : payload);
        String idempotency = connectionId + "|" + topic + "|" + sha + "|" + receivedAt.getEpochSecond();
        if (!recentInboxStore.tryClaim(projectId, idempotency)) {
            return;
        }
        try {
            persistClaimed(
                    tenantId, projectId, connectionId, topic, qos, retained, text, sha, idempotency, receivedAt);
        } catch (RuntimeException ex) {
            recentInboxStore.release(projectId, idempotency);
            throw ex;
        }
    }

    private void persistClaimed(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            String topic,
            int qos,
            boolean retained,
            String text,
            String sha,
            String idempotency,
            Instant receivedAt
    ) {
        MqttInboxMessage inbox = new MqttInboxMessage(
                tenantId, projectId, connectionId, topic, qos, retained, text, sha, idempotency);
        inbox.received(receivedAt);

        var status = GwStatusParser.tryParse(text);
        if (status.isPresent()) {
            applyGatewayStatus(tenantId, projectId, status.get(), receivedAt);
            Map<String, Object> parsed = new HashMap<>();
            parsed.put("gatewayMac", status.get().gatewayMac());
            parsed.put("state", status.get().state());
            parsed.put("online", status.get().online());
            inbox.markParsed(GwStatusParser.MESSAGE_TYPE, parsed);
            finishInbox(inbox, status.get().gatewayMac());
            return;
        }

        var parsed = AdvSrpParser.tryParse(text);
        if (parsed.isPresent()) {
            var message = parsed.get();
            inbox.markParsed(message.messageType(), message.raw());
            AdvSrpParser.ParsedDevice device = message.target();
            if (device != null && device.addr() != null && !device.addr().isBlank()) {
                Gateway gateway = resolveGateway(tenantId, projectId, message.gatewayMac(), true, receivedAt);
                applyRegisteredScan(
                        tenantId, projectId, null, gateway,
                        device.addr(), device.rssi(), device.deviceTime(), device.name(),
                        device.ibcnUuid(), device.ibcnMajor(), device.ibcnMinor(), device.ibcnRssiAt1m(),
                        device.advRaw(), device.srpRaw(), receivedAt);
            }
            finishInbox(inbox, message.gatewayMac());
            return;
        }

        var hcbg = HcbgJsonParser.tryParse(text);
        if (hcbg.isPresent()) {
            applyHcbg(tenantId, projectId, inbox, hcbg.get(), receivedAt);
            try {
                hcbgGattFollowUpService.onUplink(projectId, text);
            } catch (Exception ex) {
                log.warn("HCBG GATT follow-up failed snippet={}", snippet(text), ex);
            }
            try {
                hcbgEinkFollowUpService.onUplink(projectId, text);
            } catch (Exception ex) {
                log.warn("HCBG e-ink follow-up failed snippet={}", snippet(text), ex);
            }
            finishInbox(inbox, hcbg.get().gatewayMac());
            return;
        }

        inbox.markUnsupported(null, "No adv_srp/gw_status/hcbg parser matched payload");
        finishInbox(inbox, MqttPayloadGatewayMac.extract(text));
    }

    private void finishInbox(MqttInboxMessage inbox, String gatewayMac) {
        if (MqttRecentInboxRecord.shouldPersist(inbox.getParseStatus())) {
            if (!inboxRepository.existsByProjectIdAndIdempotencyKey(inbox.getProjectId(), inbox.getIdempotencyKey())) {
                inbox = inboxRepository.save(inbox);
            }
        }
        recentInboxStore.record(MqttRecentInboxRecord.from(inbox, MacAddresses.compact(gatewayMac)));
    }

    private void applyHcbg(
            UUID tenantId,
            UUID projectId,
            MqttInboxMessage inbox,
            HcbgJsonParser.ParsedMessage message,
            Instant receivedAt
    ) {
        String type = message.heartbeat()
                ? HcbgJsonParser.HEARTBEAT_MESSAGE_TYPE
                : (message.reportType() == null ? message.pktType() : message.reportType());
        inbox.markParsed(type, message.raw());

        boolean touch = message.gatewayMac() != null && !message.gatewayMac().isBlank();
        Gateway gateway = resolveGateway(tenantId, projectId, message.gatewayMac(), touch, receivedAt);
        for (HcbgJsonParser.ParsedDevice device : message.devices()) {
            Beacon beacon = applyRegisteredScan(
                    tenantId, projectId, null, gateway,
                    device.addr(), device.rssi(), device.deviceTime(), device.name(),
                    device.ibcnUuid(), device.ibcnMajor(), device.ibcnMinor(), device.ibcnRssiAt1m(),
                    device.advRaw(), device.srpRaw(), receivedAt);
            if (beacon != null && device.hasBattery()) {
                applyHcbgBattery(beacon, device.battType(), device.battValue());
                beaconRepository.save(beacon);
            }
        }
    }

    private Beacon applyRegisteredScan(
            UUID tenantId,
            UUID projectId,
            UUID inboxId,
            Gateway gateway,
            String addr,
            Integer rssi,
            Instant deviceTime,
            String name,
            String ibcnUuid,
            Integer ibcnMajor,
            Integer ibcnMinor,
            Integer ibcnRssiAt1m,
            String advRaw,
            String srpRaw,
            Instant receivedAt
    ) {
        String macCompact = MacAddresses.compact(addr);
        if (macCompact.isEmpty()) {
            return null;
        }
        String macCanonical = MacAddresses.canonical(macCompact);
        Beacon beacon = beaconRepository.findByProjectIdAndMacCompact(projectId, macCompact).orElse(null);
        if (beacon == null || "ARCHIVED".equalsIgnoreCase(beacon.getStatus())) {
            log.debug("Skip unregistered beacon mac={} project={}", macCompact, projectId);
            return null;
        }
        if (!macCanonical.equalsIgnoreCase(beacon.getMacAddress())) {
            beacon.update(
                    beacon.getName(),
                    macCanonical,
                    beacon.getIbeaconUuid(),
                    beacon.getIbeaconMajor(),
                    beacon.getIbeaconMinor(),
                    beacon.getRssiAt1m(),
                    beacon.getStatus()
            );
        }
        UUID hop = gateway == null ? beacon.getLastGatewayId() : gateway.getId();
        if (hop == null) {
            hop = scanEventRepository.findFirstByBeaconIdAndGatewayIdIsNotNullOrderByReceivedAtDesc(beacon.getId())
                    .map(ScanEvent::getGatewayId)
                    .orElse(null);
        }
        beacon.recordScan(
                receivedAt,
                rssi,
                hop,
                name,
                ibcnUuid,
                ibcnMajor,
                ibcnMinor,
                ibcnRssiAt1m
        );
        beacon.recordAdvRaw(advRaw);
        if (srpRaw != null && !srpRaw.isBlank()) {
            beacon.recordSrpRaw(srpRaw);
        }
        if (EinkCapabilityDetector.looksLikeEink(name, advRaw, srpRaw)) {
            beacon.applyEinkDetection(
                    EinkCapabilityDetector.detect(name, advRaw, srpRaw).profile());
        }
        applyBatteryAuto(beacon, advRaw, srpRaw);
        beacon = beaconRepository.save(beacon);

        UUID gatewayId = gateway == null ? null : gateway.getId();
        try {
            scanDailyStatWriter.increment(tenantId, projectId, receivedAt);
        } catch (RuntimeException ex) {
            log.warn("Scan daily increment skipped: {}", ex.getMessage());
        }
        scanIngestStore.recordHit(projectId);
        if (scanIngestStore.shouldPersistScan(beacon.getId(), gatewayId, rssi)) {
            ScanEvent scan = new ScanEvent(tenantId, projectId, macCanonical);
            scan.fill(
                    inboxId,
                    gatewayId,
                    beacon.getId(),
                    rssi,
                    name,
                    deviceTime,
                    ibcnUuid,
                    ibcnMajor,
                    ibcnMinor,
                    receivedAt
            );
            scanEventRepository.save(scan);
        }
        presenceRecorder.recordOnlineFromScan(
                beacon,
                gateway == null ? null : gateway.getId(),
                receivedAt
        );
        applyOpenInventoryHits(
                projectId,
                beacon.getId(),
                gateway == null ? null : gateway.getId(),
                rssi,
                receivedAt
        );
        return beacon;
    }

    private void applyHcbgBattery(Beacon beacon, Integer battType, Integer battValue) {
        if (battValue == null || battValue < 0) {
            return;
        }
        int percent;
        if (battType != null && battType == 1) {
            percent = BeaconBatteryParser.voltageToPercent(battValue);
        } else {
            percent = Math.max(0, Math.min(100, battValue));
        }
        int level = BeaconBatteryParser.levelOfPercent(percent);
        beacon.recordFindMyBattery(level, BeaconBatteryParser.labelOf(level), percent);
    }

    private void applyBatteryAuto(Beacon beacon, String advRaw, String srpRaw) {
        var resolved = BatterySignalResolver.resolve(advRaw, srpRaw);
        if (resolved.isEmpty()) {
            return;
        }
        var info = resolved.get();
        beacon.recordFindMyBattery(info.level(), info.label(), info.percentHint());
        beacon.setSignalProfile(info.protocol());
    }

    private void applyOpenInventoryHits(
            UUID projectId,
            UUID beaconId,
            UUID gatewayId,
            Integer rssi,
            Instant receivedAt
    ) {
        var binding = bindingRepository.findByBeaconIdAndActiveTrue(beaconId);
        if (binding.isEmpty()) {
            return;
        }
        UUID assetId = binding.get().getAssetId();
        Instant now = Instant.now();
        for (InventorySession session : inventorySessionRepository.findAllByProjectIdAndStatus(projectId, "OPEN")) {
            if (session.isExpired(now)) {
                continue;
            }
            var allowedGateways = InventoryDispatchService.parseUuidCsv(session.getGatewayJson());
            if (!allowedGateways.isEmpty()
                    && (gatewayId == null || !allowedGateways.contains(gatewayId))) {
                continue;
            }
            inventoryItemRepository.findBySessionIdAndAssetId(session.getId(), assetId).ifPresent(item -> {
                boolean firstHit = item.markFound(receivedAt, rssi, gatewayId, beaconId);
                inventoryItemRepository.save(item);
                if (firstHit) {
                    session.incrementFound();
                    if (session.isComplete()) {
                        session.close(receivedAt);
                        inventorySessionRepository.save(session);
                        inventoryDispatchService.dispatchStop(session);
                        log.info(
                                "Inventory {} completed early ({}/{}), dispatched stop",
                                session.getCode(),
                                session.getFoundCount(),
                                session.getExpectedCount()
                        );
                    } else {
                        inventorySessionRepository.save(session);
                    }
                }
            });
        }
    }

    private void applyGatewayStatus(
            UUID tenantId,
            UUID projectId,
            GwStatusParser.ParsedStatus status,
            Instant receivedAt
    ) {
        if (status.gatewayMac() == null || status.gatewayMac().isBlank()) {
            return;
        }
        Gateway gateway = resolveGateway(tenantId, projectId, status.gatewayMac(), false, receivedAt);
        if (gateway == null) {
            return;
        }
        if (status.online()) {
            gatewayRepository.touchOnline(gateway.getId(), receivedAt);
        } else if (gateway.getLastSeenAt() != null
                && gateway.getLastSeenAt().isAfter(receivedAt.minusSeconds(20))) {
            log.debug("Ignore gw_status offline while lastSeen is fresh mac={}", status.gatewayMac());
        } else {
            gatewayRepository.markOfflineById(gateway.getId(), receivedAt);
        }
    }

    /**
     * @param touchPresence true 时刷新 ONLINE/lastSeen；业务扫描应传 false
     */
    private Gateway resolveGateway(
            UUID tenantId,
            UUID projectId,
            String gatewayMac,
            boolean touchPresence,
            Instant receivedAt
    ) {
        String macCompact = MacAddresses.compact(gatewayMac);
        if (macCompact.isEmpty()) {
            return null;
        }
        String macCanonical = MacAddresses.canonical(macCompact);
        Gateway gateway = gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .filter(candidate -> MacAddresses.equalsIgnoreFormat(macCompact, candidate.getMacAddress()))
                .filter(candidate -> !"ARCHIVED".equalsIgnoreCase(candidate.getStatus()))
                .min((left, right) -> {
                    int vendor = Boolean.compare(right.isHcbg(), left.isHcbg());
                    if (vendor != 0) {
                        return vendor;
                    }
                    Instant leftSeen = left.getLastSeenAt();
                    Instant rightSeen = right.getLastSeenAt();
                    if (leftSeen != null && rightSeen != null && !leftSeen.equals(rightSeen)) {
                        return rightSeen.compareTo(leftSeen);
                    }
                    if (leftSeen != null && rightSeen == null) {
                        return -1;
                    }
                    if (leftSeen == null && rightSeen != null) {
                        return 1;
                    }
                    String leftName = left.getName() == null ? "" : left.getName();
                    String rightName = right.getName() == null ? "" : right.getName();
                    return leftName.compareToIgnoreCase(rightName);
                })
                .orElse(null);
        if (gateway == null) {
            log.info("Ignore uplink from unregistered gateway mac={} project={}", macCompact, projectId);
            return null;
        }
        if (!macCanonical.equalsIgnoreCase(gateway.getMacAddress() == null ? "" : gateway.getMacAddress())) {
            gateway.update(
                    gateway.getName(),
                    macCanonical,
                    gateway.getClientId(),
                    gateway.getMapId(),
                    gateway.getZoneId(),
                    gateway.getCoordinateX(),
                    gateway.getCoordinateY(),
                    gateway.getStatus()
            );
            gateway = gatewayRepository.save(gateway);
        }
        if (touchPresence) {
            gatewayRepository.touchOnline(gateway.getId(), receivedAt);
            return gatewayRepository.findById(gateway.getId()).orElse(gateway);
        }
        return gateway;
    }

    private static String sha256(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
