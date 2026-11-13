package dev.geopin.app.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import dev.geopin.core.LocationSample;
import dev.geopin.core.ScopePolicy;

/**
 * Mengganti nilai getter {@code android.location.Location} di proses aplikasi target.
 * Satu sampel immutable dipakai untuk semua getter pada objek yang sama, sehingga
 * latitude, akurasi, altitude, dan waktu selalu konsisten satu sama lain.
 */
public final class LocationHook {
    private static final String LOCATION_CLASS = "android.location.Location";
    private static final String SAMPLE_FIELD = "dev.geopin.app.hook.LocationHook.sample";

    private final PinRuntime runtime;
    private final Object sampleLock = new Object();

    private LocationHook(PinRuntime runtime) {
        this.runtime = runtime;
    }

    public static void install(String packageName, ClassLoader classLoader) {
        if (packageName == null || ScopePolicy.isProtected(packageName)) {
            return;
        }
        PinRuntime runtime = PinRuntime.create(packageName);
        if (runtime == null) {
            return;
        }
        Class<?> locationClass = XposedHelpers.findClassIfExists(LOCATION_CLASS, classLoader);
        if (locationClass == null) {
            return;
        }
        try {
            new LocationHook(runtime).installHooks(locationClass);
            XposedBridge.log("[GeoPin] hook lokasi terpasang di " + packageName);
        } catch (Throwable error) {
            XposedBridge.log("[GeoPin] hook gagal di " + packageName + ": " + error);
        }
    }

    private void installHooks(Class<?> locationClass) {
        hookDouble(locationClass, "getLatitude", new SampleDouble() {
            @Override
            public double read(LocationSample sample) {
                return sample.latitude;
            }
        });
        hookDouble(locationClass, "getLongitude", new SampleDouble() {
            @Override
            public double read(LocationSample sample) {
                return sample.longitude;
            }
        });
        hookDouble(locationClass, "getAltitude", new SampleDouble() {
            @Override
            public double read(LocationSample sample) {
                return sample.altitudeMeters;
            }
        });
        hookFloat(locationClass, "getAccuracy", new SampleFloat() {
            @Override
            public float read(LocationSample sample) {
                return (float) sample.accuracyMeters;
            }
        });
        hookFloat(locationClass, "getSpeed", new SampleFloat() {
            @Override
            public float read(LocationSample sample) {
                return (float) sample.speedMetersPerSecond;
            }
        });
        hookFloat(locationClass, "getBearing", new SampleFloat() {
            @Override
            public float read(LocationSample sample) {
                return (float) sample.bearingDegrees;
            }
        });
        hookLong(locationClass, "getTime", new SampleLong() {
            @Override
            public long read(LocationSample sample) {
                return sample.wallClockMillis;
            }
        });
        hookLong(locationClass, "getElapsedRealtimeNanos", new SampleLong() {
            @Override
            public long read(LocationSample sample) {
                return sample.elapsedRealtimeNanos;
            }
        });
        hookFlag(locationClass, "hasAccuracy");
        hookFlag(locationClass, "hasAltitude");
        hookFlag(locationClass, "hasSpeed");
        hookFlag(locationClass, "hasBearing");
        hookMockFlag(locationClass, "isFromMockProvider");
        hookMockFlag(locationClass, "isMock");
    }

    private void hookDouble(Class<?> owner, String method, final SampleDouble reader) {
        try {
            XposedHelpers.findAndHookMethod(owner, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    LocationSample sample = sampleFor(param.thisObject);
                    if (sample != null) {
                        param.setResult(reader.read(sample));
                    }
                }
            });
        } catch (Throwable missing) {
            XposedBridge.log("[GeoPin] metode " + method + " tidak tersedia: " + missing.getMessage());
        }
    }

    private void hookFloat(Class<?> owner, String method, final SampleFloat reader) {
        try {
            XposedHelpers.findAndHookMethod(owner, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    LocationSample sample = sampleFor(param.thisObject);
                    if (sample != null) {
                        param.setResult(reader.read(sample));
                    }
                }
            });
        } catch (Throwable missing) {
            XposedBridge.log("[GeoPin] metode " + method + " tidak tersedia: " + missing.getMessage());
        }
    }

    private void hookLong(Class<?> owner, String method, final SampleLong reader) {
        try {
            XposedHelpers.findAndHookMethod(owner, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    LocationSample sample = sampleFor(param.thisObject);
                    if (sample != null) {
                        param.setResult(reader.read(sample));
                    }
                }
            });
        } catch (Throwable missing) {
            XposedBridge.log("[GeoPin] metode " + method + " tidak tersedia: " + missing.getMessage());
        }
    }

    private void hookFlag(Class<?> owner, String method) {
        try {
            XposedHelpers.findAndHookMethod(owner, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sampleFor(param.thisObject) != null) {
                        param.setResult(Boolean.TRUE);
                    }
                }
            });
        } catch (Throwable missing) {
            XposedBridge.log("[GeoPin] metode " + method + " tidak tersedia: " + missing.getMessage());
        }
    }

    private void hookMockFlag(Class<?> owner, String method) {
        try {
            XposedHelpers.findAndHookMethod(owner, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sampleFor(param.thisObject) != null) {
                        param.setResult(Boolean.FALSE);
                    }
                }
            });
        } catch (Throwable missing) {
            // isMock() baru ada di API 31; ketiadaannya di Android 11 bukan kegagalan.
            XposedBridge.log("[GeoPin] " + method + " tidak tersedia (normal di API 30)");
        }
    }

    private LocationSample sampleFor(Object location) {
        if (location == null) {
            return null;
        }
        synchronized (sampleLock) {
            LocationSample cached = (LocationSample) XposedHelpers.getAdditionalInstanceField(location, SAMPLE_FIELD);
            if (cached != null) {
                if (runtime.isActive()) {
                    return cached;
                }
                // titik dimatikan atau paket dicabut dari allowlist: jangan pertahankan nilai lama
                XposedHelpers.removeAdditionalInstanceField(location, SAMPLE_FIELD);
                return null;
            }
            LocationSample created = runtime.createSample();
            if (created == null) {
                return null;
            }
            XposedHelpers.setAdditionalInstanceField(location, SAMPLE_FIELD, created);
            return created;
        }
    }

    private interface SampleDouble {
        double read(LocationSample sample);
    }

    private interface SampleFloat {
        float read(LocationSample sample);
    }

    private interface SampleLong {
        long read(LocationSample sample);
    }
}
