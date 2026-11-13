# Verifikasi

Dokumen ini merangkum pemeriksaan yang tercatat untuk versi proyek ini. Hasil dapat berbeda menurut perangkat, versi Android, LSPosed, dan konfigurasi build.

## Pemeriksaan otomatis

| Area | Perintah | Cakupan |
| --- | --- | --- |
| Mesin koordinat dan konfigurasi | `python tools/test_core.py` | Unit test JVM untuk modul `core` |
| Pemuatan peta | `python tools/test_map.py` | Loader ubin dengan pengganti Android/HTTP untuk tes |
| Hook lokasi | `python tools/test_hook.py` | Cache hook dengan pengganti runtime Xposed |
| Paket APK | `python tools/verify_apk.py` | Pemeriksaan statis APK hasil build |

Pemeriksaan melalui test double bukan bukti bahwa semua API lokasi Android atau semua aplikasi target berperilaku sama.

## Pemeriksaan perangkat

Prosedur uji perangkat tersedia di [DEVICE_TEST.md](DEVICE_TEST.md). Pengujian memerlukan perangkat uji yang dikonfigurasi dengan root, Zygisk, dan LSPosed. Catat sendiri model perangkat dan versi perangkat lunak bila hasil pengujian itu akan dipublikasikan.

Keberhasilan memasang APK atau melihat log hook hanya membuktikan langkah tersebut. Untuk memvalidasi hasil, periksa nilai lokasi yang dibaca aplikasi target dan bandingkan dengan konfigurasi controller.

## Batasan yang diketahui

- Hook mengganti getter pada objek `android.location.Location`; aplikasi dapat memakai jalur API atau field lain.
- Modul tidak membuat callback lokasi sendiri. Jika provider tidak memberi pembaruan, modul tidak menghasilkan pembaruan baru.
- Belum ada klaim kompatibilitas universal, performa tertentu, atau ketahanan terhadap mekanisme deteksi aplikasi.
