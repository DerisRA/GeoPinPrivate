package dev.geopin.app.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.geopin.core.ConfigCodec;
import dev.geopin.core.Coordinate;
import dev.geopin.core.PinConfig;

/** Daftar titik favorit, disimpan privat dan tidak pernah dibaca proses lain. */
public final class Favorites {
    private static final String PREF_FILE = "geopin_favorites";
    private static final String KEY_ITEMS = "items";

    private final SharedPreferences preferences;

    public Favorites(Context context) {
        this.preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public List<PinConfig> load() {
        String raw = preferences.getString(KEY_ITEMS, "[]");
        List<PinConfig> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                String encoded = item.optString("config", "");
                if (encoded.isEmpty()) {
                    continue;
                }
                try {
                    result.add(ConfigCodec.decode(encoded));
                } catch (IllegalArgumentException broken) {
                    // entri rusak dilewati, bukan menggagalkan seluruh daftar
                }
            }
        } catch (JSONException broken) {
            return Collections.emptyList();
        }
        return result;
    }

    public void save(List<PinConfig> configs) {
        JSONArray array = new JSONArray();
        for (PinConfig config : configs) {
            JSONObject item = new JSONObject();
            try {
                item.put("config", ConfigCodec.encode(config));
            } catch (JSONException impossible) {
                continue;
            }
            array.put(item);
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).commit();
    }

    /** Label default bila pengguna menyimpan tanpa memberi nama. */
    public static String describe(PinConfig config) {
        String label = config.label();
        if (!label.trim().isEmpty()) {
            return label.trim();
        }
        Coordinate center = config.center();
        return center.format();
    }
}
