package com.assetmanagement.device.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hcbg_gatt_ops")
public class HcbgGattOp extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;
    @Column(name = "gateway_id", nullable = false, updatable = false)
    private UUID gatewayId;
    @Column(name = "beacon_mac", nullable = false, length = 12)
    private String beaconMac;
    @Column(name = "kind", nullable = false, length = 32)
    private String kind;
    @Column(name = "status", nullable = false, length = 24)
    private String status = STATUS_PENDING;
    @Column(name = "pulse_count", nullable = false)
    private int pulseCount = 1;
    @Column(name = "char_handle")
    private Integer charHandle;
    @Column(name = "pulses_done", nullable = false)
    private int pulsesDone;
    @Column(name = "waiting_off", nullable = false)
    private boolean waitingOff;
    @Column(name = "next_action_at")
    private Instant nextActionAt;
    @Column(name = "last_outbound_id")
    private UUID lastOutboundId;

    protected HcbgGattOp() {
    }

    public HcbgGattOp(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            UUID gatewayId,
            String beaconMac,
            String kind,
            int pulseCount
    ) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.connectionId = connectionId;
        this.gatewayId = gatewayId;
        this.beaconMac = beaconMac;
        this.kind = kind;
        this.pulseCount = Math.max(1, pulseCount);
        this.status = STATUS_PENDING;
        this.pulsesDone = 0;
        this.waitingOff = false;
    }

    public void assignHandle(int handle) {
        this.charHandle = handle;
        this.nextActionAt = Instant.now();
        this.waitingOff = false;
    }

    /** Known write handle from vendor discovery; do not start the pulse timer yet. */
    public void seedWriteHandle(int handle) {
        this.charHandle = handle;
    }

    /** Connect is in flight; do not send another {@code conn_addr_request} until ready. */
    public void markAwaitingLink(Instant timeoutAt) {
        this.waitingOff = false;
        this.pulsesDone = 0;
        this.nextActionAt = timeoutAt;
    }

    public void markOnWritten(Instant nextOffAt) {
        this.waitingOff = true;
        this.nextActionAt = nextOffAt;
    }

    /** One {@code 01} was queued; wait {@code nextAt} before the next chirp. */
    public void markPulseQueued(Instant nextAt) {
        this.waitingOff = false;
        this.pulsesDone++;
        this.nextActionAt = nextAt;
    }

    public void markBurstComplete(Instant disconnectAt) {
        this.waitingOff = false;
        this.pulsesDone = this.pulseCount;
        this.nextActionAt = disconnectAt;
    }

    public void markOffWritten(Instant nextOnAt) {
        this.waitingOff = false;
        this.pulsesDone++;
        this.nextActionAt = nextOnAt;
    }

    public void rememberOutbound(UUID outboundId) {
        this.lastOutboundId = outboundId;
    }

    public void scheduleAt(Instant when) {
        this.nextActionAt = when;
    }

    public void markCompleted() {
        this.status = STATUS_COMPLETED;
        this.nextActionAt = null;
    }

    public void markFailed() {
        this.status = STATUS_FAILED;
        this.nextActionAt = null;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getConnectionId() { return connectionId; }
    public UUID getGatewayId() { return gatewayId; }
    public String getBeaconMac() { return beaconMac; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public int getPulseCount() { return pulseCount; }
    public Integer getCharHandle() { return charHandle; }
    public int getPulsesDone() { return pulsesDone; }
    public boolean isWaitingOff() { return waitingOff; }
    public Instant getNextActionAt() { return nextActionAt; }
    public UUID getLastOutboundId() { return lastOutboundId; }
}
