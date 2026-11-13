# GeoPin

GeoPin adalah modul Android berbasis LSPosed untuk menguji satu lokasi GPS tetap pada aplikasi yang dipilih. Proyek ini memisahkan controller, hook lokasi, mesin koordinat, dan aplikasi probe agar perilakunya mudah dipahami dan diuji.

> **Status:** proyek portofolio/eksperimen. Membutuhkan perangkat Android yang sudah di-root, Zygisk, dan LSPosed. Kompatibilitas bergantung pada versi Android, framework root, dan aplikasi target.

## Fitur

- Memilih koordinat melalui input atau peta OpenStreetMap.
- Menetapkan titik, ketinggian, dan bearing.
- Membatasi hook dengan scope LSPosed dan allowlist paket.
- Menyimpan favorit dan pengaturan secara lokal.
- Menyediakan aplikasi GeoPin Probe untuk memeriksa pembacaan lokasi.
- Memuat lokasi hanya saat aplikasi target meminta pembaruan; modul tidak membuat service lokasi latar.

## Cara kerja singkat

```text
GeoPin controller ── simpan konfigurasi ──> penyimpanan lokal
                                               │
                                               ▼
Target yang diizinkan ── LSPosed hook ──> pembacaan Location
GeoPin Probe ───────────────────────────> memeriksa hasil
```

Hook memodifikasi getter pada objek `android.location.Location`. API atau aplikasi yang membaca field internal, parcel, atau menghitung jarak sendiri mungkin tidak melihat nilai yang sama.

## Kebutuhan

- Perangkat Android 10 atau lebih baru (`minSdk 29`).
- Root dengan Magisk, KernelSU, atau APatch dan dukungan Zygisk yang sesuai.
- LSPosed aktif.
- Untuk build Gradle: JDK 17, Android SDK platform 35/build tools yang sesuai, dan koneksi untuk mengambil dependensi.
- Untuk jalur build manual: JDK 17, Android SDK platform 30 + build-tools 34, serta Xposed API 82.

## Build

### Gradle (disarankan)

```bash
gradle :app:assembleDebug :probe:assembleDebug
```

APK muncul di `app/build/outputs/apk/debug/` dan `probe/build/outputs/apk/debug/`. Gunakan Android Studio atau wrapper Gradle yang disediakan lingkungan build Anda.

### Build manual

Jalur manual tersedia melalui `aapt2`, `javac`, `d8`, `zipalign`, dan `apksigner`:

```bash
python tools/build_apk.py --module all
python tools/verify_apk.py
```

Atur `GEOPIN_SDK`, `GEOPIN_JDK`, dan `GEOPIN_XPOSED_API` bila alat tidak ada di lokasi standar yang dikenali skrip. Lihat [panduan build](docs/BUILD.md) untuk detail dan struktur SDK yang diperlukan.

### Pemeriksaan kode

```bash
python tools/test_core.py
python tools/test_map.py
python tools/test_hook.py
```

Perintah ini menjalankan tes unit JVM dengan toolchain yang disiapkan oleh proyek. Cakupan dan batas verifikasi perangkat dijelaskan di [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Instalasi dan penggunaan

1. Bangun dan pasang APK GeoPin serta GeoPin Probe.
2. Di LSPosed, aktifkan modul GeoPin dan pilih paket target di scope.
3. Buka GeoPin, masukkan paket tersebut ke allowlist, pilih koordinat, lalu aktifkan titik.
4. Tutup dan buka kembali aplikasi target agar hook dimuat.
5. Gunakan Probe atau aplikasi target untuk memeriksa hasil.

Nama paket default Probe adalah `dev.geopin.probe`; application ID controller default adalah `dev.geopin.privatepin`. Instalasi dari build berbeda mungkin perlu menghapus versi sebelumnya jika signing key berbeda.

## Struktur proyek

| Folder | Peran |
| --- | --- |
| `app/` | Controller Android, peta, penyimpanan, dan modul LSPosed |
| `core/` | Tipe koordinat, codec konfigurasi, kebijakan scope, mesin titik diam |
| `probe/` | Aplikasi diagnostik pembacaan lokasi |
| `tools/` | Build manual, pemeriksaan APK, dan tes JVM |
| `docs/` | Arsitektur, keputusan, build, troubleshooting, dan verifikasi |

## Batasan

- Tidak menjamin hasil yang sama pada semua aplikasi atau versi LSPosed.
- Tidak mengubah semua jalur lokasi Android, misalnya API fused location atau pembacaan internal aplikasi.
- Peta memerlukan internet untuk mengambil ubin OpenStreetMap; atribusi peta ditampilkan di aplikasi.
- Proyek ini ditujukan untuk pengujian pada perangkat dan aplikasi yang Anda miliki atau berhak modifikasi.

## Lisensi

Lisensi belum ditetapkan. Minta izin sebelum menggunakan, memodifikasi, atau mendistribusikan proyek ini sampai file lisensi ditambahkan.
