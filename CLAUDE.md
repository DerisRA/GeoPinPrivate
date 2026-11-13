# Project Instructions: GeoPin Private

## Tech Stack
- **Language**: Java 8 (source/target compatibility 1.8), built with JDK 17
- **Android Target**: minSdk 29 (Android 10), targetSdk 30 (Android 11), compileSdk 35
- **Frameworks & APIs**: LSPosed / Xposed API 82 (`de.robv.android.xposed:api:82`)
- **Build Toolchain**: Python scripts (`tools/build_apk.py` using aapt2, javac, d8, apksigner) or Gradle (AGP 8.7.3)
- **Testing**: JUnit 4.13.2

## Build & Testing Commands
- **Run JVM Core Tests**: `python tools/test_core.py`
- **Build APKs (no Gradle)**: `python tools/build_apk.py --module all` (or `--module app` / `--module probe`)
- **Verify APK Packaging**: `python tools/verify_apk.py`
- **Gradle Build**: `./gradlew :app:assembleDebug :probe:assembleDebug`
- **Custom Application ID**: `python tools/build_apk.py` (reads `GEOPIN_APPLICATION_ID` env var) or `./gradlew -PgeopinApplicationId=com.example.app`

## Project Structure
- `core/`: Pure Java 8 geodetic and stationary jitter engine (zero Android framework dependencies).
  - `Coordinate`: Validated WGS84 coordinate parsing and formatting.
  - `StationaryEngine`: Micro-jitter generator (drift <= 0.9m, accuracy 3.5-7.8m, speed 0.0, altitude +/- 0.3m).
  - `ConfigCodec`: Versioned line-based `key=value` serialization.
  - `ScopePolicy`: Two-tier package scoping and system app protection.
  - `Mercator`: Web Mercator projection calculations.
- `app/`: LSPosed hook implementation and controller UI.
  - `dev.geopin.app.HookEntry`: LSPosed entry point (`handleLoadPackage`).
  - `dev.geopin.app.hook.LocationHook`: Getter interceptor on `android.location.Location`.
  - `dev.geopin.app.hook.PinRuntime`: In-process runtime, jitter engine, and `XSharedPreferences` reloader.
  - `dev.geopin.app.ui.MainActivity`: Controller UI for toggling pins, editing coordinates, and favorites.
  - `dev.geopin.app.ui.PinMapView` & `TileStore`: Built-in Web Mercator map viewer with HTTPS tile caching.
  - `dev.geopin.app.store.PinStore`: IPC configuration store (`geopin_pin.xml` with `xposedsharedprefs`).
  - `dev.geopin.app.store.Favorites`: Private local storage for saved locations (`geopin_favorites.xml`).
- `probe/`: Standalone diagnostic app (`dev.geopin.probe`) that reads location via `LocationManager`.
- `tools/`: Build scripts (`build_apk.py`, `test_core.py`, `verify_apk.py`, `bootstrap_java.py`).
- `docs/`: Product requirements (`PRD.md`), technical decisions (`DECISIONS.md`), and verification logs (`VERIFICATION.md`).

## Core Conventions & Invariants
- **Zero Background Services**: No background services, wake locks, or boot receivers in the controller. Injections run entirely within target processes.
- **Pure Java Core**: Never introduce Android framework imports (`android.*`) into `core/`.
- **Single Immutable Sample**: In `LocationHook`, reuse a single immutable `LocationSample` per `Location` instance across all getters.
- **Two-Layer Scope**: Injections require both LSPosed Manager enablement AND inclusion in the controller allowlist (`ScopePolicy`).
- **Protected Packages**: System framework, System UI, Google Play Services, and the controller itself are strictly excluded from location spoofing.
- **IPC Privacy**: Only `PinStore` (`geopin_pin.xml`) is exposed via `xposedsharedprefs`. Favorites (`geopin_favorites.xml`) must remain strictly private (`MODE_PRIVATE`).
- **Code Hygiene**: Strict compilation with `-Werror` and `-Xlint:all` enabled.

