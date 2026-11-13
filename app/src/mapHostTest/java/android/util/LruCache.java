package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    private final Map<K, V> entries = new LinkedHashMap<K, V>(16, 0.75f, true);
    private final int maximum;
    private int bytes;
    public LruCache(int maximum) { this.maximum = maximum; }
    protected int sizeOf(K key, V value) { return 1; }
    public synchronized V get(K key) { return entries.get(key); }
    public synchronized V put(K key, V value) {
        V old = entries.put(key, value);
        bytes += sizeOf(key, value) - (old == null ? 0 : sizeOf(key, old));
        while (bytes > maximum && !entries.isEmpty()) {
            K first = entries.keySet().iterator().next();
            bytes -= sizeOf(first, entries.remove(first));
        }
        return old;
    }
}
