package id.ac.its.bmemonitor

import org.json.JSONObject
import java.security.MessageDigest

object CredentialDefaults {
 fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
  .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
 fun apply(text: String, current: BrokerConfig): BrokerConfig {
  val j = JSONObject(text)
  fun field(key: String) = j.get(key) as? String ?: error("Konfigurasi bukan string: $key")
  val c = current.copy(host = field("host"), port = field("port"), username = field("username"),
   password = field("password"), topic = field("topic"), statusTopic = field("statusTopic"))
  require(c.validate() == null) { "Konfigurasi MQTT bawaan tidak valid" }
  return c
 }
}
