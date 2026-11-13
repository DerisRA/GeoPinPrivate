package dev.geopin.app.hook;

import dev.geopin.core.LocationSample;

/** Runtime double: isolates cache lifetime from Android preferences and clocks. */
final class PinRuntime {
    boolean active = true;
    private int sequence;

    static PinRuntime create(String packageName) {
        return new PinRuntime();
    }

    boolean isActive() {
        return active;
    }

    LocationSample createSample() {
        if (!active) {
            return null;
        }
        sequence++;
        return new LocationSample(10, 20, 5, 40, 0, 0, sequence, sequence);
    }
}
