package dev.geopin.core;

import java.util.Locale;

/** Satu sampel lokasi immutable. Satu objek dipakai bersama oleh semua getter di hook. */
public final class LocationSample {
    public final double latitude;
    public final double longitude;
    public final double accuracyMeters;
    public final double altitudeMeters;
    public final double speedMetersPerSecond;
    public final double bearingDegrees;
    public final long wallClockMillis;
    public final long elapsedRealtimeNanos;

    public LocationSample(
            double latitude,
            double longitude,
            double accuracyMeters,
            double altitudeMeters,
            double speedMetersPerSecond,
            double bearingDegrees,
            long wallClockMillis,
            long elapsedRealtimeNanos) {
        if (latitude < Coordinate.MIN_LATITUDE || latitude > Coordinate.MAX_LATITUDE) {
            throw new IllegalArgumentException("Latitude sampel di luar rentang: " + latitude);
        }
        if (longitude < Coordinate.MIN_LONGITUDE || longitude > Coordinate.MAX_LONGITUDE) {
            throw new IllegalArgumentException("Longitude sampel di luar rentang: " + longitude);
        }
        if (!(accuracyMeters > 0.0)) {
            throw new IllegalArgumentException("Akurasi harus positif: " + accuracyMeters);
        }
        if (speedMetersPerSecond < 0.0) {
            throw new IllegalArgumentException("Kecepatan tidak boleh negatif: " + speedMetersPerSecond);
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.altitudeMeters = altitudeMeters;
        this.speedMetersPerSecond = speedMetersPerSecond;
        this.bearingDegrees = PinConfig.normalizeBearing(bearingDegrees);
        this.wallClockMillis = wallClockMillis;
        this.elapsedRealtimeNanos = elapsedRealtimeNanos;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "LocationSample[%.6f,%.6f acc=%.2fm alt=%.2fm speed=%.2f bearing=%.1f]",
                latitude,
                longitude,
                accuracyMeters,
                altitudeMeters,
                speedMetersPerSecond,
                bearingDegrees);
    }
}
