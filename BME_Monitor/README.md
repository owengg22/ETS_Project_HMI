# Project HMI 1.4.3

Project Android Studio native: Kotlin + Jetpack Compose. Package `id.ac.its.bmemonitor`, Android 9/API 28 ke atas. Source ini tidak mengubah firmware STM32/ESP32.

## Jalankan

1. Buka folder `BME_Monitor` melalui Android Studio -> Open.
2. Gunakan Gradle JDK 17 atau 21 dan Android SDK 35; Android Studio membuat `local.properties` lokal.
3. Build APK debug melalui Android Studio atau jalankan `./gradlew.bat assembleDebug` dari folder proyek.
4. Isi hostname HiveMQ, port 8883, username, password, dan topic di Pengaturan aplikasi.
5. Tekan **Mulai rekam**, lalu **Stop & simpan**. Buka **Riwayat**, ketuk sesi untuk melihat rata-rata dan grafiknya.

```powershell
# Dari folder BME_Monitor
.\Build.ps1
.\Install-Phone.ps1
# Atau build dan install:
.\Build.ps1 -Install
```

Kredensial diisi di Pengaturan. File opsional `app/src/main/assets/mqtt_defaults.json` tetap diabaikan oleh Git; APK yang dibangun dengan file itu jangan dibagikan.

## Tampilan dan grafik

- Angka dan grafik tampil otomatis, tanpa menekan Mulai.
- BPM dan SpO2 memakai grafik terpisah dengan satuan jelas dan sumbu X detik. Live menampilkan jendela 60 detik, grafik sesi memakai detik sejak Mulai.
- Grafik berupa tren hasil sensor, bukan waveform PPG atau ECG. Firmware mengirim hasil sekitar satu kali per detik.
- Sumbu Y mulai nol; BPM menyesuaikan agar nilai tinggi tidak terpotong. SpO2 memakai 0-100 persen.
- Sampel kosong/disisihkan memutus garis. Jeda di atas 2,5 detik juga memutus garis. Ketuk grafik untuk melihat nilai/waktu sampel.
- Pita 60-100 BPM merupakan acuan istirahat kebanyakan orang dewasa, bukan batas maksimum. Sumber: [American Heart Association](https://www.heart.org/en/healthy-living/exercise-and-physical-activity/fitness-basics/target-heart-rates).
- Nilai BPM >120 yang diterima filter tetap ditampilkan dengan teks penanda dan simbol segitiga, tanpa diubah menjadi 120.
- Logo pembuka hanya BME, dengan kilau/fade singkat memakai animasi Compose. Tema terang/gelap mengikuti sistem. Logo Direktorat Kemahasiswaan dihapus dari resource aplikasi.

## Filter transparan

1. Parse tipe data sesuai kontrak integer firmware; cek finger dan flag valid per parameter. Nilai SpO2 di luar 0-100 atau HR nonpositif tidak ditampilkan sebagai valid.
2. BPM membutuhkan tiga sampel awal yang konsisten. Konsistensi: perubahan antarsampel maksimal max(10 BPM, 10 persen nilai sebelumnya).
3. Baseline memakai median lima sampel yang diterima. Perubahan melebihi max(30 BPM, 35 persen median) ditahan sebagai lonjakan belum terkonfirmasi.
4. Tiga kandidat baru yang konsisten mengonfirmasi perubahan; sampel ketiga diterima dan baseline diperbarui. Dua kandidat sebelumnya tetap tercatat sebagai disisihkan, tanpa menulis ulang riwayat.
5. Jari dilepas, jeda lebih dari 5 detik, perubahan perangkat, atau reconnect mereset filter. Data basi tidak ditampilkan setelah 5 detik.

Filter ini heuristik, bukan indikator kualitas PPG yang tervalidasi klinis. Nilai 200 yang stabil dapat tetap diterima dan ditandai. Masalah algoritme/timing sensor tetap perlu verifikasi dengan sinyal mentah dan alat pembanding; filter aplikasi tidak membuktikan denyut sebenarnya.

## Rekaman dan riwayat

- Mulai/Stop mengatur rekaman, bukan subscription MQTT atau sampling ESP32.
- Hanya frame baru setelah Mulai yang masuk sesi. Nilai mentah, nilai diterima, alasan penyisihan, serta waktu relatif tiap sampel disimpan.
- Rata-rata aritmetika dihitung terpisah: jumlah nilai diterima dibagi jumlah sampel diterima pada parameter tersebut. Invalid/pending tidak diganti nol dan tidak masuk rerata.
- Detail sesi menampilkan rata-rata HR/SpO2, durasi, grafik, jumlah diterima/disisihkan per parameter, dan jumlah BPM >120 yang diterima.
- Tombol sampel mentah menampilkan 30 baris terakhir; seluruh sampel tetap disimpan. Riwayat memuat 50 sesi per tampilan dengan tombol untuk memuat sesi berikutnya. Tidak ada penghapusan otomatis sesi lama.
- Maksimal 3600 frame per sesi (sekitar satu jam pada 1 Hz), lalu sesi disimpan otomatis agar memori terbatas. Live tetap berjalan.
- Rotasi layar mempertahankan rekaman. Saat aplikasi benar-benar masuk latar belakang, sesi dihentikan/disimpan dan MQTT ditutup; ketika kembali, live reconnect otomatis. Ini bukan layanan rekaman background.
- Penyimpanan file sesi atomik di penyimpanan internal aplikasi. Kegagalan simpan menyediakan tombol Ulangi simpan. Snapshot versi lama tetap dipertahankan.

## Audio

- Earcon singkat untuk broker tersambung/terputus dan aksi rekaman/simpan. Tidak berbunyi setiap frame atau setiap retry.
- **Bacakan hasil** menggunakan TTS Indonesia offline dari HP, termasuk penanda jika BPM >120. Memerlukan engine dan paket suara Indonesia offline yang terpasang. Aplikasi mendukung kode locale id/in.
- Data tidak valid/basi tidak dibacakan sebagai angka. Audio dihentikan ketika kehilangan data valid, saat Stop, masuk background, atau suara dimatikan.
- Switch **Suara aplikasi** menonaktifkan bunyi dan pembacaan. Volume mengikuti volume media HP. Audio focus dan resource TTS/ToneGenerator dibersihkan saat lifecycle berakhir.
- Tidak memakai mikrofon, pengenalan suara online, atau mengirim suara/data ke layanan TTS cloud. Sonifikasi denyut kontinu belum termasuk versi ini.

## MQTT

- HiveMQ Cloud TLS 8883, MQTT 3.1.1, verifikasi hostname/sertifikat tetap aktif.
- Vitals: owen/max30102/esp32-01/vitals; status: owen/max30102/esp32-01/status.
- Subscription kedua topic aktif setiap tersambung. Subscribe QoS 1; publish vitals firmware QoS 0 sehingga pengiriman efektif QoS 0.
- Retained vitals diabaikan; retained status diperbolehkan. Duplikat seq/uptime/device yang sama diabaikan.
- Error DNS, autentikasi, izin subscribe, timeout, dan TLS dibedakan tanpa menampilkan password atau pesan exception mentah.

## Verifikasi 1.2 - 26 Oktober 2026

- testDebugUnitTest + assembleDebug: BUILD SUCCESSFUL, 24 test, 0 failures/errors/skipped.
- Test mencakup parser, integer bounds, freshness, live sebelum rekam, warmup, spike, perubahan stabil >120 tanpa cap, reset, rerata independen tanpa nol, codec raw/reason/timing, batas sesi, serta teks TTS yang tidak membaca invalid/stale.
- Source yang dibuild di salinan kerja dibandingkan SHA-256 dengan app/src di project HMI: cocok. Build stage menghindari cache Windows yang terkunci, memakai in-process compiler; tidak menghapus cache/source lama.
- Tanda tangan APK diverifikasi, signer sama dengan 1.1, kredensial paket cocok, dan resource Direktorat tidak ada.
- Delapan pasangan warna teks/status terang/gelap diperiksa matematis; minimum kontras 5,36:1.
- Uji visual di HP, rotasi/ukuran font besar/reduced motion, audio fisik, dan penyimpanan Android belum dapat diuji di perangkat dari sesi ini. Jangan menyamakan compile/unit test dengan uji fisik.

Cek di HP: buka tanpa Mulai -> live/grafik masuk; mulai rekam -> tunggu -> Stop -> riwayat/detail; uji jari dilepas, koneksi terputus, raw spike dan nilai tinggi stabil; uji Bacakan hasil, mute, rotasi, background, font besar dan animasi dimatikan.

## Perapian UI 1.3

Grafik diringkas menjadi judul parameter, satuan, Waktu (s), dan garis/titik. Pita acuan dan narasi panjang di layar grafik dihapus. Detail sampel/rerata tetap tersedia sesuai kebutuhan. Angka live berdampingan pada HP yang cukup lebar; tombol rekam/stop menyatu. Pembuka kartu custom diganti splash native Android 12+ dengan fade singkat, tanpa jeda buatan. Android 9-11 menampilkan background ringan dan fade konten.

Build 1.3 + 24 test lulus; signer sama dengan 1.2. Pemeriksaan visual/transisi pada HP belum dilakukan dari sesi ini. Kebijakan filter, data mentah, riwayat, dan koneksi MQTT tetap berlaku.


## Versi 1.3.1

Logo splash memakai PNG BME baru dengan proporsi asli, ruang aman agar tidak terpotong, serta lingkaran putih untuk kontras pada tema gelap. Build dan 27 unit test lulus; tampilan fisik pada HP belum diuji dari sesi ini.


## Versi 1.4

Aturan filter terpusat di `app/src/main/java/id/ac/its/bmemonitor/SignalFilter.kt`. Panduan untuk pengumpulan dan contoh kasus: [docs/FILTER-DATA.md](docs/FILTER-DATA.md). Batas proyek BPM >120 ditolak; kartu angka terakhir memiliki label umur dan tidak menjadi sampel grafik/rerata. Kontrol suara: Mulai rekam, Stop rekam, Bacakan hasil.
