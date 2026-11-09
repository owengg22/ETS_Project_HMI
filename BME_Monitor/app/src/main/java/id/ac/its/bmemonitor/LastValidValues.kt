package id.ac.its.bmemonitor

/** Hanya untuk kartu angka di layar. Jangan kirim nilai ini ke SessionAccumulator/grafik. */
data class HeldValue(val value: Double, val elapsedMs: Long) {
 fun ageSeconds(now: Long): Long = ((now - elapsedMs).coerceAtLeast(0)) / 1000
}

data class LastValidValues(val hr: HeldValue? = null, val spo2: HeldValue? = null) {
 fun accept(point: TrendPoint) = copy(
  hr = point.hr?.let { HeldValue(it, point.elapsedMs) } ?: hr,
  spo2 = point.spo2?.let { HeldValue(it, point.elapsedMs) } ?: spo2
 )
}
