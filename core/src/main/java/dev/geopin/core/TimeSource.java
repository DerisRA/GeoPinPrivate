package dev.geopin.core;

/**
 * Sumber waktu yang bisa diganti saat pengujian. Hook memakai jam Android supaya
 * {@code time} dan {@code elapsedRealtimeNanos} selalu mengikuti sistem.
 */
public interface TimeSource {
    /** Waktu dinding UTC dalam milidetik, padanan {@code System.currentTimeMillis()}. */
    long wallClockMillis();

    /** Waktu monotonik dalam nanodetik, padanan {@code SystemClock.elapsedRealtimeNanos()}. */
    long elapsedRealtimeNanos();
}
