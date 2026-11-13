package dev.geopin.app.store;

import android.content.Context;
import android.content.SharedPreferences;

import dev.geopin.core.ConfigCodec;
import dev.geopin.core.PinConfig;

/**
 * Penyimpanan konfigurasi pin. Berkas ini dibaca lintas proses oleh hook; keterbacaannya diurus
 * LSPosed lewat meta-data {@code xposedsharedprefs} dan hook MODE_WORLD_READABLE.
 */
public final class PinStore {
    public static final String PREF_FILE = "geopin_pin";
    public static final String KEY_CONFIG = "config";
    public static final String KEY_ALLOWLIST = "allowlist";

    private final SharedPreferences preferences;

    private PinStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @SuppressWarnings("deprecation")
    public static PinStore open(Context context) {
        try {
            return new PinStore(context.getSharedPreferences(PREF_FILE, Context.MODE_WORLD_READABLE));
        } catch (SecurityException moduleInactive) {
            return new PinStore(context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE));
        }
    }

    public void saveConfig(PinConfig config, String encoded) {
        preferences.edit()
                .putString(KEY_CONFIG, encoded)
                .putBoolean("enabled", config.enabled())
                .commit();
    }

    public PinConfig loadConfig() {
        String encoded = preferences.getString(KEY_CONFIG, null);
        if (encoded == null) {
            return null;
        }
        try {
            return ConfigCodec.decode(encoded);
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    public void saveAllowlist(String rawList) {
        preferences.edit().putString(KEY_ALLOWLIST, rawList).commit();
    }

    public String loadAllowlist() {
        return preferences.getString(KEY_ALLOWLIST, "");
    }

    public boolean hasAllowlist() {
        return preferences.contains(KEY_ALLOWLIST);
    }
}
