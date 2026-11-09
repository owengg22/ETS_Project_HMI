package id.ac.its.bmemonitor

data class BrokerConfig(
 val host: String = "",
 val port: String = "8883", val username: String = "", val password: String = "",
 val topic: String = "owen/max30102/esp32-01/vitals",
 val statusTopic: String = "owen/max30102/esp32-01/status",
 val name: String = "", val email: String = ""
) {
 fun validate(): String? = when {
  !host.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")) -> "Isi hostname tanpa https:// atau nomor port."
  port.toIntOrNull()?.let { it in 1..65535 } != true -> "Port harus 1â€“65535."
  username.isBlank() || password.isEmpty() -> "Isi username dan password MQTT."
  listOf(topic, statusTopic).any { it.isBlank() || it.length > 256 || it.any { c -> c in "#+" || c.isWhitespace() || c == '\u0000' } } -> "Gunakan topic lengkap tanpa wildcard atau spasi."
  topic == statusTopic -> "Topic vitals dan status harus berbeda."
  else -> null
 }
}
