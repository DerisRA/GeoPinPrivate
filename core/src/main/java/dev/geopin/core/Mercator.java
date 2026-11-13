package dev.geopin.core;

/**
 * Konversi Web Mercator untuk peta ubin. Sumbu X dan Y memakai piksel dunia
 * (0..worldSize); worldSize sendiri ditentukan pemanggil dari level zoom.
 */
public final class Mercator {
    /** Batas lintang Web Mercator. Di luar nilai ini rumus proyeksi menuju tak hingga. */
    public static final double MAX_LATITUDE = 85.05112878;

    private Mercator() {
    }

    public static int tileZoom(double zoom) {
        return Math.max(0, (int) Math.floor(zoom) - 1);
    }

    public static double tileScreenSize(double zoom) {
        return 256.0 * Math.pow(2.0, zoom - tileZoom(zoom));
    }

    public static double longitudeToX(double longitude, double worldSize) {
        return (longitude + 180.0) / 360.0 * worldSize;
    }

    /** y = 0 di kutub utara dan bertambah ke selatan. */
    public static double latitudeToY(double latitude, double worldSize) {
        double radians = Math.toRadians(latitude);
        double mercator = Math.log(Math.tan(radians) + 1.0 / Math.cos(radians));
        return (1.0 - mercator / Math.PI) / 2.0 * worldSize;
    }

    public static double xToLongitude(double x, double worldSize) {
        return x / worldSize * 360.0 - 180.0;
    }

    public static double yToLatitude(double y, double worldSize) {
        double mercator = Math.PI - 2.0 * Math.PI * y / worldSize;
        return Math.toDegrees(Math.atan(Math.sinh(mercator)));
    }

    /** Menjepit lintang ke batas Mercator agar hasil proyeksi tetap berhingga. */
    public static double clampLatitude(double latitude) {
        return Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, latitude));
    }

    /** Membungkus bujur ke rentang -180..180 supaya peta bisa digeser melewati garis tanggal. */
    public static double normalizeLongitude(double longitude) {
        double value = longitude % 360.0;
        if (value > 180.0) {
            value -= 360.0;
        } else if (value < -180.0) {
            value += 360.0;
        }
        return value;
    }

    public static double clampZoom(double zoom, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, zoom));
    }
}
