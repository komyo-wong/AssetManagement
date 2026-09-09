package com.assetmanagement.geotag.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "geotag_track_points")
public class GeotagTrackPoint extends BaseEntity {

    @Column(name = "sn", nullable = false, length = 160)
    private String sn;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "battery")
    private Long battery;

    @Column(name = "battery_status", length = 40)
    private String batteryStatus;

    @Column(name = "accuracy", length = 40)
    private String accuracy;

    @Column(name = "confidence", length = 40)
    private String confidence;

    @Column(name = "reported_time")
    private Instant reportedTime;

    @Column(name = "location_time", nullable = false)
    private Instant locationTime;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected GeotagTrackPoint() {
    }

    public GeotagTrackPoint(
            String sn,
            double lat,
            double lng,
            String address,
            Long battery,
            String batteryStatus,
            String accuracy,
            String confidence,
            Instant reportedTime,
            Instant locationTime,
            Instant receivedAt
    ) {
        this.sn = sn;
        this.lat = lat;
        this.lng = lng;
        this.address = address;
        this.battery = battery;
        this.batteryStatus = batteryStatus;
        this.accuracy = accuracy;
        this.confidence = confidence;
        this.reportedTime = reportedTime;
        this.locationTime = locationTime;
        this.receivedAt = receivedAt == null ? Instant.now() : receivedAt;
    }

    public String getSn() {
        return sn;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Long getBattery() {
        return battery;
    }

    public String getBatteryStatus() {
        return batteryStatus;
    }

    public String getAccuracy() {
        return accuracy;
    }

    public String getConfidence() {
        return confidence;
    }

    public Instant getReportedTime() {
        return reportedTime;
    }

    public Instant getLocationTime() {
        return locationTime;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
