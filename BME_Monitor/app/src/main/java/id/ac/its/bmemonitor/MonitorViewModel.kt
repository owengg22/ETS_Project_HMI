package id.ac.its.bmemonitor

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocketFactory

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
 private val store = LocalStore(app)
 private val initialConfig = store.config()
 private val initialSessions = runCatching { store.sessions() }
 private val mutable = MutableStateFlow(MonitorState(config = initialConfig, history = store.history(),
  sessions = initialSessions.getOrDefault(emptyList()), audioEnabled = store.audioEnabled(),
  lastError = store.bootstrapError ?: initialSessions.exceptionOrNull()?.let { "Indeks riwayat tidak terbaca; rekaman lama tetap disimpan." },
  message = "Data live diterima otomatis saat broker tersambung."))
 val state = mutable.asStateFlow()
 private val soundChannel = Channel<SoundEvent>(Channel.BUFFERED)
 val sounds = soundChannel.receiveAsFlow()
 private val io = Executors.newSingleThreadExecutor()
 private val main = Handler(Looper.getMainLooper())
 private val filter = SignalFilter()
 private var recorder: SessionAccumulator? = null
 private var pending: SessionRecord? = null
 private var cleared = false
 private var selectionGeneration = 0
 private var client: MqttAsyncClient? = null
 private var connecting = false
 private var foreground = false
 private var lastAttempt = -10000L
 private var savingConfig = false
 private var heldDevice: String? = null
 private var heldUptime: Long? = null
 init {
  viewModelScope.launch {
   while (true) {
    val now = SystemClock.elapsedRealtime()
    mutable.update { it.copy(nowElapsed = now, trend = trimChartPoints(it.trend, now)) }
    if (foreground && !connecting && client?.isConnected != true && now - lastAttempt >= 5000 && state.value.config.validate() == null) connect()
    delay(500)
   }
  }
 }
 private fun sound(kind: String) { if (state.value.audioEnabled) soundChannel.trySend(SoundEvent(kind)) }
 fun setAudioEnabled(enabled: Boolean) { store.saveAudioEnabled(enabled); mutable.update { it.copy(audioEnabled = enabled) } }
 fun setForeground(active: Boolean, stopRecording: Boolean = true) {
  foreground = active
  if (!active) { if (stopRecording && recorder != null) stopAcquisition("Aplikasi masuk latar belakang"); dispose() }
  else { lastAttempt = -10000; if (pending != null && !state.value.savingSession) retrySave() }
 }
 fun startAcquisition() {
  if (recorder != null || pending != null || state.value.savingSession) return
  if (!state.value.connected) { mutable.update { it.copy(message = "Tunggu broker terhubung sebelum merekam sesi.") }; return }
  val now = SystemClock.elapsedRealtime()
  recorder = SessionAccumulator(System.currentTimeMillis(), now, state.value.config.name)
  mutable.update { it.copy(acquiring = true, recordingStarted = now, recordingSamples = 0, message = "Merekam sesi. Data live tetap diperbarui.") }
  sound("started")
 }
 fun stopAcquisition(reason: String = "Stop") {
  val active = recorder ?: return
  recorder = null
  pending = active.finish(System.currentTimeMillis(), SystemClock.elapsedRealtime())
  mutable.update { it.copy(acquiring = false, recordingStarted = null, unsavedSession = true, message = "Sesi berakhir ($reason). Menyimpan rekaman...") }
  sound("stopped"); retrySave()
 }
 fun retrySave() {
  val record = pending ?: return
  if (state.value.savingSession) return
  mutable.update { it.copy(savingSession = true) }
  io.execute {
   val result = runCatching { store.saveSession(record) }
   main.post {
    if (!cleared) result.fold(onSuccess = { list ->
     pending = null
     mutable.update { it.copy(sessions = list, savingSession = false, unsavedSession = false, message = "Sesi tersimpan. Buka Riwayat untuk grafik dan rata-rata.") }
     sound("saved")
    }, onFailure = {
     mutable.update { it.copy(savingSession = false, unsavedSession = true, message = "Sesi belum tersimpan. Tekan Ulangi simpan; jangan tutup aplikasi.") }
     sound("error")
    })
   }
  }
 }
 fun selectSession(id: String) {
  val generation = ++selectionGeneration
  mutable.update { it.copy(selectedSession = null, loadingSession = true, sessionError = null) }
  io.execute {
   val result = runCatching { store.session(id) }
   main.post { if (!cleared && generation == selectionGeneration) result.fold(
    onSuccess = { record -> mutable.update { it.copy(selectedSession = record, loadingSession = false) } },
    onFailure = { mutable.update { it.copy(loadingSession = false, sessionError = "Rekaman tidak dapat dibaca. File disimpan tanpa perubahan.") } }) }
  }
 }
 fun clearSelection() { selectionGeneration++; mutable.update { it.copy(selectedSession = null, loadingSession = false, sessionError = null) } }
 private fun dispose() {
  val old = client; client = null; connecting = false; filter.reset()
  mutable.update { it.copy(connected = false, reading = null, point = null, deviceStatus = "Belum diketahui") }
  if (old != null) {
   old.setCallback(null)
   Thread({
    try { old.disconnectForcibly(0, 1000, false) } catch (_: Exception) { }
    try { old.close(true) } catch (_: Exception) { }
   }, "mqtt-cleanup").start()
  }
 }
 private fun failure(cause: Throwable?, stage: String = "Koneksi") {
  val wasConnected = state.value.connected
  val error = mqttFailure(stage, cause); dispose(); Log.e("BME_MQTT", error)
  mutable.update { it.copy(message = "Koneksi gagal. Mencoba lagi...", lastError = error) }
  if (wasConnected) sound("error")
 }
 private fun listener(owner: MqttAsyncClient, subscription: Boolean = false, success: () -> Unit): IMqttActionListener = object : IMqttActionListener {
  override fun onSuccess(token: IMqttToken?) { viewModelScope.launch {
   if (client === owner) {
    if (subscription && token?.grantedQos?.let { it.isNotEmpty() && it.all { qos -> qos in 0..2 } } != true) failure(MqttException(128), "Subscription")
    else success()
   }
  } }
  override fun onFailure(token: IMqttToken?, exception: Throwable?) { viewModelScope.launch { if (client === owner) failure(exception, if (subscription) "Subscription" else "Koneksi") } }
 }
 private fun receive(reading: Reading) {
  val previous = state.value.reading
  if (previous?.deviceId == reading.deviceId && previous.sequence == reading.sequence && previous.uptimeMs == reading.uptimeMs) return
  val deviceChanged = previous != null && (previous.deviceId != reading.deviceId || previous.uptimeMs > reading.uptimeMs)
  if (deviceChanged) { filter.reset(); mutable.update { it.copy(lastValid = LastValidValues(), trend = emptyList(), liveStartedElapsed = null) } }
  if (heldDevice != reading.deviceId || heldUptime?.let { reading.uptimeMs < it } == true) {
   mutable.update { it.copy(lastValid = LastValidValues(), trend = emptyList(), liveStartedElapsed = null) }
  }
  heldDevice = reading.deviceId; heldUptime = reading.uptimeMs
  val point = filter.apply(reading)
  val active = recorder
  if (active?.device != null && active.device != reading.deviceId) stopAcquisition("Perangkat berubah")
  recorder?.let { current ->
   current.add(point, reading.deviceId)
   mutable.update { it.copy(recordingSamples = current.size) }
   if (current.size >= SessionAccumulator.MAX_SAMPLES) stopAcquisition("Batas 3600 sampel tercapai")
  }
  val now = SystemClock.elapsedRealtime()
  mutable.update { it.copy(reading = reading, point = point, lastValid = it.lastValid.accept(point), nowElapsed = now, liveStartedElapsed = it.liveStartedElapsed ?: reading.receivedElapsed,
   trend = trimChartPoints(it.trend + point, now)) }
 }
 private fun connect() {
  val config = state.value.config
  if (config.validate() != null) return
  dispose(); lastAttempt = SystemClock.elapsedRealtime(); connecting = true
  mutable.update { it.copy(message = "Menghubungkan ke HiveMQ...") }
  try {
   val owner = MqttAsyncClient("ssl://${config.host}:${config.port}", "bme-${UUID.randomUUID().toString().take(16)}", MemoryPersistence())
   client = owner
   owner.setCallback(object : MqttCallback {
    override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    override fun connectionLost(cause: Throwable?) { viewModelScope.launch { if (client === owner) failure(cause) } }
    override fun messageArrived(topic: String, message: MqttMessage) {
     val payload = if (message.payload.size <= 2048) String(message.payload, Charsets.UTF_8) else return
     val elapsed = SystemClock.elapsedRealtime(); val wall = System.currentTimeMillis()
     viewModelScope.launch {
      if (client !== owner) return@launch
      if (topic == config.statusTopic) {
       when (payload) {
        "online" -> mutable.update { it.copy(deviceStatus = "Online") }
        "offline" -> { filter.reset(); mutable.update { it.copy(deviceStatus = "Offline", reading = null, point = null) } }
       }
      } else if (topic == config.topic) {
       val reading = Reading.parse(payload, message.isRetained, wall, elapsed)
       if (reading != null) receive(reading)
       else if (!message.isRetained) mutable.update { it.copy(message = "Pesan diterima, format JSON sensor tidak sesuai.") }
      }
     }
    }
   })
   val options = MqttConnectOptions().apply {
    userName = config.username; password = config.password.toCharArray(); isCleanSession = true; isAutomaticReconnect = false
    connectionTimeout = 10; keepAliveInterval = 20; socketFactory = SSLSocketFactory.getDefault()
    setHttpsHostnameVerificationEnabled(true); mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
   }
   owner.connect(options, null, listener(owner) {
    try {
     owner.subscribe(arrayOf(config.topic, config.statusTopic), intArrayOf(1, 1), null, listener(owner, subscription = true) {
      connecting = false
      mutable.update { it.copy(connected = true, lastError = null, message = "Broker tersambung. Data live diterima otomatis.") }
      sound("connected")
     })
    } catch (e: Exception) { failure(e, "Subscription") }
   })
  } catch (e: Exception) { failure(e) }
 }
 fun saveConfig(config: BrokerConfig) {
  val clean = config.copy(host = config.host.trim(), port = config.port.trim(), username = config.username.trim(), topic = config.topic.trim(),
   statusTopic = config.statusTopic.trim(), name = config.name.trim(), email = config.email.trim())
  clean.validate()?.let { error -> mutable.update { it.copy(message = error) }; return }
  if (recorder != null || state.value.savingSession || pending != null) { mutable.update { it.copy(message = "Hentikan dan simpan rekaman sebelum mengubah koneksi.") }; return }
  if (savingConfig) return
  savingConfig = true
  viewModelScope.launch(Dispatchers.IO) {
   val saved = runCatching { store.save(clean) }.isSuccess
   launch(Dispatchers.Main) {
    savingConfig = false
    if (saved) {
     dispose(); mutable.update { it.copy(config = clean, trend = emptyList(), lastValid = LastValidValues(), liveStartedElapsed = null, lastError = null, message = "Pengaturan tersimpan. Menghubungkan...") }; lastAttempt = -10000
    } else mutable.update { it.copy(message = "Gagal menyimpan pengaturan dengan aman.") }
   }
  }
 }
 override fun onCleared() {
  recorder?.let { active ->
   val record = active.finish(System.currentTimeMillis(), SystemClock.elapsedRealtime())
   io.execute { runCatching { store.saveSession(record) } }
  }
  cleared = true; dispose(); io.shutdown(); soundChannel.close(); super.onCleared()
 }
}
