package dev.geopin.core;

import java.util.Objects;
import java.util.Random;

/**
 * Menghasilkan sampel titik diam dengan micro-jitter yang menyerupai GPS diam.
 * Semua parameter acak berasal dari {@link Random} yang di-inject agar dapat diuji.
 */
public final class StationaryEngine {
    /** Jarak maksimum pergeseran dari titik pusat, sesuai PRD (0.3-0.9 m). */
    public static final double MAX_DRIFT_METERS = 0.9;
    public static final double MIN_ACCURACY_METERS = 3.5;
    public static final double MAX_ACCURACY_METERS = 7.8;
    public static final double ALTITUDE_FLUCTUATION_METERS = 0.3;
    public static final double MAX_BEARING_DRIFT_DEGREES = 1.5;

    private static final double METERS_PER_DEGREE_LATITUDE = 111320.0;
    private static final double TAU = Math.PI * 2.0;

    private final Random random;
    private final TimeSource timeSource;

    public StationaryEngine(Random random, TimeSource timeSource) {
        this.random = Objects.requireNonNull(random, "random");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    /** Satu sampel baru untuk pemanggilan lokasi berikutnya. */
    public LocationSample next(PinConfig config) {
        Objects.requireNonNull(config, "config");
        Coordinate center = config.center();

        double radius = random.nextDouble() * MAX_DRIFT_METERS;
        double angle = random.nextDouble() * TAU;
        double northMeters = radius * Math.cos(angle);
        double eastMeters = radius * Math.sin(angle);

        double latitude = center.latitude + northMeters / METERS_PER_DEGREE_LATITUDE;
        latitude = clamp(latitude, Coordinate.MIN_LATITUDE, Coordinate.MAX_LATITUDE);
        double metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude));
        if (metersPerDegreeLongitude < 1.0) {
            metersPerDegreeLongitude = 1.0;
        }
        double longitude = wrapLongitude(center.longitude + eastMeters / metersPerDegreeLongitude);

        double accuracy = MIN_ACCURACY_METERS + random.nextDouble() * (MAX_ACCURACY_METERS - MIN_ACCURACY_METERS);
        double altitude = config.resolvedAltitudeMeters() + signedUnit() * ALTITUDE_FLUCTUATION_METERS;
        double bearing = PinConfig.normalizeBearing(config.bearingDegrees() + signedUnit() * MAX_BEARING_DRIFT_DEGREES);

        return new LocationSample(
                latitude,
                longitude,
                accuracy,
                altitude,
                0.0,
                bearing,
                timeSource.wallClockMillis(),
                timeSource.elapsedRealtimeNanos());
    }

    private double signedUnit() {
        return random.nextDouble() * 2.0 - 1.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }

    private static double wrapLongitude(double longitude) {
        double value = longitude;
        while (value > Coordinate.MAX_LONGITUDE) {
            value -= 360.0;
        }
        while (value < Coordinate.MIN_LONGITUDE) {
            value += 360.0;
        }
        return value;
    }
}
