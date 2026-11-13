package dev.geopin.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scope dua lapis: paket harus dipilih di LSPosed <em>dan</em> tercantum di allowlist controller.
 * Paket sistem dan controller sendiri tidak pernah menerima lokasi palsu.
 */
public final class ScopePolicy {
    private static final List<String> PROTECTED_PACKAGES = Collections.unmodifiableList(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.gms.persistent"));

    private final String controllerPackage;
    private final Set<String> allowedPackages;

    public ScopePolicy(String controllerPackage, Collection<String> allowedPackages) {
        this.controllerPackage = controllerPackage == null ? "" : controllerPackage.trim();
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (allowedPackages != null) {
            for (String packageName : allowedPackages) {
                String trimmed = packageName == null ? "" : packageName.trim();
                if (!trimmed.isEmpty()) {
                    copy.add(trimmed);
                }
            }
        }
        this.allowedPackages = Collections.unmodifiableSet(copy);
    }

    /** Membaca allowlist dari teks dipisah koma atau baris baru. */
    public static Set<String> parseList(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[,\\s]+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    public static boolean isProtected(String packageName) {
        return packageName != null && PROTECTED_PACKAGES.contains(packageName);
    }

    public boolean isInjected(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (isProtected(packageName)) {
            return false;
        }
        if (!controllerPackage.isEmpty() && controllerPackage.equals(packageName)) {
            return false;
        }
        return allowedPackages.contains(packageName);
    }

    public Set<String> allowedPackages() {
        return allowedPackages;
    }

    public String controllerPackage() {
        return controllerPackage;
    }
}
