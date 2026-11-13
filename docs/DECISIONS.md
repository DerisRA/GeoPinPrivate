# Keputusan teknis

Catatan singkat untuk keputusan yang menyimpang dari PRD/kontrak awal, beserta alasannya.

## D-01 — Peta OSM ditulis sendiri, bukan osmdroid

**Konteks.** PRD dan `STARTER_CONTRACT.md` menyebut `osmdroid-android:6.1.20`. Mesin build ini tidak
punya Gradle, jadi APK dirakit dengan `aapt2` + `javac` + `d8`.

**Masalah.** AAR osmdroid membawa resource dan kelas `org.osmdroid.library.R` yang pada build Gradle
dihasilkan otomatis. Tanpa Gradle, kelas R itu harus disintesis manual dan resource-nya digabung.
Kesalahan pemetaan ID akan menjadi `NoClassDefFoundError`/resource salah **saat runtime**, dan tidak
ada perangkat untuk mengujinya.

**Keputusan.** `PinMapView` + `TileStore` ditulis sendiri: proyeksi Web Mercator, geser, cubit,
ketuk-dua-kali zoom, ketuk untuk pin, cache memori + disk, HTTPS, User-Agent aplikasi, hanya ubin
terlihat. Tanpa dependensi eksternal.

**Konsekuensi.** Tidak ada fitur osmdroid (overlay, marker cluster, rotasi). Perilaku peta belum diuji
di perangkat. Cache disk ubin tidak dibatasi sendiri — ia berada di `getCacheDir()/osm` dan dibersihkan
oleh sistem bila ruang menipis. Bila ingin kembali ke osmdroid, tambahkan dependensi di
`app/build.gradle`, ganti `PinMapView` dengan `MapView`, dan bangun lewat Gradle — bukan jalur manual.

## D-08 — Peta menggenggam gesturnya sendiri

**Keputusan.** `PinMapView.onTouchEvent` memanggil `requestDisallowInterceptTouchEvent(true)` saat
`ACTION_DOWN` dan melepasnya saat `ACTION_UP`/`ACTION_CANCEL`. Flag `pinching` di-set dari
`onScale`, dan `onSingleTapUp` mengonsumsinya tanpa menaruh pin.

**Alasan.** Tanpa yang pertama, `ScrollView` induk mencuri drag vertikal setelah melewati touch slop
sehingga peta tidak bisa digeser ke atas/bawah. Tanpa yang kedua, jari terakhir dari cubit memicu
ketukan dan memindahkan pin tanpa sengaja.

**Konsekuensi.** Selama jari menyentuh peta, halaman tidak ikut bergulir; pengguna harus mengangkat
jari lalu menggulir di luar area peta. Ini disengaja agar pan/pinch konsisten.

## D-02 — Probe tanpa dependensi Google Play Services

**Konteks.** Kontrak menyebut probe membaca `LocationManager` dan GMS FLP.

**Keputusan.** Probe memakai `LocationManager` saja. Menambah `play-services-location` berarti
menarik banyak AAR transitif yang harus dirakit tanpa Gradle, dengan risiko *resource merge* yang
tidak bisa diverifikasi di perangkat.

**Konsekuensi.** Jalur FLP belum diuji langsung. Secara desain, hook mengganti getter
`android.location.Location` sehingga hasil FLP (objek kelas yang sama) ikut berubah, tetapi ini
belum dibuktikan di perangkat.

## D-03 — Hook di level getter `Location`, bukan di level transport

**Keputusan.** `LocationHook` membungkus `getLatitude`, `getLongitude`, `getAccuracy`, `getAltitude`,
`getSpeed`, `getBearing`, `getTime`, `getElapsedRealtimeNanos`, `hasAccuracy/Altitude/Speed/Bearing`,
`isFromMockProvider`, dan `isMock` (bila ada).

**Alasan.** Satu titik hook menangkap semua jalur pengiriman (LocationManager, listener, hasil FLP,
parcel yang dibaca ulang menjadi `Location`) tanpa perlu mengetahui kelas milik aplikasi target, dan
tanpa menjadwalkan callback baru — sesuai kontrak "modul tidak menyediakan lokasi saat provider tidak
mengirim apa pun".

**Konsekuensi.** Kode yang membaca field internal (mis. `Location.distanceTo`, `toString`, `Parcel`)
tidak melihat nilai palsu. Satu sampel immutable di-cache per objek `Location` agar semua getter
konsisten.

## D-04 — Konfigurasi lintas proses lewat XSharedPreferences + `xposedsharedprefs`

**Keputusan.** `PinStore` menulis dengan `Context.MODE_PRIVATE` biasa. Keterbacaan lintas proses
diserahkan ke LSPosed melalui meta-data `xposedsharedprefs=true`; hook membaca berkas itu dengan
`XSharedPreferences` dan menyegarkan tiap 3 detik agar sakelar langsung berlaku.

**Alasan.** `MODE_WORLD_READABLE` sudah ditolak framework sejak targetSdk 24, jadi memakainya berarti
selalu jatuh ke `SecurityException` — persis yang terjadi pada versi pertama modul ini: banner
"penyimpanan lintas proses tidak tersedia" muncul di setiap peluncuran meski LSPosed bekerja normal.
Mode privat + `xposedsharedprefs` adalah jalur yang memang dirancang LSPosed untuk kasus ini.

**Konsekuensi.** Bila LSPosed di perangkat tidak menangani `xposedsharedprefs`, hook tidak menemukan
konfigurasi. Karena itu `PinRuntime` menulis satu baris `[GeoPin] belum ada konfigurasi pin …` ke
logcat saat konfigurasi tidak terbaca, dan UI menandai modul belum aktif — bukan diam-diam.

## D-07 — Satu sampel per objek `Location`, dievaluasi ulang saat sakelar berubah

**Keputusan.** `LocationHook` menyimpan `IdentityHashMap<Location, LocationSample>`. Setiap kali getter
dipanggil, entri cache hanya dipakai bila `PinRuntime.isActive()` masih benar; bila pin dimatikan atau
paket dicabut dari allowlist, entri dibuang dan getter mengembalikan nilai asli.

**Alasan.** Tanpa pemeriksaan ulang, objek `Location` yang dipegang aplikasi (mis. `lastKnownLocation`)
akan terus mengembalikan titik palsu setelah sakelar dimatikan, karena cache tidak pernah menyentuh
`enabled()`/allowlist.

**Konsekuensi.** Cache tetap menjaga agar latitude, longitude, altitude, bearing, dan waktu pada satu
objek konsisten. Akses cache dan pembuatan sampel dibungkus satu blok `synchronized`, dan konfigurasi
dipublikasikan sebagai `Snapshot` immutable supaya tidak ada pembacaan campuran lama/baru.

## D-05 — `applicationId` dibaca dari `BuildConfig`

**Konteks.** Hook perlu tahu nama paket modul untuk menemukan berkas preferensi, tetapi
`applicationId` bisa diganti lewat properti Gradle.

**Keputusan.** Hook memakai `BuildConfig.APPLICATION_ID`. Jalur Gradle menghasilkannya otomatis; jalur
manual (`tools/build_apk.py`) menulis `BuildConfig.java` dengan nilai yang sama, dan manifest di-rename
dengan `aapt2 link --rename-manifest-package` agar paket APK = `applicationId` sementara kelas R tetap
di namespace `dev.geopin.app`.

## D-06 — Tahap "hanya source + tes" dilampaui

`STARTER_CONTRACT.md` menyatakan tahap saat itu berhenti di source + tes JVM dan tidak menghasilkan
APK. Permintaan terbaru adalah membangun aplikasinya, jadi APK debug kini dihasilkan dengan toolchain
manual. Kontrak tersebut dibiarkan sebagai catatan historis; dokumen ini mencatat penyimpangannya.
