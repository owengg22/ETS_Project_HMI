package id.ac.its.bmemonitor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

data class SoundEvent(val kind: String, val elapsedMs: Long = SystemClock.elapsedRealtime())

class AudioController(context: Context, private val status: (String) -> Unit) {
 private val handler = Handler(Looper.getMainLooper())
 private val manager = context.getSystemService(AudioManager::class.java)
 private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
 private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
  .setAudioAttributes(attributes).setOnAudioFocusChangeListener { if (it < 0) stop() }.build()
 private var tone: ToneGenerator? = null
 private var tts: TextToSpeech? = null
 private var ready = false
 private var closed = false
 var enabled = true
  set(value) { field = value; if (!value) stop() }
 init {
  tts = TextToSpeech(context.applicationContext) { result -> handler.post {
   if (!closed) {
    val engine = tts
    val voice = engine?.voices?.filter { it.locale.language in setOf("id", "in") && !it.isNetworkConnectionRequired && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
     ?.sortedBy { if (it.locale.country == "ID") 0 else 1 }?.firstOrNull()
    if (result == TextToSpeech.SUCCESS && engine != null && voice != null) {
     engine.voice = voice; engine.setSpeechRate(0.9f); engine.setAudioAttributes(attributes); ready = true
     status("Suara Indonesia offline siap.")
    } else status("Pasang suara Indonesia offline melalui pengaturan Text-to-speech HP agar Bacakan hasil tersedia.")
   }
  } }
  tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
   override fun onStart(id: String?) = Unit
   override fun onDone(id: String?) { handler.post { manager.abandonAudioFocusRequest(focus) } }
   @Deprecated("Legacy callback") override fun onError(id: String?) { handler.post { manager.abandonAudioFocusRequest(focus); status("Pemutaran suara gagal.") } }
  })
 }
 fun play(kind: String) {
  if (!enabled || closed || tts?.isSpeaking == true) return
  if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
  try {
   val player = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 35).also { tone = it }
   player.startTone(when (kind) {
    "connected", "started" -> ToneGenerator.TONE_PROP_ACK
    "saved" -> ToneGenerator.TONE_PROP_BEEP2
    "error" -> ToneGenerator.TONE_PROP_NACK
    else -> ToneGenerator.TONE_PROP_PROMPT
   }, 150)
   handler.postDelayed({ if (tts?.isSpeaking != true) manager.abandonAudioFocusRequest(focus) }, 220)
  } catch (_: RuntimeException) { manager.abandonAudioFocusRequest(focus); status("Bunyi status tidak tersedia di perangkat ini.") }
 }
 fun speak(text: String): Boolean {
  if (!enabled || closed) return false
  if (!ready) { status("Suara Indonesia offline belum siap. Periksa pengaturan Text-to-speech HP."); return false }
  if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { status("Audio sedang dipakai aplikasi lain."); return false }
  tone?.stopTone()
  val success = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reading-" + SystemClock.elapsedRealtime()) == TextToSpeech.SUCCESS
  if (!success) { manager.abandonAudioFocusRequest(focus); status("Hasil belum berhasil dibacakan.") }
  return success
 }
 fun stop() { tts?.stop(); tone?.stopTone(); manager.abandonAudioFocusRequest(focus) }
 fun close() { closed = true; handler.removeCallbacksAndMessages(null); stop(); tts?.shutdown(); tts = null; tone?.release(); tone = null }
}
