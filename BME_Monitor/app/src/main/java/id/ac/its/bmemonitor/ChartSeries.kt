package id.ac.its.bmemonitor

/** Geometri untuk tampilan saja. Tidak menghasilkan sampel sensor atau masukan rerata. */
data class ChartSegment(val fromMs: Long, val toMs: Long, val fromValue: Double, val toValue: Double, val held: Boolean)

/** Input berurutan menurut elapsedMs; garis held mendatar hingga sampel valid berikutnya. */
fun chartSegments(points: List<TrendPoint>, startMs: Long, untilMs: Long, heartRate: Boolean): List<ChartSegment> {
 if (untilMs < startMs) return emptyList()
 val result = mutableListOf<ChartSegment>()
 fun segment(from: Long, to: Long, a: Double, b: Double, held: Boolean) {
  if (to < startMs || from > untilMs || to < from) return
  val left = maxOf(from, startMs); val right = minOf(to, untilMs)
  if (from == to) {
   if (a != b) result.add(ChartSegment(from, to, a, b, held))
  } else if (left < right) {
   fun interpolate(t: Long) = a + (b - a) * (t - from).toDouble() / (to - from)
   result.add(ChartSegment(left, right, interpolate(left), interpolate(right), held))
  }
 }
 var previous: TrendPoint? = null
 var lastValue: Double? = null
 var missing = false
 for (point in points) {
  if (point.elapsedMs > untilMs) break
  val value = (if (heartRate) point.hr else point.spo2)?.takeIf { it.isFinite() }
  if (value == null) { missing = true; continue }
  val old = previous; val oldValue = lastValue
  if (old != null && oldValue != null) {
   if (missing || point.elapsedMs - old.elapsedMs > 2500) {
    segment(old.elapsedMs, point.elapsedMs, oldValue, oldValue, true)
    segment(point.elapsedMs, point.elapsedMs, oldValue, value, false)
   } else segment(old.elapsedMs, point.elapsedMs, oldValue, value, false)
  }
  previous = point; lastValue = value; missing = false
 }
 if (previous != null && lastValue != null) segment(previous.elapsedMs, untilMs, lastValue, lastValue, true)
 return result
}

/** Maksimal 600 sampel dalam jendela, ditambah 3 anchor untuk nilai sebelum batas kiri. */
fun trimChartPoints(points: List<TrendPoint>, nowMs: Long): List<TrendPoint> {
 val cutoff = nowMs - 60000
 val older = points.filter { it.elapsedMs < cutoff }
 val anchors = listOfNotNull(older.lastOrNull { it.hr != null }, older.lastOrNull { it.spo2 != null }, older.lastOrNull())
  .distinct().sortedBy { it.elapsedMs }
 return anchors + points.filter { it.elapsedMs >= cutoff }.takeLast(600)
}
