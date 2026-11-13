# Kontrak starter — GeoPin Private

Target runtime: Android 11/API 30; minimum Android 10/API 29 untuk UI. Java 8 source, JDK 17 build, AGP 8.7.3/Gradle 8.9. `compileSdk 35`, `targetSdk 30`, `minSdk 29`.

- `core`: Java murni, tanpa Android. `Coordinate`, `PinConfig`, `StationaryEngine`, `LocationSample`, `ConfigCodec`, `ScopePolicy`.
- `app`: controller dan modul legacy LSPosed; namespace `dev.geopin.app`, applicationId default `dev.geopin.privatepin` (dapat dikustomisasi melalui Gradle property, bukan klaim anti-deteksi).
- `probe`: aplikasi diagnostik milik proyek, package `dev.geopin.probe`, pembaca LocationManager dan GMS FLP. Tidak dimodifikasi sistem di luar aplikasi terpilih.
- Preferensi pin dipisah dari favorit. Hanya konfigurasi pin & allowlist yang dibaca cross-process. Favorit privat. Shared preferences legacy API 93 membutuhkan dukungan framework; bila tidak tersedia, toggle injection tidak diaktifkan. Tidak ada provider ekspor atau chmod.
- Scope dua lapis: dipilih di LSPosed **dan** tercantum dalam allowlist controller. Default hanya `dev.geopin.probe`. `android`, Google Play services, System UI, dan controller tidak menerima perubahan lokasi. Controller boleh di-scope hanya untuk indikator aktif.
- Hook menangani hasil LocationManager dan transport API 30 serta accessor hasil GMS. Modul tidak menjadwalkan callback baru dan tidak menyediakan lokasi saat provider tidak mengirim apa pun. Ketersediaan, izin, GNSS/raw satellite, native, geofence, parcel/PendingIntent bukan klaim cakupan starter.
- Sampling: satu sampel immutable per objek `Location` yang masuk, digunakan bersama oleh semua getter. Tidak membuat sampel per getter. Koordinat disk drift maksimum 0.9 m, akurasi 3.5–7.8 m, altitude manual ±0.3 m, speed 0, bearing manual. Time wall-clock dan monotonic dari clock yang di-inject.
- Default controller nonaktif, tanpa service/boot receiver/wakelock/overlay. Map osmdroid 6.1.20 dibuka on demand; OSM attribution, HTTPS, User-Agent aplikasi, cache bawaan, tidak ada bulk tile download.
- Tidak ada janji tersembunyi, anti-deteksi universal, <30 MB RAM, atau verifikasi Infinix tanpa perangkat. osmdroid sudah archived; legacy New XSharedPreferences tidak kompatibel universal dengan semua fork LSPosed.
- Tahap ini: source + tes JVM. Tidak menginstal SDK/menyetujui lisensi SDK; tidak menghasilkan atau mengklaim APK.
