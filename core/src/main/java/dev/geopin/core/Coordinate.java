package dev.geopin.core;

import java.util.Locale;

/** Posisi WGS84 tervalidasi. Nilai di luar rentang atau non-finite ditolak saat konstruksi. */
public final class Coordinate {
    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    public final double latitude;
    public final double longitude;

    public Coordinate(double latitude, double longitude) {
        if (!isFinite(latitude) || !isFinite(longitude)) {
            throw new IllegalArgumentException("Koordinat harus berupa angka berhingga");
        }
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException("Latitude di luar rentang -90..90: " + latitude);
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException("Longitude di luar rentang -180..180: " + longitude);
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Membaca koordinat yang ditempel pengguna, misalnya {@code "-6.175392, 106.827153"}.
     * Pemisah boleh koma, titik koma, atau spasi; akhiran arah N/S/E/W juga diterima.
     */
    public static Coordinate parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Teks koordinat tidak boleh null");
        }
        String cleaned = text.replace('\u00B0', ' ').replace(';', ',').trim();
        if (cleaned.isEmpty()) {
            throw invalid(text);
        }
        String[] raw = cleaned.split("[,\\s]+");
        String[] tokens = new String[raw.length];
        int count = 0;
        for (String token : raw) {
            if (!token.isEmpty()) {
                tokens[count++] = token;
            }
        }
        if (count != 2) {
            throw invalid(text);
        }
        double latitude = readComponent(tokens[0], "latitude", text);
        double longitude = readComponent(tokens[1], "longitude", text);
        char first = lastLetter(tokens[0]);
        char second = lastLetter(tokens[1]);
        if ((first != 0 && !isLatitudeSuffix(first))
                || (second != 0 && !isLongitudeSuffix(second))) {
            throw invalid(text);
        }
        if (isLatitudeSuffix(first)) {
            latitude = signed(latitude, first);
        }
        if (isLongitudeSuffix(second)) {
            longitude = signed(longitude, second);
        }
        return new Coordinate(latitude, longitude);
    }

    /** Format stabil enam desimal untuk ditempel kembali ke kolom input. */
    public String format() {
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude);
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Coordinate)) {
            return false;
        }
        Coordinate that = (Coordinate) other;
        return Double.compare(latitude, that.latitude) == 0 && Double.compare(longitude, that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(latitude) + Double.hashCode(longitude);
    }

    private static double readComponent(String token, String label, String source) {
        String digits = stripSuffix(token);
        if (digits.isEmpty()) {
            throw invalid(source);
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Bagian " + label + " bukan angka: " + token, error);
        }
    }

    private static String stripSuffix(String token) {
        char last = lastLetter(token);
        if (last == 0) {
            return token;
        }
        return token.substring(0, token.length() - 1);
    }

    private static char lastLetter(String token) {
        if (token.isEmpty()) {
            return 0;
        }
        char last = token.charAt(token.length() - 1);
        return Character.isLetter(last) ? Character.toUpperCase(last) : 0;
    }

    private static boolean isLatitudeSuffix(char c) {
        return c == 'N' || c == 'S';
    }

    private static boolean isLongitudeSuffix(char c) {
        return c == 'E' || c == 'W';
    }

    private static double signed(double value, char suffix) {
        boolean negative = suffix == 'S' || suffix == 'W';
        return negative ? -Math.abs(value) : Math.abs(value);
    }

    private static IllegalArgumentException invalid(String source) {
        return new IllegalArgumentException("Format koordinat tidak dikenal: \"" + source + "\"");
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
