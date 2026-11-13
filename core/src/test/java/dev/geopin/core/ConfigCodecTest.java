package dev.geopin.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public final class ConfigCodecTest {
    @Test
    public void roundTripsEveryField() {
        PinConfig original = new PinConfig(new Coordinate(-6.175392, 106.827153), 45.2, 137.5, true, "Kantor \u2014 lantai 3");
        PinConfig decoded = ConfigCodec.decode(ConfigCodec.encode(original));

        assertEquals(original.center(), decoded.center());
        assertEquals(45.2, decoded.manualAltitudeMeters(), 0.0);
        assertEquals(137.5, decoded.bearingDegrees(), 0.0);
        assertEquals(true, decoded.enabled());
        assertEquals("Kantor \u2014 lantai 3", decoded.label());
    }

    @Test
    public void roundTripsLabelWithNewlinesAndBackslashes() {
        PinConfig original = PinConfig.centeredAt(new Coordinate(1.5, 2.5)).withLabel("baris1\nbaris2\\akhir");
        PinConfig decoded = ConfigCodec.decode(ConfigCodec.encode(original));
        assertEquals("baris1\nbaris2\\akhir", decoded.label());
    }

    @Test
    public void autoAltitudeStaysNullAfterRoundTrip() {
        PinConfig original = PinConfig.centeredAt(new Coordinate(-6.2, 106.8));
        PinConfig decoded = ConfigCodec.decode(ConfigCodec.encode(original));
        assertNull(decoded.manualAltitudeMeters());
        assertEquals(original.resolvedAltitudeMeters(), decoded.resolvedAltitudeMeters(), 0.0);
    }

    @Test
    public void disabledStateSurvivesRoundTrip() {
        PinConfig disabled = PinConfig.centeredAt(new Coordinate(0.0, 0.0)).withEnabled(false);
        assertEquals(false, ConfigCodec.decode(ConfigCodec.encode(disabled)).enabled());
    }

    @Test
    public void rejectsUnknownHeaderAndMissingCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("geopin-pin/2\nlat=1\nlon=2"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("geopin-pin/1\nlat=1.0\n"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode("geopin-pin/1\nlat=bukan angka\nlon=2"));
    }

    @Test
    public void ignoresUnknownKeysForForwardCompatibility() {
        String text = "geopin-pin/1\nlat=-6.2\nlon=106.8\nfuture=whatever\n";
        PinConfig decoded = ConfigCodec.decode(text);
        assertEquals(-6.2, decoded.center().latitude, 0.0);
        assertEquals(106.8, decoded.center().longitude, 0.0);
    }
}
