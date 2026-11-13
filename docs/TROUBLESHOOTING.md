# Troubleshooting

Panduan gejala, sebab, dan tindakan berdasarkan kode di repo ini. Tidak ada klaim hasil uji perangkat.

## 1. Modul belum aktif

- Gejala: kartu "Status modul" menampilkan "Modul belum aktif di LSPosed" dengan warna merah, dan teks
  "Modul belum aktif di proses ini. Aktifkan modul di LSPosed Manager, lalu centang juga Calibrator
  agar status ini terbaca." muncul di bawah kartu status.
- Sebab: `ModuleStatus.isActive()` tidak menemukan `de.robv.android.xposed.XposedBridge` atau
  `getXposedVersion()` tidak mengembalikan angka di atas 0. Kelas itu hanya ada di proses yang
  di-scope, jadi biasanya controller belum dicentang di LSPosed Manager.
- Tindakan: buka LSPosed Manager, pilih Modules, pilih Calibrator, aktifkan modul, lalu centang juga
  Calibrator sendiri. Hentikan paksa Calibrator dan buka lagi; status dibaca ulang di `onResume()`.

## 2. Koordinat tidak berubah di aplikasi target

- Gejala: aplikasi target tetap memakai lokasi asli, atau nilainya tidak bergerak setelah sakelar
  ditekan.
- Sebab: paket sudah di-scope di LSPosed tetapi belum ada di allowlist controller (default hanya
  `dev.geopin.probe`); teks allowlist hanya tersimpan saat "Simpan Cakupan" ditekan; proses target
  sudah berjalan sebelum modul atau scope diaktifkan sehingga hook tidak pernah terpasang; pin masih
  nonaktif atau config kosong sehingga `createSample()` mengembalikan `null`; objek `Location` yang
  sudah punya sampel tetap memakai sampel itu selama titik aktif, termasuk setelah pusat diganti.
- Tindakan:
  - Isi kolom "Paket yang diizinkan" dengan package name target, lalu tekan "Simpan Cakupan".
  - Hentikan paksa aplikasi target, buka lagi, lalu baca lokasi.
  - Pastikan status "Titik diam AKTIF"; bila belum, tekan "Aktifkan Titik".
  - Periksa log `[GeoPin]` untuk memastikan hook terpasang di proses itu.

## 3. Hook tidak menemukan konfigurasi

- Gejala: log menampilkan `[GeoPin] belum ada konfigurasi pin di <applicationId>; modul menunggu`, dan
  aplikasi target tetap memakai lokasi asli walau "Status modul" sudah aktif.
- Sebab: konfigurasi belum pernah disimpan (membuka controller tidak menulis konfigurasi pin; ia hanya
  mengisi allowlist default bila kolomnya masih kosong), atau LSPosed di perangkat tidak menangani
  meta-data `xposedsharedprefs` sehingga `XSharedPreferences` tidak melihat berkas `geopin_pin`, atau
  paket modul yang di-scope berbeda dari `applicationId` build.
- Tindakan: simpan konfigurasi lewat "Terapkan" atau "Aktifkan Titik"; pastikan paket yang di-scope
  sama dengan `applicationId` (`dev.geopin.privatepin` pada build default); hentikan paksa target dan
  buka lagi; bila perlu periksa `shared_prefs/geopin_pin.xml` di data aplikasi.
- Catatan: versi awal memakai `MODE_WORLD_READABLE` dan menampilkan banner penyimpanan lintas proses.
  Mode itu selalu ditolak sejak `targetSdk 24`, sehingga jalurnya dihapus dan banner tersebut tidak
  akan muncul lagi. Kegagalan yang tersisa terlihat sebagai baris log di atas.

## 4. Perubahan baru berlaku sampai sekitar 3 detik

- Gejala: sakelar, koordinat, atau allowlist baru terasa setelah jeda, kadang lebih lama.
- Sebab: `PinRuntime.RELOAD_INTERVAL_MS` bernilai 3000 dan `refreshIfStale()` hanya dipanggil dari
  `createSample()` atau `isActive()`, yaitu saat getter lokasi dipanggil. Tidak ada timer atau service.
- Tindakan: tunggu hingga 3 detik sambil memicu pembacaan lokasi di aplikasi target. Bila tidak ada
  perubahan, periksa log untuk baris "gagal menyegarkan preferensi" atau "konfigurasi pin rusak",
  lalu pastikan scope dan allowlist benar. Perubahan scope LSPosed tidak tercakup jendela ini.

## 5. Aplikasi target masih melihat GPS asli

- Gejala: probe menampilkan koordinat palsu, aplikasi lain tidak.
- Sebab:
  - Aplikasi sudah membaca nilai lokasi sebelum hook aktif lalu menyimpan angkanya sendiri. Hook
    mengganti getter, jadi nilai yang sudah tersalin tetap asli; objek `Location` lama yang getter-nya
    dipanggil setelah hook aktif tetap mengikuti titik palsu.
  - Kode memakai field internal, bukan getter: `Location.distanceTo()`, salinan `Parcel`,
    `toString()`, atau akses field langsung. Ini batasan yang didokumentasikan, bukan regresi.
  - Proses termasuk yang dilindungi: `com.google.android.gms`, `com.google.android.gms.persistent`,
    `com.google.android.gsf`, `android`, `com.android.systemui`. Allowlist tidak bisa membukanya.
- Tindakan: bandingkan dengan probe, hentikan paksa aplikasi target, dan untuk aplikasi yang membaca
  lewat GMS atau field internal, batasan ini tidak bisa diatasi oleh desain hook getter.

## 6. Ubin peta kosong

- Gejala: area peta abu-abu atau tidak ada gambar, pin tetap bisa ditaruh.
- Sebab: `TileStore` hanya menyentuh cache memori di `peek()`; disk dan jaringan dimuat di thread
  pekerja dari `https://tile.openstreetmap.org/{z}/{x}/{y}.png` dengan batas koneksi 10 detik dan baca
  15 detik. Hanya ubin yang terlihat diminta, cache disk disimpan per tingkat zoom di
  `cacheDir/osm/<zoom>/<x>/<y>.png`, dan ubin di atas 512 KB ditolak. Tanpa internet, server tidak
  terjangkau, atau koneksi diblokir, ubin tidak pernah tiba.
- Tindakan: periksa koneksi, DNS, atau Private DNS, coba zoom lain sambil menunggu, lalu bersihkan
  cache aplikasi lewat pengaturan sistem bila cache diduga rusak. Pembukaan pertama memang butuh
  internet.

## 7. Probe menampilkan mock true atau koordinat asli

- Gejala: baris `mock` bernilai `true`, atau latitude/longitude tidak mengikuti titik yang di-set.
- Sebab: probe belum di-scope di LSPosed; probe belum ada di allowlist; allowlist sudah diedit tetapi
  belum ditekan "Simpan Cakupan"; pin nonaktif; atau izin lokasi probe ditolak sehingga tidak ada
  pembacaan sama sekali.
- Tindakan: centang `dev.geopin.probe` di LSPosed, tambahkan ke "Paket yang diizinkan" dan simpan,
  aktifkan titik, lalu beri izin lokasi. Gunakan "Mulai pantau" untuk aliran pembaruan berkelanjutan;
  "Baca lokasi terakhir" hanya membaca satu nilai tersimpan.

## 8. Di mana harus melihat log

- Perintah: `adb logcat | findstr GeoPin` di Windows, atau `adb logcat | grep GeoPin` di Unix.
- Baris dari `HookEntry`: `[GeoPin] gagal memasang hook di <paket>: <error>`.
- Baris dari `LocationHook`: `[GeoPin] hook lokasi terpasang di <paket>`,
  `[GeoPin] hook gagal di <paket>: <error>`, `[GeoPin] metode <nama> tidak tersedia: <pesan>`, dan
  `[GeoPin] isMock tidak tersedia (normal di API 30)`.
- Baris dari `PinRuntime`: `[GeoPin] preferensi modul tidak terbaca: <error>`,
  `[GeoPin] belum ada konfigurasi pin di <applicationId>; modul menunggu`,
  `[GeoPin] konfigurasi pin rusak: <pesan>`, dan `[GeoPin] gagal menyegarkan preferensi: <error>`.
- `XposedBridge.log` juga masuk ke log LSPosed. Penerusan ke logcat bergantung pada versi LSPosed dan
  ROM, jadi tidak adanya baris bukan bukti mutlak bahwa modul tidak dimuat. Adanya baris "hook lokasi
  terpasang" adalah bukti langsung untuk proses tersebut.
