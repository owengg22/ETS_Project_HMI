package id.ac.its.bmemonitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(context: Context) {
 private val prefs = context.getSharedPreferences("bme_monitor", Context.MODE_PRIVATE)
 private val alias = "bme_mqtt_password"
 private val assets = context.assets
 private val sessionsDir = File(context.filesDir, "sessions").apply { mkdirs() }
 var bootstrapError: String? = null
  private set
 private fun key(): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias, null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 private fun encode(value: String): String {
  val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
  return Base64.encodeToString(c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
 }
 private fun password(): String = try {
  val text = prefs.getString("password", null)
  if (text == null) "" else {
   val b = Base64.decode(text, Base64.NO_WRAP)
   val c = Cipher.getInstance("AES/GCM/NoPadding")
   c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
   String(c.doFinal(b.copyOfRange(12, b.size)), Charsets.UTF_8)
  }
 } catch (_: Exception) { "" }
 fun config(): BrokerConfig {
  val d = BrokerConfig()
  fun get(k: String, default: String) = prefs.getString(k, default) ?: default
  val current = d.copy(host = get("host", d.host), port = get("port", d.port), username = get("username", d.username),
   password = password(), topic = get("topic", d.topic), statusTopic = get("statusTopic", d.statusTopic),
   name = get("name", d.name), email = get("email", d.email))
  val text = try { assets.open("mqtt_defaults.json").bufferedReader(Charsets.UTF_8).use { it.readText() } }
   catch (_: java.io.FileNotFoundException) { return current }
  return try {
   val hash = CredentialDefaults.fingerprint(text)
   if (prefs.getString("bootstrapHash", null) == hash) current else {
    val seeded = CredentialDefaults.apply(text, current)
    save(seeded)
    check(prefs.edit().putString("bootstrapHash", hash).commit())
    seeded
   }
  } catch (e: Exception) {
   bootstrapError = "Gagal memuat konfigurasi MQTT bawaan [${e.javaClass.simpleName}]."
   current
  }
 }
 fun save(c: BrokerConfig) {
  val encrypted = encode(c.password)
  check(prefs.edit().putString("host", c.host).putString("port", c.port).putString("username", c.username)
   .putString("password", encrypted).putString("topic", c.topic).putString("statusTopic", c.statusTopic)
   .putString("name", c.name).putString("email", c.email).commit())
 }
 fun history(): List<Snapshot> = try {
  val a = JSONArray(prefs.getString("history", "[]"))
  (0 until a.length()).map { i ->
   val o = a.getJSONObject(i)
   Snapshot(o.getLong("time"), if (o.isNull("hr")) null else o.getDouble("hr"),
    if (o.isNull("spo2")) null else o.getDouble("spo2"), o.getString("device"), o.optString("name"))
  }
 } catch (_: Exception) { emptyList() }
 fun saveHistory(items: List<Snapshot>) {
  val a = JSONArray()
  items.take(200).forEach { s -> a.put(JSONObject().put("time", s.time).put("hr", s.hr ?: JSONObject.NULL)
   .put("spo2", s.spo2 ?: JSONObject.NULL).put("device", s.device).put("name", s.name)) }
  check(prefs.edit().putString("history", a.toString()).commit())
 }
 fun audioEnabled(): Boolean = prefs.getBoolean("audioEnabled", true)
 fun saveAudioEnabled(enabled: Boolean) { prefs.edit().putBoolean("audioEnabled", enabled).apply() }
 fun sessions(): List<SessionSummary> {
  val text = prefs.getString("sessions_v1", "[]") ?: "[]"
  val a = JSONArray(text)
  return (0 until a.length()).map { SessionCodec.summary(a.getJSONObject(it)) }
 }
 @Synchronized fun saveSession(record: SessionRecord): List<SessionSummary> {
  UUID.fromString(record.summary.id)
  val previous = sessions()
  val atomic = AtomicFile(File(sessionsDir, record.summary.id + ".json"))
  val stream = atomic.startWrite()
  try { stream.write(SessionCodec.encode(record).toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
  catch (e: Exception) { atomic.failWrite(stream); throw e }
  val all = (listOf(record.summary) + previous).distinctBy { it.id }
  val kept = all
  val a = JSONArray(); kept.forEach { a.put(SessionCodec.summary(it)) }
  check(prefs.edit().putString("sessions_v1", a.toString()).commit())
  return kept
 }
 fun session(id: String): SessionRecord {
  UUID.fromString(id)
  val file = AtomicFile(File(sessionsDir, id + ".json"))
  return SessionCodec.decode(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
 }

}
