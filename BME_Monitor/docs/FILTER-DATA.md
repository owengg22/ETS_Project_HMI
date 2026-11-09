# Filter data Project HMI

## File utama

- `app/src/main/java/id/ac/its/bmemonitor/SignalFilter.kt`: aturan penerimaan sampel HR dan SpO2.
- `LastValidValues.kt` pada direktori yang sama: menyimpan angka terakhir khusus untuk kartu tampilan.
- `ChartSeries.kt`: geometri garis penahan dan anchor batas jendela 60 detik, hanya untuk grafik. `ChartSeriesTest.kt` menguji garis mendatar, data kembali, pemisahan kanal, dan rerata yang tidak berubah.
- `MonitorViewModel.kt`: menerima MQTT, memanggil filter, lalu mengirim hasilnya ke grafik dan pencatat sesi.
- `app/src/test/java/id/ac/its/bmemonitor/SignalFilterTest.kt` dan `LastValidValuesTest.kt`: contoh input/output yang dapat dijalankan.

## Alur

ESP → MQTT JSON → Reading.parse → SignalFilter.apply → TrendPoint → grafik dan SessionAccumulator.
Dari TrendPoint yang sama, LastValidValues.accept hanya memperbarui kartu angka jika kanal terkait valid.
Firmware ESP tidak perlu diubah. Data mentah dan alasan penolakan tetap tersimpan pada rekaman Android.

## Aturan

1. Reading.parse memeriksa JSON, identitas perangkat, angka, finger, hr_valid dan spo2_valid. Nilai sentinel seperti -999 tidak menjadi nilai pengukuran.
2. BPM harus positif dan tidak lebih dari `MAX_BPM = 120`. Nilai 121/150/200 ditolak, bukan dipotong menjadi 120. Ini batas proyek yang dipilih pengguna, bukan pernyataan bahwa semua denyut >120 tidak mungkin terjadi.
3. Awal pembacaan perlu 3 sampel BPM konsisten. Dua sampel awal tidak dimasukkan kembali secara retroaktif ke grafik/rerata.
4. Baseline adalah median dari maksimal 5 BPM yang diterima. Perubahan lebih besar dari max(30 BPM, 35% median) dianggap calon lonjakan dan ditahan.
5. Perubahan dalam batas 120 baru diterima pada calon sampel ke-3 yang konsisten. Konsisten berarti beda antarcalon tidak lebih dari max(10 BPM, 10% calon sebelumnya). Dua calon yang sebelumnya ditolak tetap ditolak. Nilai >120 tidak pernah lolos walaupun berulang.
6. Kandidat terputus jika sampel hilang/tidak valid. Jari hilang, perangkat berubah, restart perangkat, atau jeda paket >5 detik mereset baseline; diperlukan warm-up lagi.
7. SpO2 diterima jika flag valid, jari terdeteksi, dan angka 0–100%. Tidak ada pengisian angka buatan atau pemaksaan ke rentang normal.
8. Kanal HR dan SpO2 independen: HR ditolak tidak berarti SpO2 valid ikut dibuang.

## Ketika data hilang

Kartu tetap menampilkan nilai terakhir dengan label `Terakhir · N dtk lalu`. Umurnya dihitung sejak sampel valid terakhir, bukan sejak paket invalid terbaru. Tanpa nilai valid sebelumnya, tetap ditampilkan `-`.
Sejak 1.4.2 grafik menahan nilai valid terakhir dengan garis mendatar putus-putus sampai pembacaan valid berikutnya. Perubahan setelah jeda digambar tepat pada waktu sampel baru, bukan mengarang perubahan selama jeda. Titik tetap hanya menandai sampel valid yang benar-benar masuk. Sebelum sampel valid pertama, grafik tetap kosong. Rerata tiap kanal hanya memakai sampel baru yang diterima; nilai yang ditahan di kartu tidak ditambahkan ke pencatat sesi. TTS hanya membacakan kanal valid dari paket terkini, bukan nilai kartu yang sudah lama.
Nilai kartu dihapus ketika pindah perangkat, perangkat restart, atau pengaturan koneksi diganti. Setelah koneksi putus, angka terakhir masih dapat terlihat tetapi selalu berlabel umur dan status koneksi.

## Contoh

| Input BPM | Hasil |
|---|---|
| Awal 80, 81, 82 | Sampel 80/81 warm-up; 82 diterima |
| Baseline 80, lalu 150 | 150 ditolak; kartu tetap angka valid sebelumnya |
| Baseline 80, lalu 120, lalu 81 | 120 ditahan sebagai lonjakan; 81 diterima |
| Baseline 60, lalu 100, 101, 102 | 100/101 ditahan; 102 diterima sebagai perubahan yang konsisten |
| 150, 151, 152 berulang | Semuanya ditolak oleh batas >120 |
| HR valid, SpO2 -999 | HR boleh diperbarui; kartu SpO2 menampilkan nilai valid sebelumnya |

## Pengujian

Dari direktori proyek jalankan `./Build.ps1`, atau `gradle testDebugUnitTest assembleDebug` dengan JDK/SDK proyek. Tidak diperlukan sensor untuk unit test. Verifikasi fisik tambahan: cabut jari, gerakkan jari, putuskan Wi-Fi, lalu periksa label umur, celah grafik dan hitungan sampel pada riwayat.

## Kontrol suara

`VoiceCommand.kt` berisi grammar tiga perintah: Mulai rekam, Stop rekam, Bacakan hasil. `VoiceController.kt` menangani mikrofon dan SpeechRecognizer sistem Android. Mikrofon aktif hanya setelah tombol Perintah suara ditekan, berhenti setelah hasil/error/timeout 12 detik, dan dibatalkan saat aplikasi ke latar belakang. Izin mikrofon diminta saat pertama dipakai. Hasil akhir harus cocok penuh; potongan kata, ucapan negatif atau gabungan perintah diabaikan.
Sejak versi 1.4.1, layanan pengenalan sistem HP digunakan tanpa memaksa mode offline. Tersedianya engine offline tidak menjamin model bahasa Indonesia sudah terpasang. Layanan sistem bisa membutuhkan internet/model bahasa. Aplikasi tidak menyimpan rekaman audio. Tombol sentuh tetap tersedia bila pengenalan suara gagal.
Referensi API: https://developer.android.com/reference/android/speech/SpeechRecognizer


## Grafik menahan nilai (1.4.2)

Grafik live mempertahankan satu nilai valid sebelum batas kiri untuk setiap kanal, sehingga jeda lebih dari 60 detik tidak membuat garis menghilang. Anchor dibuang saat perangkat berganti/restart atau konfigurasi koneksi berubah. Garis diperpanjang hanya sampai waktu aktual, bukan area sumbu waktu yang masih di masa depan. Riwayat menggunakan aturan yang sama sampai akhir sesi. Buffer live dibatasi 600 sampel dalam jendela ditambah maksimal 3 anchor. Rekaman mentah, sampel invalid, dan rerata tidak diubah.
