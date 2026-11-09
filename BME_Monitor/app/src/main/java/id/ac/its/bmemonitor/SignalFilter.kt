package id.ac.its.bmemonitor

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max

data class TrendPoint(
 val elapsedMs: Long, val rawHr: Double?, val hr: Double?, val rawSpo2: Double?, val spo2: Double?,
 val hrReason: String? = null, val spo2Reason: String? = null
) {
 val highHr: Boolean get() = hr != null && hr > 120
}

/** Filter sampel baru. Nilai null tetap null untuk grafik/rerata; bukan diganti nilai lama.
 * Batas 120 BPM adalah aturan proyek pilihan pengguna, bukan batas fisiologis universal.
 * Penjelasan dan contoh: docs/FILTER-DATA.md. Tes: SignalFilterTest.kt.
 */
class SignalFilter {
 companion object {
  const val MAX_BPM = 120.0
  const val CONSISTENT_SAMPLES = 3
  const val RESET_GAP_MS = 5000L
  const val BASELINE_SIZE = 5
  const val JUMP_BPM = 30.0
  const val JUMP_FRACTION = 0.35
 }
 private val baseline = ArrayDeque<Double>()
 private val pending = ArrayDeque<Double>()
 private var lastTime: Long? = null
 private var lastDevice: String? = null
 fun reset() { baseline.clear(); pending.clear(); lastTime = null; lastDevice = null }
 private fun remember(value: Double) { baseline.addLast(value); while (baseline.size > BASELINE_SIZE) baseline.removeFirst() }
 fun apply(r: Reading): TrendPoint {
  if (lastDevice != r.deviceId || lastTime?.let { r.receivedElapsed - it > RESET_GAP_MS } == true || !r.finger) reset()
  lastTime = r.receivedElapsed; lastDevice = r.deviceId
  var accepted = r.heartRate?.takeIf { r.finger && it.isFinite() && it > 0.0 }
  // Tolak sebelum warm-up/baseline agar sampel >120 tidak memengaruhi sampel berikutnya.
  val exceedsLimit = accepted?.let { it > MAX_BPM } == true
  if (exceedsLimit) accepted = null
  var reason: String? = if (accepted == null) if (r.finger) "Nilai/flag BPM tidak valid" else "Jari tidak terdeteksi" else null
  if (exceedsLimit) reason = "BPM >120: ditolak oleh batas proyek"
  if (accepted == null) pending.clear() else {
   val value = accepted
   val median = baseline.sorted().let { if (it.isEmpty()) value else it[it.size / 2] }
   if (baseline.size < CONSISTENT_SAMPLES) {
    if (pending.isNotEmpty() && abs(value - pending.last()) > max(10.0, pending.last() * 0.1)) pending.clear()
    pending.addLast(value)
    if (pending.size >= CONSISTENT_SAMPLES) { pending.forEach(::remember); pending.clear() }
    else { accepted = null; reason = "Menunggu 3 sampel awal yang konsisten" }
   } else if (abs(value - median) > max(JUMP_BPM, median * JUMP_FRACTION)) {
    if (pending.isNotEmpty() && abs(value - pending.last()) > max(10.0, pending.last() * 0.1)) pending.clear()
    pending.addLast(value)
    if (pending.size >= CONSISTENT_SAMPLES) {
     baseline.clear(); pending.forEach(::remember); pending.clear()
    } else { accepted = null; reason = "Lonjakan belum terkonfirmasi" }
   } else { pending.clear(); remember(value) }
  }
  val sp = r.spo2?.takeIf { r.finger && it.isFinite() && it in 0.0..100.0 }
  return TrendPoint(r.receivedElapsed, r.rawHeartRate, accepted, r.rawSpo2, sp, reason,
   if (sp == null) if (r.finger) "Nilai/flag SpO2 tidak valid" else "Jari tidak terdeteksi" else null)
 }
}
