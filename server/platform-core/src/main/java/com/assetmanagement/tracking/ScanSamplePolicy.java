package com.assetmanagement.tracking;

public final class ScanSamplePolicy {

    public static final int WINDOW_SECONDS = 30;
    public static final int RSSI_FORCE_DELTA = 6;

    private ScanSamplePolicy() {
    }

    public static boolean shouldPersist(boolean windowOpen, Integer previousRssi, Integer rssi) {
        if (!windowOpen) {
            return true;
        }
        return jumped(previousRssi, rssi);
    }

    static boolean jumped(Integer previousRssi, Integer rssi) {
        if (previousRssi == null || rssi == null) {
            return false;
        }
        return Math.abs(rssi - previousRssi) >= RSSI_FORCE_DELTA;
    }
}
