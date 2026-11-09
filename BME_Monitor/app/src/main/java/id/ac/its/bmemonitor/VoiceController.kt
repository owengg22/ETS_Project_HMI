package id.ac.its.bmemonitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Satu sesi setelah tombol ditekan. Tidak merekam file atau mendengar di background. */
class VoiceController(private val context: Context,
 private val status: (Boolean, String) -> Unit, private val command: (VoiceCommand) -> Unit) {
 private var recognizer: SpeechRecognizer? = null
 private val handler = Handler(Looper.getMainLooper())
 private var timeout: Runnable? = null
 fun cancel() {
  release()
  status(false, "")
 }
 private fun release() {
  timeout?.let(handler::removeCallbacks); timeout = null
  val old = recognizer; recognizer = null
  runCatching { old?.cancel() }; runCatching { old?.destroy() }
 }
 fun start() {
  if (recognizer != null) return
  if (!SpeechRecognizer.isRecognitionAvailable(context)) {
   status(false, "Layanan pengenalan suara belum tersedia di HP."); return
  }
  try {
   // Ketersediaan engine offline tidak berarti model bahasa id-ID terpasang.
   // Gunakan layanan sistem agar provider dapat memilih online/offline sesuai dukungannya.
   val owner = SpeechRecognizer.createSpeechRecognizer(context)
   recognizer = owner
   owner.setRecognitionListener(object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) { if (recognizer === owner) status(true, "Mendengarkan...") }
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { if (recognizer === owner) status(true, "Memproses ucapan...") }
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onError(error: Int) {
     if (recognizer !== owner) return
     release()
     status(false, when (error) {
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan."
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> "Ucapan belum dikenali. Tekan tombol untuk mencoba lagi."
      SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Pengenalan suara memerlukan koneksi. Periksa internet HP."
      SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Layanan suara yang dipilih belum mendukung bahasa Indonesia (kode 12). Periksa layanan input suara di Pengaturan HP."
      SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Model bahasa Indonesia belum tersedia (kode 13). Aktifkan internet atau pasang paket bahasa pada layanan suara HP."
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Layanan suara sedang dipakai aplikasi lain."
      else -> "Pengenalan suara gagal (kode $error). Tombol manual tetap tersedia."
     })
    }
    override fun onResults(results: Bundle?) {
     if (recognizer !== owner) return
     val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
     release()
     val parsed = VoiceCommand.parse(text)
     status(false, if (parsed == null) "Perintah tidak dikenali: ${text.take(100)}" else "Perintah: ${text.take(100)}")
     if (parsed != null) command(parsed)
    }
   })
   status(true, "Menyiapkan mikrofon...")
   timeout = Runnable { if (recognizer === owner) { release(); status(false, "Waktu mendengarkan habis. Tekan tombol untuk mencoba lagi.") } }.also { handler.postDelayed(it, 12000) }
   owner.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
   })
  } catch (_: Exception) { release(); status(false, "Mikrofon tidak dapat dibuka. Coba lagi atau gunakan tombol manual.") }
 }
}
