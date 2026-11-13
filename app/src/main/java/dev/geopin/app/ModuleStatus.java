package dev.geopin.app;

/** Deteksi apakah proses aplikasi ini benar-benar sudah diinjeksi LSPosed. */
public final class ModuleStatus {

    private ModuleStatus() {
    }

    /** HookEntry mengganti hasil hanya pada proses controller yang diinjeksi LSPosed. */
    public static boolean isActive() {
        return false;
    }
}
