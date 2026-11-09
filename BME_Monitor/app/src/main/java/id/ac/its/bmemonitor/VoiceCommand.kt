package id.ac.its.bmemonitor

import java.util.Locale

/** Grammar kecil: satu hasil final harus cocok penuh, bukan sekadar mengandung kata. */
enum class VoiceCommand {
 START, STOP, READ;
 companion object {
  fun parse(text: String): VoiceCommand? = when (text.lowercase(Locale.ROOT)
   .trim().trimEnd('.', '!', '?').trim().replace(Regex("\\s+"), " ")) {
   "mulai rekam" -> START
   "stop rekam", "hentikan rekaman" -> STOP
   "bacakan hasil" -> READ
   else -> null
  }
 }
}
