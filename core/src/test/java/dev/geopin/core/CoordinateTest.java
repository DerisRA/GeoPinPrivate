package dev.geopin.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public final class CoordinateTest {
    @Test
    public void parsesPastedLatitudeLongitude() {
        Coordinate point = Coordinate.parse("  -6.175392, 106.827153  ");
        assertEquals(-6.175392, point.latitude, 0.0);
        assertEquals(106.827153, point.longitude, 0.0);
    }

    @Test
    public void acceptsWhitespaceSemicolonAndDegreeSign() {
        Coordinate point = Coordinate.parse("-6.175392;106.827153");
        assertEquals(-6.175392, point.latitude, 0.0);
        assertEquals(106.827153, point.longitude, 0.0);

        Coordinate spaced = Coordinate.parse("-6.2\u00B0 106.8\u00B0");
        assertEquals(-6.2, spaced.latitude, 0.0);
        assertEquals(106.8, spaced.longitude, 0.0);
    }

    @Test
    public void acceptsCompassSuffix() {
        Coordinate point = Coordinate.parse("6.175392S 106.827153E");
        assertEquals(-6.175392, point.latitude, 0.0);
        assertEquals(106.827153, point.longitude, 0.0);

        Coordinate western = Coordinate.parse("6.175392N 106.827153W");
        assertEquals(6.175392, western.latitude, 0.0);
        assertEquals(-106.827153, western.longitude, 0.0);
    }

    @Test
    public void rejectsUnknownAndWrongAxisSuffixes() {
        for (String text : new String[] {
                "10Q, 20", "10, 20Q", "10N, 20S", "10E, 20N", "10W, 20", "10, 20N"
        }) {
            assertThrows(text, IllegalArgumentException.class, () -> Coordinate.parse(text));
        }
    }

    @Test
    public void acceptsLowercaseDirectionsAndScientificNotation() {
        assertEquals(new Coordinate(-10, -20), Coordinate.parse("10s, 20w"));
        assertEquals(new Coordinate(10, 20), Coordinate.parse("1e1, 2e1"));
    }

    @Test
    public void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0.0, 181.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse("91.0, 0.0"));
    }

    @Test
    public void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(null));
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse("hanya teks"));
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse("1.0, 2.0, 3.0"));
    }

    @Test
    public void formatRoundTripsThroughParse() {
        Coordinate sixDecimals = new Coordinate(-6.175392, 106.827153);
        assertEquals(sixDecimals, Coordinate.parse(sixDecimals.format()));

        // format() sengaja membulatkan ke enam desimal (~0.1 m), bukan menyimpan bit ganda
        Coordinate precise = new Coordinate(-6.1753924, 106.8271528);
        Coordinate parsed = Coordinate.parse(precise.format());
        assertEquals(precise.latitude, parsed.latitude, 1e-6);
        assertEquals(precise.longitude, parsed.longitude, 1e-6);
    }

    @Test
    public void equalityUsesBothComponents() {
        Coordinate first = new Coordinate(-6.2, 106.8);
        assertEquals(first, new Coordinate(-6.2, 106.8));
        assertEquals(first.hashCode(), new Coordinate(-6.2, 106.8).hashCode());
        assertNotEquals(first, new Coordinate(-6.200001, 106.8));
    }
}
