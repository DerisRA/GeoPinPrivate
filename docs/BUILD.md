# Build

Ada dua jalur build: Gradle untuk Android Studio/SDK lengkap dan pipeline manual berbasis Android
SDK tools. Gradle adalah jalur yang disarankan untuk pengembangan umum.

## 1. Jalur manual (tanpa Gradle)

Pipeline: `aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`.

Prasyarat:

| Komponen | Versi yang dipakai | Cara menyediakan |
| --- | --- | --- |
| JDK | Temurin 17.0.20.1 | `python tools/bootstrap_java.py`, atau setel `GEOPIN_JDK` |
| Android SDK | build-tools 34.0.0, platform-30 | setel `GEOPIN_SDK` ke folder berisi `build-tools/` dan `platform-30/` |
| Xposed API | `api-82.jar` | `.toolchain/libs/api-82.jar`, atau setel `GEOPIN_XPOSED_API` |

Urutan pencarian SDK: `GEOPIN_SDK` → `.toolchain/android-sdk` → `ANDROID_HOME` → `ANDROID_SDK_ROOT`.
Urutan pencarian JDK: `GEOPIN_JDK` → `.toolchain/jdk` → `JAVA_HOME`.

Perintah:

```bash
python tools/build_apk.py --module all      # app + probe
python tools/build_apk.py --module app      # hanya controller
python tools/verify_apk.py                  # pemeriksaan statis paket
python tools/test_core.py                   # unit test JVM core
python tools/test_hook.py                   # logika cache hook dengan test double JVM
```

Keluaran:

- `dist/GeoPinPrivate-0.1.0-debug.apk` — controller + modul LSPosed
- `dist/GeoPinProbe-0.1.0-debug.apk` — aplikasi diagnostik
- `dist/verification.json` — laporan pemeriksaan statis

Build memakai `-Werror` dengan `-Xlint:all` untuk Java dan menolak APK yang gagal `apksigner verify`.

### Reproduksibilitas

Build **tidak** byte-identik antar-jalan: `classes.dex` dan entri `META-INF/*` membawa waktu build dari
zip, dan tanda tangan v2 menyegelnya. Karena itu SHA-256 di dokumentasi hanya berlaku untuk artefak
yang benar-benar ada di `dist/`. Untuk memeriksa ulang sebuah APK tanpa menebak hash, jalankan
`python tools/verify_apk.py` — skrip itu membaca isi paket, bukan hash.

### Tanda tangan

Keystore debug dibuat otomatis di `.toolchain/debug.keystore` (alias `geopin`, kata sandi `android`).
Karena `minSdk 29`, apksigner menandatangani dengan **skema v3** saja; tanda tangan v1 (JAR signing)
tidak diperlukan dan tidak dihasilkan. Karena itu berkas ini di-gitignore. Untuk build pribadi yang
stabil, simpan berkas tersebut; jika dihapus, build berikutnya memakai kunci baru dan APK harus
di-uninstall dulu sebelum dipasang ulang.

### Mengganti package name

Jalur manual membaca `GEOPIN_APPLICATION_ID` (default `dev.geopin.privatepin`), jalur Gradle membaca
properti `-PgeopinApplicationId`. Namespace R tetap `dev.geopin.app` di kedua jalur; manifest sumber
tidak memuat atribut `package` (AGP 8 menolaknya) dan `tools/build_apk.py` menyuntikkannya ke salinan
manifest di `build/` sebelum `aapt2 link`. Hook membaca `BuildConfig.APPLICATION_ID`, jadi nilai itu
otomatis mengikuti kedua jalur.

## 2. Jalur Gradle

Dipakai bila Anda punya Android Studio/SDK lengkap:

```bash
./gradlew :app:assembleDebug :probe:assembleDebug
# applicationId kustom:
./gradlew :app:assembleDebug -PgeopinApplicationId=com.contoh.calibrator
```

Catatan:

- `compileSdk 35`, `minSdk 29`, `targetSdk 30` (sesuai perangkat Android 11).
- `app/build.gradle` memakai `compileOnly 'de.robv.android.xposed:api:82'`.
- `probe` sengaja tanpa dependensi GMS.
- Jalur Gradle dan jalur manual memakai sumber Java yang sama; `BuildConfig` di jalur manual
  dihasilkan oleh `tools/build_apk.py`.

## 3. Struktur build manual

Tes regresi controller ada di `app/src/androidTest/`. Dengan Gradle dan SDK lengkap,
jalankan `gradle :app:connectedDebugAndroidTest` pada emulator/perangkat uji khusus.
Tes ini menghapus preferensi pin controller sebelum tiap kasus. Tes mencakup tombol
matikan dengan input berubah/tidak valid, allowlist kosong, dan default instalasi baru.
Tes ini tidak membuktikan injeksi LSPosed; pengujian itu tetap mengikuti `DEVICE_TEST.md`.

`tools/test_hook.py` mengompilasi `LocationHook` asli dengan pengganti runtime dan
penyimpanan field Xposed khusus tes. Ia memeriksa konsistensi 300 objek hidup dan
penghentian sampel ketika runtime nonaktif, bukan integrasi Android/LSPosed.

```
build/<module>/
  gen/          R.java dan BuildConfig.java
  res/          hasil aapt2 compile
  base.apk      hasil aapt2 link (resource + manifest + assets)
  classes/      keluaran javac
  classes.jar   pembungkus untuk d8
  dex/          classes.dex
  unsigned.apk  base.apk + classes.dex
  aligned.apk   hasil zipalign
```

Folder ini dihapus lagi setelah build selesai kecuali `--keep` diberikan.
