# Uji perangkat

Panduan singkat untuk `tools/device_check.ps1` pada PC Windows dengan adb. Pengujian membutuhkan
perangkat Android dengan root, Zygisk, dan LSPosed.

Skrip hanya mengotomasi langkah adb. Skrip tidak dapat mengaktifkan modul atau mengubah scope di
LSPosed Manager, dan tidak membuktikan hook bekerja. PASS berarti langkah adb yang dijalankan
teramati berhasil, bukan bahwa lokasi palsu sudah benar.

## Prasyarat

- Windows PowerShell 5.1.
- Android Platform Tools. Skrip mencari `adb` di PATH, `$env:ADB`, `ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, dan lokasi SDK umum.
- USB debugging aktif dan prompt RSA di perangkat sudah disetujui.
- Kedua APK ada di `dist\`:
  `GeoPinPrivate-0.1.0-debug.apk` (`dev.geopin.privatepin`) dan
  `GeoPinProbe-0.1.0-debug.apk` (`dev.geopin.probe`).
- LSPosed Manager tersedia; aktivasi modul dan scope dilakukan manual setelah instalasi.

## Menjalankan

Dari root repositori:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\device_check.ps1 -WhatIfOnly
powershell -NoProfile -ExecutionPolicy Bypass -File tools\device_check.ps1
```

Dry-run memeriksa adb, perangkat, APK, dan hash tanpa instalasi, logcat, atau screenshot. Dry-run
selalu keluar dengan kode 0 bila skrip selesai, tetapi ringkasannya tetap menampilkan prasyarat yang
gagal dan tidak pernah menyebut PASS.

Opsi:

| Opsi | Fungsi |
| --- | --- |
| `-WhatIfOnly` | Hanya periksa prasyarat dan cetak rencana. |
| `-Serial <serial>` | Pilih perangkat bila ada lebih dari satu. |
| `-AdbPath <file>` | Pakai `adb.exe` tertentu; menimpa PATH dan `$env:ADB`. |
| `-SkipLogcat` | Lewati perekaman logcat. |
| `-ClearLogcat` | Kosongkan buffer logcat sebelum merekam. |
| `-LogSeconds <5-600>` | Lama jendela logcat; default 60 detik. |

Contoh:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\device_check.ps1 -ClearLogcat -LogSeconds 90
```

## Yang dilakukan skrip

1. Mencari adb, mencetak versi, dan menjalankan `adb devices -l`. Skrip berhenti dengan pesan yang
   jelas bila tidak ada perangkat berstatus `device`.
2. Memeriksa kedua APK, mencetak ukuran dan SHA-256 aktual.
3. Memasang keduanya dengan `adb install -r`. Output adb dicetak apa adanya; PASS instalasi hanya
   diberikan bila adb melaporkan `Success`.
4. Meluncurkan `dev.geopin.privatepin/dev.geopin.app.ui.MainActivity` dan memeriksa PID controller.
5. Merekam logcat selama jendela yang dipilih, memfilter baris yang mengandung `GeoPin`, lalu
   menulisnya ke `dist\logcat-geopin.txt`.
6. Meluncurkan controller lagi dan mengambil `adb exec-out screencap -p` ke
   `dist\screenshot-controller.png`.
7. Mencetak ringkasan PASS/FAIL dan daftar langkah manual yang tersisa.

Kode keluar: `0` untuk PASS atau dry-run, `1` bila ada langkah gagal, `2` bila prasyarat
(adb, perangkat, atau APK) tidak terpenuhi. Skrip tidak melempar stack trace untuk kondisi tersebut.

## Langkah manual

Lakukan setelah kedua APK terpasang. Modul hanya menyuntik paket yang **di-scope di LSPosed dan**
tercantum di allowlist controller.

1. Buka **LSPosed Manager > Modules**. Aktifkan modul **Calibrator**
   (`dev.geopin.privatepin`). Bila modul belum muncul, reboot perangkat.
2. Buka **Scope** untuk modul tersebut dan centang `dev.geopin.probe`. Centang
   `dev.geopin.privatepin` hanya bila ingin indikator status di controller hidup; controller sendiri
   tidak pernah menerima lokasi palsu.
3. Buka **Calibrator**. Isi **Paket yang diizinkan** dengan `dev.geopin.probe` (nilai default),
   simpan, isi koordinat uji, lalu nyalakan titik diam.
4. Setelah mengubah scope, paksa hentikan **GeoPin Probe**, buka lagi, beri izin lokasi, lalu tekan
   **Mulai pantau**.
5. Periksa provider, latitude, longitude, accuracy, altitude, speed, bearing, mock, dan time di layar
   Probe. Nilai harus bisa dibandingkan dengan titik yang di-set di Calibrator.

## Logcat dan batas bukti

Baris hook diawali `[GeoPin]` dan ditulis lewat XposedBridge; tag logcat biasanya `LSPosed`. Skrip
memfilter isi baris, bukan nama tag.

- Baris `[GeoPin] hook lokasi terpasang di dev.geopin.probe` hanya menunjukkan kode hook dipanggil.
  Baris itu tidak membuktikan koordinat, akurasi, atau perilaku aplikasi lain benar.
- Tidak ada baris `GeoPin` bukan bukti hook rusak. Penyebab yang umum: paket belum di-scope, belum
  ada di allowlist, proses target belum dimuat ulang, buffer logcat baru dibersihkan, atau pin belum
  dinyalakan.
- `-ClearLogcat` menghapus jejak startup lama. Setelah memakainya, paksa hentikan dan buka lagi
  Probe selama jendela perekaman agar baris pemasangan hook muncul.
- Screenshot hanya membuktikan file PNG berhasil diambil; isi layar tidak diverifikasi.

## Jika gagal

| Gejala | Tindakan |
| --- | --- |
| `ADB tidak ditemukan` | Pasang Platform Tools, tambahkan ke PATH, atau set `$env:ADB`. |
| `unauthorized` atau `offline` | Setujui prompt RSA, cek kabel/data, jalankan `adb kill-server` lalu `adb devices`. |
| Instalasi gagal | Baca output adb. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` berarti tanda tangan APK berbeda; uninstall versi lama (data hilang) lalu ulangi. |
| Tidak ada baris `GeoPin` | Pastikan modul aktif, scope dan allowlist benar, lalu paksa hentikan Probe sebelum menguji. |
| Screenshot bukan PNG | Baca pesan adb; pastikan perangkat terbuka dan `adb exec-out` didukung. |
| APK tidak ditemukan | Bangun ulang: `python tools/build_apk.py --module all`. |
