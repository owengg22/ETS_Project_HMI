package id.ac.its.bmemonitor

import org.json.JSONObject

data class Reading(
 val deviceId: String, val sequence: Long, val uptimeMs: Long,
 val heartRate: Double?, val spo2: Double?, val finger: Boolean,
 val receivedAt: Long, val receivedElapsed: Long,
 val rawHeartRate: Double? = heartRate, val rawSpo2: Double? = spo2
) {
 fun isFresh(now: Long): Boolean = now - receivedElapsed in 0..5000
 companion object {
  fun parse(payload: String, retained: Boolean, wall: Long, elapsed: Long): Reading? {
   if (retained || payload.length > 2048) return null
   return try {
    val j = JSONObject(payload)
    val device = j.get("device_id") as? String ?: return null
    if (!device.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return null
    fun number(k: String): Double {
     val n = (j.get(k) as? Number)?.toDouble() ?: error("Bukan angka")
     require(n.isFinite()); return n
    }
    fun counter(k: String): Long {
     val n = number(k); require(n in 0.0..4294967295.0 && n % 1.0 == 0.0); return n.toLong()
    }
    val finger = j.get("finger") as? Boolean ?: return null
    val hv = j.get("hr_valid") as? Boolean ?: return null
    val sv = j.get("spo2_valid") as? Boolean ?: return null
    val hr = number("hr"); val sp = number("spo2")
    require(listOf(hr, sp).all { it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0 })
    Reading(device, counter("seq"), counter("uptime_ms"), hr.takeIf { finger && hv && it > 0 },
     sp.takeIf { finger && sv && it in 0.0..100.0 }, finger, wall, elapsed, hr, sp)
   } catch (_: Exception) { null }
  }
 }
}
