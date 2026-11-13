package dev.geopin.core;

import java.util.Objects;

/**
 * Serialisasi konfigurasi pin ke format baris {@code key=value} berversi.
 * Format ini dipakai lintas proses (aplikasi UI ke hook) tanpa ketergantungan Android.
 */
public final class ConfigCodec {
    private static final String HEADER = "geopin-pin/1";
    private static final String AUTO = "auto";

    private ConfigCodec() {
    }

    public static String encode(PinConfig config) {
        Objects.requireNonNull(config, "config");
        StringBuilder builder = new StringBuilder(128);
        builder.append(HEADER).append('\n');
        builder.append("lat=").append(config.center().latitude).append('\n');
        builder.append("lon=").append(config.center().longitude).append('\n');
        Double manualAltitude = config.manualAltitudeMeters();
        builder.append("alt=").append(manualAltitude == null ? AUTO : manualAltitude.toString()).append('\n');
        builder.append("bearing=").append(config.bearingDegrees()).append('\n');
        builder.append("enabled=").append(config.enabled() ? '1' : '0').append('\n');
        builder.append("label=").append(escape(config.label())).append('\n');
        return builder.toString();
    }

    public static PinConfig decode(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Teks konfigurasi tidak boleh null");
        }
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("Header konfigurasi tidak dikenal");
        }
        Double latitude = null;
        Double longitude = null;
        Double altitude = null;
        double bearing = 0.0;
        boolean enabled = false;
        String label = "";
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.trim().isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Baris konfigurasi rusak: " + line);
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if ("lat".equals(key)) {
                latitude = readNumber(value, key);
            } else if ("lon".equals(key)) {
                longitude = readNumber(value, key);
            } else if ("alt".equals(key)) {
                altitude = AUTO.equals(value) ? null : readNumber(value, key);
            } else if ("bearing".equals(key)) {
                bearing = readNumber(value, key);
            } else if ("enabled".equals(key)) {
                enabled = "1".equals(value);
            } else if ("label".equals(key)) {
                label = unescape(value);
            }
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Konfigurasi tidak memuat lat/lon");
        }
        return new PinConfig(new Coordinate(latitude, longitude), altitude, bearing, enabled, label);
    }

    private static Double readNumber(String value, String key) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Nilai " + key + " bukan angka: " + value, error);
        }
    }

    private static String escape(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\') {
                builder.append("\\\\");
            } else if (character == '\n') {
                builder.append("\\n");
            } else if (character == '\r') {
                builder.append("\\r");
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static String unescape(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '\\') {
                builder.append(character);
                continue;
            }
            index++;
            if (index >= text.length()) {
                throw new IllegalArgumentException("Escape terpotong di akhir label");
            }
            char escaped = text.charAt(index);
            if (escaped == 'n') {
                builder.append('\n');
            } else if (escaped == 'r') {
                builder.append('\r');
            } else if (escaped == '\\') {
                builder.append('\\');
            } else {
                throw new IllegalArgumentException("Escape tidak dikenal: \\" + escaped);
            }
        }
        return builder.toString();
    }
}
