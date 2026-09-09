package com.assetmanagement.geotag;

/**
 * GeoTag cloud reports GCJ-02 inside mainland China and WGS-84 elsewhere.
 * OSM / Esri / CARTO / MapTiler tiles are WGS-84, so China pins must be converted.
 */
public final class GeotagCoords {

    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private GeotagCoords() {
    }

    public record Point(double lat, double lng) {
    }

    public static Point toWgs84(double lat, double lng) {
        if (!inMainlandChina(lat, lng)) {
            return new Point(lat, lng);
        }
        Point offset = gcjOffset(lat, lng);
        return new Point(lat * 2 - offset.lat(), lng * 2 - offset.lng());
    }

    public static boolean inMainlandChina(double lat, double lng) {
        if (lng < 73.66 || lng > 135.05 || lat < 17.8 || lat > 53.55) {
            return false;
        }
        if (inTaiwan(lat, lng) || inHongKong(lat, lng) || inMacau(lat, lng)) {
            return false;
        }
        return true;
    }

    private static Point gcjOffset(double lat, double lng) {
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new Point(lat + dLat, lng + dLng);
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    private static boolean inTaiwan(double lat, double lng) {
        return lng >= 119.3 && lng <= 122.15 && lat >= 21.8 && lat <= 25.4;
    }

    private static boolean inHongKong(double lat, double lng) {
        return lng >= 113.75 && lng <= 114.51 && lat >= 22.13 && lat <= 22.58;
    }

    private static boolean inMacau(double lat, double lng) {
        return lng >= 113.52 && lng <= 113.63 && lat >= 22.09 && lat <= 22.22;
    }
}
