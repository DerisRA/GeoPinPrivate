package dev.geopin.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ScopePolicyTest {
    private static final String CONTROLLER = "dev.geopin.privatepin";

    @Test
    public void injectsOnlyAllowlistedPackages() {
        ScopePolicy policy = new ScopePolicy(CONTROLLER, Arrays.asList("dev.geopin.probe", "com.example.target"));
        assertTrue(policy.isInjected("dev.geopin.probe"));
        assertTrue(policy.isInjected("com.example.target"));
        assertFalse(policy.isInjected("com.example.other"));
        assertFalse(policy.isInjected(null));
        assertFalse(policy.isInjected(""));
    }

    @Test
    public void neverInjectsIntoSystemOrController() {
        ScopePolicy policy = new ScopePolicy(CONTROLLER, Arrays.asList(
                "android", "com.android.systemui", "com.google.android.gms", CONTROLLER));
        assertFalse(policy.isInjected("android"));
        assertFalse(policy.isInjected("com.android.systemui"));
        assertFalse(policy.isInjected("com.google.android.gms"));
        assertFalse(policy.isInjected("com.google.android.gsf"));
        assertFalse(policy.isInjected(CONTROLLER));
        assertTrue(ScopePolicy.isProtected("android"));
        assertFalse(ScopePolicy.isProtected("com.example.target"));
    }

    @Test
    public void parseListHandlesCommasWhitespaceAndBlankEntries() {
        Set<String> parsed = ScopePolicy.parseList(" dev.geopin.probe, com.example.target ,, \n com.example.third ");
        assertEquals(new HashSet<>(Arrays.asList("dev.geopin.probe", "com.example.target", "com.example.third")), parsed);
        assertTrue(ScopePolicy.parseList(null).isEmpty());
        assertTrue(ScopePolicy.parseList("   ").isEmpty());
    }

    @Test
    public void allowlistIsImmutableCopy() {
        ScopePolicy policy = new ScopePolicy(CONTROLLER, Arrays.asList("dev.geopin.probe"));
        Set<String> snapshot = policy.allowedPackages();
        assertEquals(1, snapshot.size());
        try {
            snapshot.add("com.example.hacked");
            throw new AssertionError("allowlist seharusnya immutable");
        } catch (UnsupportedOperationException expected) {
            // sesuai kontrak: allowlist tidak boleh diubah pemanggil
        }
    }
}
