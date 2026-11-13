package dev.geopin.app;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import dev.geopin.app.hook.LocationHook;

/**
 * Titik masuk modul. LSPosed memanggil ini di setiap proses aplikasi yang di-scope;
 * {@link LocationHook} memutuskan sendiri apakah paket ini benar-benar menerima titik diam.
 */
public final class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam == null || loadPackageParam.packageName == null) {
            return;
        }
        String packageName = loadPackageParam.packageName;
        try {
            if (BuildConfig.APPLICATION_ID.equals(packageName)) {
                XposedHelpers.findAndHookMethod(ModuleStatus.class.getName(),
                        loadPackageParam.classLoader, "isActive",
                        XC_MethodReplacement.returnConstant(true));
                return;
            }
            LocationHook.install(packageName, loadPackageParam.classLoader);
        } catch (Throwable error) {
            XposedBridge.log("[GeoPin] gagal memasang hook di " + packageName + ": " + error);
        }
    }
}
