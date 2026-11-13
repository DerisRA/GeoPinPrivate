package dev.geopin.core;

/** Konfigurasi titik diam: pusat, ketinggian opsional, arah hadap, dan label favorit. */
public final class PinConfig {
    /** Ketinggian dasar bila pengguna tidak mengisi; dipakai agar tidak pernah 0.0 m. */
    public static final double AUTO_ALTITUDE_BASE = 45.2;

    private final Coordinate center;
    private final Double altitudeMeters;
    private final double bearingDegrees;
    private final boolean enabled;
    private final String label;

    public PinConfig(Coordinate center, Double altitudeMeters, double bearingDegrees, boolean enabled, String label) {
        if (center == null) {
            throw new IllegalArgumentException("Titik pusat wajib diisi");
        }
        if (altitudeMeters != null && (Double.isNaN(altitudeMeters) || Double.isInfinite(altitudeMeters))) {
            throw new IllegalArgumentException("Ketinggian harus angka berhingga");
        }
        this.center = center;
        this.altitudeMeters = altitudeMeters;
        this.bearingDegrees = normalizeBearing(bearingDegrees);
        this.enabled = enabled;
        this.label = label == null ? "" : label;
    }

    public static PinConfig centeredAt(Coordinate center) {
        return new PinConfig(center, null, 0.0, true, "");
    }

    public Coordinate center() {
        return center;
    }

    /** Ketinggian manual, atau {@code null} bila modul harus menurunkannya sendiri. */
    public Double manualAltitudeMeters() {
        return altitudeMeters;
    }

    /** Ketinggian yang benar-benar dipakai, selalu berhingga dan tidak pernah 0.0 m. */
    public double resolvedAltitudeMeters() {
        if (altitudeMeters != null) {
            return altitudeMeters;
        }
        double seed = center.latitude * 7.3 + center.longitude * 13.7;
        double span = Math.abs(seed) % 400.0;
        return AUTO_ALTITUDE_BASE + span;
    }

    public double bearingDegrees() {
        return bearingDegrees;
    }

    public boolean enabled() {
        return enabled;
    }

    public String label() {
        return label;
    }

    public PinConfig withEnabled(boolean newEnabled) {
        return new PinConfig(center, altitudeMeters, bearingDegrees, newEnabled, label);
    }

    public PinConfig withLabel(String newLabel) {
        return new PinConfig(center, altitudeMeters, bearingDegrees, enabled, newLabel);
    }

    public PinConfig withAltitude(Double newAltitude) {
        return new PinConfig(center, newAltitude, bearingDegrees, enabled, label);
    }

    public PinConfig withBearing(double newBearing) {
        return new PinConfig(center, altitudeMeters, newBearing, enabled, label);
    }

    public static double normalizeBearing(double degrees) {
        if (Double.isNaN(degrees) || Double.isInfinite(degrees)) {
            throw new IllegalArgumentException("Bearing harus angka berhingga: " + degrees);
        }
        double value = degrees % 360.0;
        if (value < 0.0) {
            value += 360.0;
        }
        return value;
    }
}
