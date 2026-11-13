package dev.geopin.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class StationaryEngineTest {
    private static final Coordinate JAKARTA = new Coordinate(-6.175392, 106.827153);

    private static final class FixedClock implements TimeSource {
        private long wall;
        private long nanos;

        FixedClock(long wall, long nanos) {
            this.wall = wall;
            this.nanos = nanos;
        }

        @Override
        public long wallClockMillis() {
            return wall;
        }

        @Override
        public long elapsedRealtimeNanos() {
            return nanos;
        }
    }

    @Test
    public void keepsEverySampleInsideDriftAndAccuracyBounds() {
        StationaryEngine engine = new StationaryEngine(new Random(42L), new FixedClock(1L, 2L));
        PinConfig config = PinConfig.centeredAt(JAKARTA);
        double widestDrift = 0.0;
        for (int index = 0; index < 500; index++) {
            LocationSample sample = engine.next(config);
            double drift = metersBetween(JAKARTA.latitude, JAKARTA.longitude, sample.latitude, sample.longitude);
            widestDrift = Math.max(widestDrift, drift);
            assertTrue("drift " + drift, drift <= StationaryEngine.MAX_DRIFT_METERS + 0.02);
            assertTrue(sample.accuracyMeters >= StationaryEngine.MIN_ACCURACY_METERS - 1e-9);
            assertTrue(sample.accuracyMeters <= StationaryEngine.MAX_ACCURACY_METERS + 1e-9);
            assertEquals(0.0, sample.speedMetersPerSecond, 0.0);
            assertTrue(sample.bearingDegrees >= 0.0 && sample.bearingDegrees < 360.0);
        }
        assertTrue("jitter harus benar-benar bergerak, drift terbesar=" + widestDrift, widestDrift > 0.1);
    }

    @Test
    public void altitudeFluctuatesAroundConfiguredValue() {
        StationaryEngine engine = new StationaryEngine(new Random(7L), new FixedClock(0L, 0L));
        PinConfig config = PinConfig.centeredAt(JAKARTA).withAltitude(45.2);
        for (int index = 0; index < 200; index++) {
            LocationSample sample = engine.next(config);
            assertTrue(Math.abs(sample.altitudeMeters - 45.2) <= StationaryEngine.ALTITUDE_FLUCTUATION_METERS + 1e-9);
        }
    }

    @Test
    public void autoAltitudeIsStableAndNeverZero() {
        PinConfig config = PinConfig.centeredAt(JAKARTA);
        double first = config.resolvedAltitudeMeters();
        double second = PinConfig.centeredAt(JAKARTA).resolvedAltitudeMeters();
        assertEquals(first, second, 0.0);
        assertTrue("ketinggian otomatis tidak boleh 0", first > 0.0);
    }

    @Test
    public void timestampsComeFromInjectedClock() {
        FixedClock clock = new FixedClock(1_700_000_000_000L, 987_654_321L);
        StationaryEngine engine = new StationaryEngine(new Random(3L), clock);
        LocationSample sample = engine.next(PinConfig.centeredAt(JAKARTA));
        assertEquals(1_700_000_000_000L, sample.wallClockMillis);
        assertEquals(987_654_321L, sample.elapsedRealtimeNanos);

        clock.wall = 1_700_000_005_000L;
        clock.nanos = 5_000_000_000L;
        LocationSample later = engine.next(PinConfig.centeredAt(JAKARTA));
        assertEquals(1_700_000_005_000L, later.wallClockMillis);
        assertEquals(5_000_000_000L, later.elapsedRealtimeNanos);
    }

    @Test
    public void sameSeedIsReproducible() {
        PinConfig config = PinConfig.centeredAt(JAKARTA);
        LocationSample first = new StationaryEngine(new Random(11L), new FixedClock(5L, 6L)).next(config);
        LocationSample second = new StationaryEngine(new Random(11L), new FixedClock(5L, 6L)).next(config);
        assertEquals(first.latitude, second.latitude, 0.0);
        assertEquals(first.longitude, second.longitude, 0.0);
        assertEquals(first.accuracyMeters, second.accuracyMeters, 0.0);
    }

    @Test
    public void bearingDriftsAroundConfiguredBearing() {
        StationaryEngine engine = new StationaryEngine(new Random(19L), new FixedClock(0L, 0L));
        PinConfig config = PinConfig.centeredAt(JAKARTA).withBearing(90.0);
        for (int index = 0; index < 100; index++) {
            double bearing = engine.next(config).bearingDegrees;
            double delta = Math.abs(((bearing - 90.0) + 540.0) % 360.0 - 180.0);
            assertTrue("simpangan bearing " + delta, delta <= StationaryEngine.MAX_BEARING_DRIFT_DEGREES + 1e-9);
        }
    }

    private static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371008.8;
        double deltaLatitude = Math.toRadians(lat2 - lat1);
        double deltaLongitude = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return 2 * earthRadius * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
