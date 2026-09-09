package com.assetmanagement.device.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "hcbg_eink_ops")
public class HcbgEinkOp extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String PHASE_CONNECTING = "CONNECTING";
    public static final String PHASE_WAIT_BEGIN_BW = "WAIT_BEGIN_BW";
    public static final String PHASE_SEND_BW = "SEND_BW";
    public static final String PHASE_WAIT_COMMIT_BW = "WAIT_COMMIT_BW";
    public static final String PHASE_WAIT_BEGIN_RED = "WAIT_BEGIN_RED";
    public static final String PHASE_SEND_RED = "SEND_RED";
    public static final String PHASE_WAIT_COMMIT_RED = "WAIT_COMMIT_RED";
    public static final String PHASE_WAIT_REFRESH = "WAIT_REFRESH";
    public static final String PHASE_DONE = "DONE";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;
    @Column(name = "gateway_id", nullable = false, updatable = false)
    private UUID gatewayId;
    @Column(name = "job_id")
    private UUID jobId;
    @Column(name = "beacon_mac", nullable = false, length = 12)
    private String beaconMac;
    @Column(name = "passkey", length = 16)
    private String passkey;
    @Column(name = "status", nullable = false, length = 24)
    private String status = STATUS_PENDING;
    @Column(name = "phase", nullable = false, length = 32)
    private String phase = PHASE_CONNECTING;
    @Column(name = "session_handle")
    private Integer sessionHandle;
    @Column(name = "data_handle")
    private Integer dataHandle;
    @Column(name = "unlock_handle")
    private Integer unlockHandle;
    @Column(name = "plane", nullable = false)
    private short plane;
    @Column(name = "send_offset", nullable = false)
    private int sendOffset;
    @Column(name = "poll_count", nullable = false)
    private int pollCount;
    @Column(name = "bw", nullable = false)
    private byte[] bw;
    @Column(name = "red", nullable = false)
    private byte[] red;

    protected HcbgEinkOp() {
    }

    public HcbgEinkOp(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            UUID gatewayId,
            UUID jobId,
            String beaconMac,
            String passkey,
            byte[] bw,
            byte[] red
    ) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.connectionId = connectionId;
        this.gatewayId = gatewayId;
        this.jobId = jobId;
        this.beaconMac = beaconMac;
        this.passkey = passkey;
        this.bw = bw;
        this.red = red;
        this.status = STATUS_PENDING;
        this.phase = PHASE_CONNECTING;
    }

    public void markHandles(Integer sessionHandle, Integer dataHandle, Integer unlockHandle) {
        this.sessionHandle = sessionHandle;
        this.dataHandle = dataHandle;
        this.unlockHandle = unlockHandle;
        this.status = STATUS_RUNNING;
    }

    public void markPhase(String nextPhase) {
        this.phase = nextPhase;
        this.pollCount = 0;
    }

    public void advanceOffset(int bytes) {
        this.sendOffset += Math.max(0, bytes);
    }

    public void resetPlane(int plane) {
        this.plane = (short) plane;
        this.sendOffset = 0;
    }

    public void incrementPoll() {
        this.pollCount++;
    }

    public void markCompleted() {
        this.status = STATUS_COMPLETED;
        this.phase = PHASE_DONE;
    }

    public void markFailed() {
        this.status = STATUS_FAILED;
    }

    public boolean isOpen() {
        return STATUS_PENDING.equals(status) || STATUS_RUNNING.equals(status);
    }

    public byte[] planeBytes() {
        return plane == 1 ? red : bw;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getConnectionId() { return connectionId; }
    public UUID getGatewayId() { return gatewayId; }
    public UUID getJobId() { return jobId; }
    public String getBeaconMac() { return beaconMac; }
    public String getPasskey() { return passkey; }
    public String getStatus() { return status; }
    public String getPhase() { return phase; }
    public Integer getSessionHandle() { return sessionHandle; }
    public Integer getDataHandle() { return dataHandle; }
    public Integer getUnlockHandle() { return unlockHandle; }
    public int getPlane() { return plane; }
    public int getSendOffset() { return sendOffset; }
    public int getPollCount() { return pollCount; }
    public byte[] getBw() { return bw; }
    public byte[] getRed() { return red; }
}
