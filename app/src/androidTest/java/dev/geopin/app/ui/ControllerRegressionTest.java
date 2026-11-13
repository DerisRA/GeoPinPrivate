package dev.geopin.app.ui;

import android.content.Context;
import android.widget.Button;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import dev.geopin.app.store.PinStore;
import dev.geopin.core.ConfigCodec;
import dev.geopin.core.Coordinate;
import dev.geopin.core.PinConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Run on a dedicated test device: each test resets the controller pin preferences. */
@RunWith(AndroidJUnit4.class)
public final class ControllerRegressionTest {
    private Context context;
    private PinStore store;

    @Before
    public void resetPreferences() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getSharedPreferences(PinStore.PREF_FILE, Context.MODE_PRIVATE)
                .edit().clear().commit());
        store = PinStore.open(context);
    }

    @Test
    public void disableIgnoresInvalidInput() {
        verifyDisable("coordinateInput", "invalid");
    }

    @Test
    public void disableIgnoresEditedCoordinates() {
        verifyDisable("coordinateInput", "30, 40");
    }

    @Test
    public void disableIgnoresInvalidAltitude() {
        verifyDisable("altitudeInput", "-");
    }

    @Test
    public void disableIgnoresEditedBearing() {
        verifyDisable("bearingInput", "90");
    }

    @Test
    public void emptyAllowlistSurvivesControllerRecreation() {
        store.saveAllowlist("");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertEquals("", store.loadAllowlist());
            scenario.recreate();
            assertEquals("", store.loadAllowlist());
        }
    }

    @Test
    public void firstLaunchInitializesDefaultAllowlist() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertEquals("dev.geopin.probe", store.loadAllowlist()));
        }
    }

    private void verifyDisable(String inputField, String editedValue) {
        PinConfig original = PinConfig.centeredAt(new Coordinate(10, 20));
        store.saveConfig(original, ConfigCodec.encode(original));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                EditText input = field(activity, inputField, EditText.class);
                input.setText(editedValue);
                field(activity, "toggleButton", Button.class).performClick();
                assertFalse(store.loadConfig().enabled());
                assertEquals(original.center(), store.loadConfig().center());
                assertEquals(original.manualAltitudeMeters(), store.loadConfig().manualAltitudeMeters());
                assertEquals(original.bearingDegrees(), store.loadConfig().bearingDegrees(), 0.0);
            });
        }
    }

    private static <T> T field(MainActivity activity, String name, Class<T> type) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(activity));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
