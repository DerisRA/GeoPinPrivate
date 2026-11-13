package dev.geopin.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MercatorTest {
    private static final double WORLD_SIZE = 256.0 * (1 << 15);

    @Test
    public void longitudeZeroMapsToWorldCenter() {
        assertEquals(WORLD_SIZE / 2.0, Mercator.longitudeToX(0.0, WORLD_SIZE), 0.0);
        assertEquals(0.0, Mercator.longitudeToX(-180.0, WORLD_SIZE), 0.0);
        assertEquals(WORLD_SIZE, Mercator.longitudeToX(180.0, WORLD_SIZE), 0.0);
    }

    @Test
    public void latitudeZeroMapsToWorldCenter() {
        assertEquals(WORLD_SIZE / 2.0, Mercator.latitudeToY(0.0, WORLD_SIZE), 0.0);
    }

    @Test
    public void longitudeRoundTripsThroughX() {
        double[] longitudes = {-180.0, -170.5, -6.175392, 0.0, 106.827153, 179.999999, 180.0};
        for (double longitude : longitudes) {
            double x = Mercator.longitudeToX(longitude, WORLD_SIZE);
            assertEquals(longitude, Mercator.xToLongitude(x, WORLD_SIZE), 1e-9);
        }
    }

    @Test
    public void latitudeRoundTripsThroughY() {
        double[] latitudes = {
                -Mercator.MAX_LATITUDE, -84.999999, -80.0, -45.5, -6.2,
                0.0, 33.3333, 60.0, 84.999999, Mercator.MAX_LATITUDE
        };
        for (double latitude : latitudes) {
            double y = Mercator.latitudeToY(latitude, WORLD_SIZE);
            assertEquals(latitude, Mercator.yToLatitude(y, WORLD_SIZE), 1e-9);
        }
    }

    @Test
    public void latitudeAboveMaximumIsClamped() {
        assertEquals(Mercator.MAX_LATITUDE, Mercator.clampLatitude(89.9), 0.0);
        assertEquals(-Mercator.MAX_LATITUDE, Mercator.clampLatitude(-89.9), 0.0);
        assertEquals(Mercator.MAX_LATITUDE, Mercator.clampLatitude(Mercator.MAX_LATITUDE), 0.0);
        assertEquals(-6.2, Mercator.clampLatitude(-6.2), 0.0);
    }

    @Test
    public void longitudeWrapsAtDateLine() {
        assertEquals(-170.0, Mercator.normalizeLongitude(190.0), 0.0);
        assertEquals(170.0, Mercator.normalizeLongitude(-190.0), 0.0);
        assertEquals(180.0, Mercator.normalizeLongitude(180.0), 0.0);
        assertEquals(-180.0, Mercator.normalizeLongitude(-180.0), 0.0);
        assertEquals(0.0, Mercator.normalizeLongitude(360.0), 0.0);
        assertEquals(-179.0, Mercator.normalizeLongitude(181.0), 0.0);
        assertEquals(179.0, Mercator.normalizeLongitude(-181.0), 0.0);
    }

    @Test
    public void clampZoomRespectsBounds() {
        assertEquals(19.0, Mercator.clampZoom(25.0, 3.0, 19.0), 0.0);
        assertEquals(3.0, Mercator.clampZoom(1.0, 3.0, 19.0), 0.0);
        assertEquals(10.5, Mercator.clampZoom(10.5, 3.0, 19.0), 0.0);
        assertEquals(4.5, Mercator.clampZoom(9.0, 0.5, 4.5), 0.0);
    }

    @Test
    public void latitudeLimitMapsToWorldEdges() {
        double worldSize = 256.0;
        assertEquals(0.0, Mercator.latitudeToY(Mercator.MAX_LATITUDE, worldSize), 1e-6);
        assertEquals(worldSize, Mercator.latitudeToY(-Mercator.MAX_LATITUDE, worldSize), 1e-6);
    }

    @Test
    public void fractionalZoomKeepsRequestsInTheSameGeographicTile() {
        for (double zoom : new double[]{15.0, 15.25, 15.5, 15.99}) {
            int source = Mercator.tileZoom(zoom);
            double size = Mercator.tileScreenSize(zoom);
            double world = 256.0 * Math.pow(2.0, zoom);
            double sourceWorld = 256.0 * (1 << source);
            assertEquals(Mercator.longitudeToX(96.37967, sourceWorld) / 256.0,
                    Mercator.longitudeToX(96.37967, world) / size, 1e-9);
            assertEquals(Mercator.latitudeToY(2.464849, sourceWorld) / 256.0,
                    Mercator.latitudeToY(2.464849, world) / size, 1e-9);
        }
    }

    @Test
    public void simpleMapUsesFewerTilesAtTheSameDisplayZoom() {
        assertEquals(15, Mercator.tileZoom(16.0));
        assertEquals(512.0, Mercator.tileScreenSize(16.0), 0.0);
        double world = 256.0 * (1 << 16);
        double left = Mercator.longitudeToX(96.37967, world) - 328;
        double top = Mercator.latitudeToY(2.464849, world) - 320;
        int oldTiles = visibleTiles(left, top, 256.0);
        int newTiles = visibleTiles(left, top, Mercator.tileScreenSize(16.0));
        assertTrue(newTiles <= oldTiles / 2);
    }

    private static int visibleTiles(double left, double top, double size) {
        int width = (int) Math.ceil((left + 656) / size) - (int) Math.floor(left / size);
        int height = (int) Math.ceil((top + 640) / size) - (int) Math.floor(top / size);
        return width * height;
    }
}

