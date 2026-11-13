package dev.geopin.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cache ubin OpenStreetMap: memori, disk, lalu jaringan. Semua pembacaan disk dan dekode bitmap
 * terjadi di thread pekerja, jadi {@link #peek(int, int, int)} hanya menyentuh memori.
 */
final class TileStore {
    private static final String TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "GeoPinPrivate/0.1 (Android; stationary-pin controller)";
    private static final int MEMORY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TILE_BYTES = 256 * 1024;
    private static final long DISK_BYTES = 12L * 1024 * 1024;

    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(MEMORY_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final ConcurrentHashMap<String, Load> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> retryAfter = new ConcurrentHashMap<>();
    private volatile Set<String> visible = Collections.emptySet();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(24));
    private final ExecutorService cancellations = Executors.newSingleThreadExecutor();
    private final File root;

    TileStore(Context context) {
        this.root = new File(context.getCacheDir(), "osm");
    }

    void setViewport(int zoom, int firstX, int lastX, int firstY, int lastY) {
        Set<String> next = new HashSet<>();
        int count = 1 << zoom;
        for (int x = firstX; x <= lastX; x++) {
            for (int y = Math.max(0, firstY); y <= Math.min(count - 1, lastY); y++) {
                next.add(key(zoom, ((x % count) + count) % count, y));
            }
        }
        if (next.equals(visible)) return;
        visible = next;
        retryAfter.keySet().retainAll(next);
        for (Load load : inFlight.values()) {
            if (!next.contains(load.key)) {
                load.cancelled = true;
                executor.remove(load);
                inFlight.remove(load.key, load);
                final HttpURLConnection connection = load.connection;
                if (connection != null) cancellations.execute(new Runnable() {
                    @Override public void run() { connection.disconnect(); }
                });
            }
        }
    }

    /** Hanya cache memori; aman dipanggil dari {@code onDraw}. */
    Bitmap peek(int zoom, int x, int y) {
        return memory.get(key(zoom, x, y));
    }

    /** Memuat ubin dari disk atau jaringan di thread pekerja, lalu memanggil {@code onReady}. */
    void request(final int zoom, final int x, final int y, final Runnable onReady) {
        final String key = key(zoom, x, y);
        Long retry = retryAfter.get(key);
        if (!visible.contains(key) || memory.get(key) != null || executor.isShutdown()
                || (retry != null && System.nanoTime() < retry)) return;
        Load load = new Load(zoom, x, y, onReady);
        if (inFlight.putIfAbsent(key, load) != null) return;
        try {
            executor.execute(load);
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            inFlight.remove(key, load);
        }
    }

    void shutdown() {
        setViewport(0, 0, -1, 0, -1);
        executor.shutdownNow();
        cancellations.shutdown();
    }

    private final class Load implements Runnable {
        final int zoom;
        final int x;
        final int y;
        final String key;
        final Runnable onReady;
        volatile boolean cancelled;
        volatile HttpURLConnection connection;

        Load(int zoom, int x, int y, Runnable onReady) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
            this.key = key(zoom, x, y);
            this.onReady = onReady;
        }

        boolean needed() { return !cancelled && visible.contains(key); }

        @Override public void run() {
            boolean ready = false;
            try {
                if (needed()) ready = loadFromDisk(zoom, x, y) || fetchAndCache(this);
                if (ready) trimDisk();
                else if (needed()) retryAfter.put(key, System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
            } finally {
                inFlight.remove(key, this);
            }
            if (ready && needed()) onReady.run();
        }
    }

    private boolean loadFromDisk(int zoom, int x, int y) {
        File file = file(zoom, x, y);
        if (!file.isFile()) {
            return false;
        }
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (decoded == null) {
            // berkas rusak: buang supaya tidak diunduh ulang setiap frame
            file.delete();
            return false;
        }
        memory.put(key(zoom, x, y), decoded);
        return true;
    }

    private boolean fetchAndCache(Load load) {
        HttpURLConnection connection = null;
        try {
            if (!load.needed()) return false;
            URL url = new URL(String.format(java.util.Locale.ROOT, TILE_URL, load.zoom, load.x, load.y));
            connection = (HttpURLConnection) url.openConnection();
            load.connection = connection;
            if (!load.needed()) return false;
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || !load.needed() || connection.getContentLength() > MAX_TILE_BYTES) {
                return false;
            }
            byte[] data = readAll(connection.getInputStream(), load);
            if (data == null) {
                return false;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) {
                return false;
            }
            if (!load.needed()) return false;
            writeFile(load.zoom, load.x, load.y, data);
            memory.put(load.key, bitmap);
            return true;
        } catch (IOException unavailable) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream input, Load load) throws IOException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(16 * 1024);
            byte[] chunk = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) > 0) {
                total += read;
                if (!load.needed() || total > MAX_TILE_BYTES) {
                    return null;
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } finally {
            input.close();
        }
    }

    private void writeFile(int zoom, int x, int y, byte[] data) {
        File target = file(zoom, x, y);
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return;
        }
        File temporary = new File(parent, target.getName() + ".part");
        try {
            FileOutputStream output = new FileOutputStream(temporary);
            try {
                output.write(data);
            } finally {
                output.close();
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
            }
        } catch (IOException unwritable) {
            temporary.delete();
        }
    }

    private File file(int zoom, int x, int y) {
        return new File(new File(new File(root, String.valueOf(zoom)), String.valueOf(x)), y + ".png");
    }

    private synchronized void trimDisk() {
        List<File> files = new ArrayList<>();
        collectTiles(root, files);
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= DISK_BYTES) return;
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return Long.compare(left.lastModified(), right.lastModified());
            }
        });
        for (File file : files) {
            long length = file.length();
            if (file.delete()) total -= length;
            if (total <= DISK_BYTES) break;
        }
    }

    private static void collectTiles(File directory, List<File> tiles) {
        File[] entries = directory.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) collectTiles(entry, tiles);
            else if (entry.getName().endsWith(".png")) tiles.add(entry);
        }
    }

    private static String key(int zoom, int x, int y) {
        return zoom + "/" + x + "/" + y;
    }
}
