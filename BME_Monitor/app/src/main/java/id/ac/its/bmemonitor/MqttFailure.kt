package id.ac.its.bmemonitor

import org.eclipse.paho.client.mqttv3.MqttException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun mqttFailure(stage: String, cause: Throwable?): String {
 val chain = generateSequence(cause) { it.cause }.take(8).toList()
 val mqtt = chain.filterIsInstance<MqttException>().firstOrNull()
 val detail = when {
  mqtt?.reasonCode == 4 -> "Username atau password MQTT ditolak broker."
  mqtt?.reasonCode == 5 -> "Akun tidak diizinkan terhubung ke broker."
  mqtt?.reasonCode == 128 -> "Izin subscribe topic ditolak broker."
  chain.any { it is UnknownHostException } -> "DNS hostname broker tidak ditemukan. Periksa internet HP dan hostname."
  chain.any { it is SSLException } -> "TLS gagal. Periksa tanggal/jam HP dan sertifikat."
  chain.any { it is SocketTimeoutException } -> "Koneksi timeout. Periksa internet HP dan akses port broker."
  chain.any { it is ConnectException } -> "Port broker tidak dapat dijangkau dari jaringan HP."
  chain.any { it.javaClass.simpleName == "NetworkOnMainThreadException" } -> "Operasi jaringan terblokir di thread UI."
  else -> "Periksa koneksi internet HP dan konfigurasi MQTT."
 }
 val root = chain.lastOrNull()?.javaClass?.simpleName ?: "TanpaDetail"
 val code = mqtt?.reasonCode?.let { "; MQTT=$it" } ?: ""
 return "$stage gagal: $detail [$root$code]"
}
