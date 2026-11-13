package dev.geopin.app.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import dev.geopin.core.LocationSample;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class LocationHookTest {
    private PinRuntime runtime;
    private LocationHook hook;
    private Method sampleFor;

    @Before
    public void setUp() throws Exception {
        runtime = new PinRuntime();
        Constructor<LocationHook> constructor = LocationHook.class.getDeclaredConstructor(PinRuntime.class);
        constructor.setAccessible(true);
        hook = constructor.newInstance(runtime);
        sampleFor = LocationHook.class.getDeclaredMethod("sampleFor", Object.class);
        sampleFor.setAccessible(true);
    }

    @Test
    public void retainsSamplesForAllLiveLocationsBeyond128() throws Exception {
        List<Object> locations = new ArrayList<>();
        List<LocationSample> samples = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            Object location = new Object();
            locations.add(location);
            samples.add(sample(location));
        }
        for (int index = 0; index < locations.size(); index++) {
            assertSame(samples.get(index), sample(locations.get(index)));
        }
    }

    @Test
    public void disablingSuppressesAndRemovesCachedSample() throws Exception {
        Object location = new Object();
        LocationSample before = sample(location);
        runtime.active = false;
        assertNull(sample(location));
        assertNull(sample(new Object()));
        runtime.active = true;
        assertNotSame(before, sample(location));
    }

    @Test
    public void nullLocationHasNoSample() throws Exception {
        assertNull(sample(null));
    }

    private LocationSample sample(Object location) throws Exception {
        return (LocationSample) sampleFor.invoke(hook, location);
    }
}
