# STM32 MAX30102 -> ESP32 -> HiveMQ

STM32: proyek `../test3` (STM32F401CCU6).

Wiring MAX30102 breakout 3V3: SCL->STM PB6, SDA->STM PB7, GND->GND, VCC->3V3 sesuai rating breakout.
STM PA9 TX->ESP GPIO16 RX2. STM PA10 RX<-ESP GPIO17 TX2 opsional.
GND STM dan ESP disatukan. UART 115200 8N1, logika 3,3 V.
Jika kedua board memakai sumber USB terpisah, jangan satukan rail 5 V.

ESP ini hanya publisher. Laptop/HP subscribe topic:
- owen/max30102/esp32-01/vitals
- owen/max30102/esp32-01/status

Subscribe HiveMQ Web Client pada cluster yang sama. Credentials ESP perlu izin
publish topic tersebut; laptop/HP perlu izin subscribe. Koneksi ESP TLS 8883.
VITALS JSON memakai QoS 0 tanpa retain. Status online/offline retained.
HR/SpO2 hanya valid bila finger=true dan hr_valid/spo2_valid=true.
RED/IR adalah ADC mentah. Belum ada kalibrasi akurasi pengukuran.
Jika tidak ada vitals baru >5 detik, subscriber harus menyembunyikan angka lama.
Status online hanya menyatakan koneksi ESP, bukan sensor hidup.
Buffer UART dibuang setelah upaya reconnect agar pembacaan lama tidak dipublish.

Salin `config.example.h` ke `config.h`, lalu isi Wi-Fi dan kredensial HiveMQ.
Jangan bagikan config.h. Firmware test3 STM32 tidak perlu diubah jika sudah terpasang.
Aktifkan hotspot dan internet. Bila STM ditahan debugger, Resume/F8.

```powershell
pio device list
pio run -t upload --upload-port COM18
pio device monitor --port COM18 -b 115200
```
Ganti COM18 sesuai port aktual. Ctrl+C menutup monitor sebelum upload.
Upload ini hanya untuk ESP32. Untuk STM32 buka proyek test3 di STM32CubeIDE.

Log ESP: DATA,... membuktikan UART menerima data. MQTT_CONNECTED menunjukkan
koneksi broker. Tidak ada DATA: cek kabel, GND, baud dan STM tidak halt.

Verifikasi: kesesuaian format DATA dan wiring dibandingkan dengan test3.
Belum terverifikasi kompilasi/hardware; compiler lokal gagal dijalankan oleh
sesi Codex dengan Illegal System DLL Relocation. Tidak mengubah instalasi compiler.
