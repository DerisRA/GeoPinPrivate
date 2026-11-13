package dev.geopin.app.hook;

import android.os.SystemClock;

import java.util.Random;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import dev.geopin.app.BuildConfig;
import dev.geopin.app.store.PinStore;
import dev.geopin.core.ConfigCodec;
import dev.geopin.core.LocationSample;
import dev.geopin.core.PinConfig;
import dev.geopin.core.ScopePolicy;
import dev.geopin.core.StationaryEngine;
import dev.geopin.core.TimeSource;

/**
 * Status runtime di dalam proses aplikasi target: konfigurasi terakhir, allowlist, dan mesin jitter.
 * Konfigurasi dan allowlist dipublikasikan sebagai satu {@link Snapshot} immutable supaya pembaca
 * tidak pernah melihat kombinasi lama/baru, dan pembacaan berkas dilakukan di bawah satu kunci
 * karena {@link XSharedPreferences} tidak menjamin thread-safety.
 */
final class PinRuntime {
    private static final long RELOAD_INTERVAL_MS = 3000L;

    private static final TimeSource ANDROID_CLOCK = new TimeSource() {
        @Override
        public long wallClockMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long elapsedRealtimeNanos() {
            return SystemClock.elapsedRealtimeNanos();
        }
    };

    private final String packageName;
    private final StationaryEngine engine;
    private final XSharedPreferences preferences;
    private final Object reloadLock = new Object();

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile long lastReloadMillis;
    private boolean reportedMissingConfig;

    private PinRuntime(String packageName) throws Throwable {
        this.packageName = packageName;
        this.engine = new StationaryEngine(new Random(), ANDROID_CLOCK);
        this.preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID, PinStore.PREF_FILE);
        reload();
        lastReloadMillis = SystemClock.elapsedRealtime();
    }

    static PinRuntime create(String packageName) {
        try {
            return new PinRuntime(packageName);
        } catch (Throwable error) {
            XposedBridge.log("[GeoPin] preferensi modul tidak terbaca: " + error);
            return null;
        }
    }

    /** Sampel baru untuk satu objek lokasi, atau {@code null} bila paket ini tidak disentuh. */
    LocationSample createSample() {
        refreshIfStale();
        Snapshot current = snapshot;
        if (!current.isActiveFor(packageName)) {
            return null;
        }
        synchronized (engine) {
            return engine.next(current.config);
        }
    }

    /** Apakah paket ini sedang menerima titik diam, tanpa membuat sampel baru. */
    boolean isActive() {
        refreshIfStale();
        return snapshot.isActiveFor(packageName);
    }

    void refreshIfStale() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastReloadMillis < RELOAD_INTERVAL_MS) {
            return;
        }
        synchronized (reloadLock) {
            if (now - lastReloadMillis < RELOAD_INTERVAL_MS) {
                return;
            }
            lastReloadMillis = now;
            try {
                reload();
            } catch (Throwable error) {
                XposedBridge.log("[GeoPin] gagal menyegarkan preferensi: " + error);
            }
        }
    }

    private void reload() {
        preferences.reload();
        String encoded = preferences.getString(PinStore.KEY_CONFIG, null);
        String allowlist = preferences.getString(PinStore.KEY_ALLOWLIST, "");
        ScopePolicy scope = new ScopePolicy(BuildConfig.APPLICATION_ID, ScopePolicy.parseList(allowlist));
        snapshot = new Snapshot(decode(encoded), scope);
    }

    private PinConfig decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            if (!reportedMissingConfig) {
                reportedMissingConfig = true;
                XposedBridge.log("[GeoPin] belum ada konfigurasi pin di "
                        + BuildConfig.APPLICATION_ID + "; modul menunggu");
            }
            return null;
        }
        try {
            return ConfigCodec.decode(encoded);
        } catch (IllegalArgumentException broken) {
            XposedBridge.log("[GeoPin] konfigurasi pin rusak: " + broken.getMessage());
            return null;
        }
    }

    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(null, new ScopePolicy("", null));

        final PinConfig config;
        final ScopePolicy scope;

        Snapshot(PinConfig config, ScopePolicy scope) {
            this.config = config;
            this.scope = scope;
        }

        boolean isActiveFor(String packageName) {
            return config != null && config.enabled() && scope.isInjected(packageName);
        }
    }
}
