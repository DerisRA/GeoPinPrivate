package de.robv.android.xposed;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Host-only substitute for Xposed's additional instance fields, not hook execution. */
public final class XposedHelpers {
    private static final Map<Object, Map<String, Object>> FIELDS = new WeakHashMap<>();

    private XposedHelpers() {
    }

    public static synchronized Object getAdditionalInstanceField(Object owner, String key) {
        Map<String, Object> fields = FIELDS.get(owner);
        return fields == null ? null : fields.get(key);
    }

    public static synchronized Object setAdditionalInstanceField(Object owner, String key, Object value) {
        return FIELDS.computeIfAbsent(owner, ignored -> new HashMap<>()).put(key, value);
    }

    public static synchronized Object removeAdditionalInstanceField(Object owner, String key) {
        Map<String, Object> fields = FIELDS.get(owner);
        return fields == null ? null : fields.remove(key);
    }

    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        throw new UnsupportedOperationException("Host test does not install hooks");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> owner, String name, Object... args) {
        throw new UnsupportedOperationException("Host test does not install hooks");
    }
}
