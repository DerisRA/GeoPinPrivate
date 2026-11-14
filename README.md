# GeoPin

GeoPin is an LSPosed Android module for testing a fixed GPS location in selected apps. The project separates the controller, location hook, coordinate engine, and probe app to make their behavior easier to understand and verify.

> **Status:** Portfolio and experimental project. Requires a rooted Android device, Zygisk, and LSPosed. Compatibility depends on the Android version, root framework, and target app.

## Features

- Choose coordinates by entering them or selecting a point on an OpenStreetMap view.
- Set a location, altitude, and bearing.
- Limit hooks through LSPosed scope and a package allowlist.
- Store favorites and settings locally.
- Use the GeoPin Probe app to inspect location readings.
- Apply the location only when a target app requests an update; the module does not run a background location service.

## How it works

```text
GeoPin controller -- saves configuration --> local storage
                                             |
                                             v
Allowed target app -- LSPosed hook --------> Location reading
GeoPin Probe ------------------------------> checks the result
```

The hook modifies getters on `android.location.Location` objects. APIs or apps that read internal fields or parcel data, or calculate distance independently, may not see the same values.

## Requirements

- Android 10 or later (`minSdk 29`).
- Root access through Magisk, KernelSU, or APatch with compatible Zygisk support.
- LSPosed enabled.
- For Gradle builds: JDK 17, Android SDK platform 35 and the matching build tools, and an internet connection for dependencies.
- For manual builds: JDK 17, Android SDK platform 30, build-tools 34, and Xposed API 82.

## Build

### Gradle (recommended)

```bash
gradle :app:assembleDebug :probe:assembleDebug
```

The APKs are created in `app/build/outputs/apk/debug/` and `probe/build/outputs/apk/debug/`. Use Android Studio or a Gradle wrapper provided by your build environment.

### Manual build

The manual build uses `aapt2`, `javac`, `d8`, `zipalign`, and `apksigner`:

```bash
python tools/build_apk.py --module all
python tools/verify_apk.py
```

Set `GEOPIN_SDK`, `GEOPIN_JDK`, and `GEOPIN_XPOSED_API` if the tools are not in a location recognized by the scripts. See the [build guide](docs/BUILD.md) for details and the required SDK layout.

### Code checks

```bash
python tools/test_core.py
python tools/test_map.py
python tools/test_hook.py
```

These commands run JVM unit tests with the toolchain prepared by the project. See [verification notes](docs/VERIFICATION.md) for coverage and device-testing limits.

## Install and use

1. Build and install GeoPin and GeoPin Probe.
2. In LSPosed, enable GeoPin and select target packages in its scope.
3. Open GeoPin, add the target package to the allowlist, choose coordinates, and enable the location.
4. Close and reopen the target app to load the hook.
5. Use Probe or the target app to inspect the result.

The Probe package name is `dev.geopin.probe`; the controller application ID defaults to `dev.geopin.privatepin`. Installing a build signed with a different key may require uninstalling the previous version first.

## Project structure

| Directory | Purpose |
| --- | --- |
| `app/` | Android controller, map, storage, and LSPosed module |
| `core/` | Coordinate types, configuration codec, scope policy, and stationary location engine |
| `probe/` | Location-reading diagnostic app |
| `tools/` | Manual build, APK checks, and JVM tests |
| `docs/` | Architecture, decisions, build, troubleshooting, and verification documentation |

## Limitations

- Behavior is not guaranteed across all apps or LSPosed versions.
- The module does not cover every Android location path, such as fused location APIs or app-internal reads.
- The map needs an internet connection to load OpenStreetMap tiles; map attribution is shown in the app.
- This project is intended for testing on devices and apps you own or are authorized to modify.

## License

No license has been assigned. Ask for permission before using, modifying, or distributing this project until a license file is added.
