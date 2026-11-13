package dev.geopin.core;

/** Sumber waktu berbasis jam host, dipakai di JVM dan sebagai fallback saat hook gagal. */
public final class SystemTimeSource implements TimeSource {
    public static final SystemTimeSource INSTANCE = new SystemTimeSource();

    private SystemTimeSource() {
    }

    @Override
    public long wallClockMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long elapsedRealtimeNanos() {
        return System.nanoTime();
    }
}
