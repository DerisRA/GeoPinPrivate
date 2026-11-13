package dev.geopin.app.ui;

import android.content.Context;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public final class TileStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final List<FakeConnection> CONNECTIONS = new CopyOnWriteArrayList<>();
    private static CountDownLatch blocked;
    private static boolean holdOldZoom;
    private static boolean oversized;

    @BeforeClass public static void interceptNetwork() {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                FakeConnection connection = new FakeConnection(url);
                CONNECTIONS.add(connection);
                return connection;
            }
        } : null);
    }

    @Before public void resetNetwork() {
        CONNECTIONS.clear();
        blocked = new CountDownLatch(2);
        holdOldZoom = false;
        oversized = false;
    }

    @Test public void changedViewportCancelsOldTransfersAndSkipsQueuedTiles() throws Exception {
        holdOldZoom = true;
        TileStore store = new TileStore(new Context(temporary.getRoot()));
        AtomicInteger staleCallbacks = new AtomicInteger();
        try {
            store.setViewport(4, 1, 8, 1, 1);
            for (int x = 1; x <= 8; x++) store.request(4, x, 1, staleCallbacks::incrementAndGet);
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            store.setViewport(5, 20, 20, 2, 2);
            CountDownLatch ready = new CountDownLatch(1);
            store.request(5, 20, 2, ready::countDown);
            assertTrue("Current tiles wait behind obsolete downloads", ready.await(2, TimeUnit.SECONDS));
            assertEquals(0, staleCallbacks.get());
            assertEquals(2, CONNECTIONS.stream().filter(c -> c.urlPath.startsWith("/4/")).count());
            assertNotNull(store.peek(5, 20, 2));
        } finally { store.shutdown(); }
    }

    @Test public void revisitUsesDiskWithoutDownloadingAgain() throws Exception {
        Context context = new Context(temporary.getRoot());
        for (int visit = 0; visit < 2; visit++) {
            TileStore store = new TileStore(context);
            try {
                store.setViewport(8, 1, 1, 2, 2);
                CountDownLatch ready = new CountDownLatch(1);
                store.request(8, 1, 2, ready::countDown);
                assertTrue(ready.await(2, TimeUnit.SECONDS));
                assertNotNull(store.peek(8, 1, 2));
            } finally { store.shutdown(); }
        }
        assertEquals(1, CONNECTIONS.size());
    }

    @Test public void rejectsLargeResponseBeforeReadingItsBody() throws Exception {
        oversized = true;
        TileStore store = new TileStore(new Context(temporary.getRoot()));
        try {
            store.setViewport(8, 1, 1, 2, 2);
            store.request(8, 1, 2, () -> { });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((CONNECTIONS.isEmpty() || !CONNECTIONS.get(0).closed)
                    && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(1, CONNECTIONS.size());
            assertFalse("Oversized tile body was downloaded", CONNECTIONS.get(0).bodyRead);
            assertNull(store.peek(8, 1, 2));
        } finally { store.shutdown(); }
    }

    @Test public void diskCacheIsBoundedAfterWritingNewTile() throws Exception {
        File old = new File(temporary.getRoot(), "osm/7/1");
        assertTrue(old.mkdirs());
        for (int i = 0; i < 30; i++) {
            File file = new File(old, i + ".png");
            Files.write(file.toPath(), new byte[512 * 1024]);
            assertTrue(file.setLastModified(1000L + i));
        }
        TileStore store = new TileStore(new Context(temporary.getRoot()));
        try {
            store.setViewport(8, 1, 1, 2, 2);
            CountDownLatch ready = new CountDownLatch(1);
            store.request(8, 1, 2, ready::countDown);
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            long bytes;
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(temporary.getRoot().toPath())) {
                bytes = paths.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
            }
            assertTrue("Disk cache exceeded 12 MiB: " + bytes, bytes <= 12L * 1024 * 1024);
            assertFalse(new File(old, "0.png").exists());
        } finally { store.shutdown(); }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final String urlPath;
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean closed;
        volatile boolean bodyRead;
        FakeConnection(URL url) { super(url); urlPath = url.getPath(); }
        @Override public void connect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { closed = true; release.countDown(); }
        @Override public int getResponseCode() throws IOException {
            if (holdOldZoom && urlPath.startsWith("/4/")) {
                blocked.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                if (closed) throw new IOException("cancelled");
            }
            return HTTP_OK;
        }
        @Override public int getContentLength() { return oversized ? 300 * 1024 : 16; }
        @Override public InputStream getInputStream() {
            bodyRead = true;
            return new ByteArrayInputStream(new byte[getContentLength()]);
        }
    }
}
