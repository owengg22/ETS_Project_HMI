package id.ac.its.bmemonitor

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SessionSummary(
 val id: String, val startedAt: Long, val endedAt: Long, val durationMs: Long,
 val name: String, val device: String, val samples: Int,
 val hrCount: Int, val spo2Count: Int, val meanHr: Double?, val meanSpo2: Double?, val highHrCount: Int
) {
 val excludedHr: Int get() = samples - hrCount
 val excludedSpo2: Int get() = samples - spo2Count
}
data class SessionRecord(val summary: SessionSummary, val points: List<TrendPoint>)

class SessionAccumulator(val startedAt: Long, val startedElapsed: Long, private val name: String) {
 companion object { const val MAX_SAMPLES = 3600 }
 private val samples = mutableListOf<TrendPoint>()
 var device: String? = null
  private set
 val size: Int get() = samples.size
 fun add(point: TrendPoint, deviceId: String): Boolean {
  if (point.elapsedMs < startedElapsed || samples.size >= MAX_SAMPLES || (device != null && device != deviceId)) return false
  device = deviceId
  samples.add(point.copy(elapsedMs = point.elapsedMs - startedElapsed))
  return true
 }
 fun finish(endedAt: Long, endedElapsed: Long): SessionRecord {
  val hr = samples.mapNotNull { it.hr }; val sp = samples.mapNotNull { it.spo2 }
  val duration = (endedElapsed - startedElapsed).coerceAtLeast(0)
  return SessionRecord(SessionSummary(UUID.randomUUID().toString(), startedAt, endedAt, duration, name,
   device ?: "Belum ada data", samples.size, hr.size, sp.size, hr.takeIf { it.isNotEmpty() }?.average(),
   sp.takeIf { it.isNotEmpty() }?.average(), samples.count { it.highHr }), samples.toList())
 }
}

object SessionCodec {
 private fun JSONObject.nullableNumber(k: String): Double? = if (isNull(k)) null else getDouble(k).also { require(it.isFinite()) }
 fun summary(s: SessionSummary): JSONObject = JSONObject().put("id", s.id).put("started", s.startedAt)
  .put("ended", s.endedAt).put("duration", s.durationMs).put("name", s.name).put("device", s.device)
  .put("samples", s.samples).put("hrCount", s.hrCount).put("spo2Count", s.spo2Count)
  .put("meanHr", s.meanHr ?: JSONObject.NULL).put("meanSpo2", s.meanSpo2 ?: JSONObject.NULL).put("highHrCount", s.highHrCount)
 fun summary(j: JSONObject): SessionSummary {
  val s = SessionSummary(j.getString("id"), j.getLong("started"), j.getLong("ended"), j.getLong("duration"),
   j.getString("name"), j.getString("device"), j.getInt("samples"), j.getInt("hrCount"), j.getInt("spo2Count"),
   j.nullableNumber("meanHr"), j.nullableNumber("meanSpo2"), j.getInt("highHrCount"))
  UUID.fromString(s.id)
  require(s.durationMs >= 0 && s.samples in 0..SessionAccumulator.MAX_SAMPLES &&
   s.hrCount in 0..s.samples && s.spo2Count in 0..s.samples && s.highHrCount in 0..s.hrCount)
  return s
 }
 fun encode(record: SessionRecord): String {
  val a = JSONArray()
  record.points.forEach { p -> a.put(JSONObject().put("t", p.elapsedMs).put("rawHr", p.rawHr ?: JSONObject.NULL)
   .put("hr", p.hr ?: JSONObject.NULL).put("rawSpo2", p.rawSpo2 ?: JSONObject.NULL).put("spo2", p.spo2 ?: JSONObject.NULL)
   .put("hrReason", p.hrReason ?: JSONObject.NULL).put("spo2Reason", p.spo2Reason ?: JSONObject.NULL)) }
  return JSONObject().put("summary", summary(record.summary)).put("points", a).toString()
 }
 fun decode(text: String): SessionRecord {
  val j = JSONObject(text); val s = summary(j.getJSONObject("summary")); val a = j.getJSONArray("points")
  require(a.length() == s.samples)
  val points = (0 until a.length()).map { i ->
   val p = a.getJSONObject(i)
   TrendPoint(p.getLong("t"), p.nullableNumber("rawHr"), p.nullableNumber("hr"), p.nullableNumber("rawSpo2"),
    p.nullableNumber("spo2"), if (p.isNull("hrReason")) null else p.getString("hrReason"),
    if (p.isNull("spo2Reason")) null else p.getString("spo2Reason"))
  }
  require(points.all { it.elapsedMs in 0..s.durationMs })
  require(points.zipWithNext().all { (a, b) -> b.elapsedMs >= a.elapsedMs })
  return SessionRecord(s, points)
 }
 fun speech(point: TrendPoint?, fresh: Boolean): String {
  if (!fresh || point == null) return "Data sensor belum tersedia atau sudah basi."
  fun spoken(n: Double): String = String.format(java.util.Locale("id", "ID"), "%.0f", n)
  val parts = mutableListOf<String>()
  point.hr?.let { parts.add("Detak jantung ${spoken(it)} kali per menit.") }
  point.spo2?.let { parts.add("Saturasi oksigen ${spoken(it)} persen.") }
  if (point.highHr) parts.add("Detak di atas penanda 120. Verifikasi dengan alat pembanding.")
  if (parts.isEmpty()) return "Sinyal belum stabil. Pastikan jari tetap pada sensor."
  return parts.joinToString(" ")
 }
}
