package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test

class ChartSeriesTest {
 private fun point(t: Long, hr: Double?, sp: Double? = 98.0) = TrendPoint(t, hr ?: -999.0, hr, sp ?: -999.0, sp)
 @Test fun adjacentValidSamplesKeepTheirSolidLine() {
  val s = chartSegments(listOf(point(1000, 80.0), point(2000, 82.0)), 1000, 2000, true)
  assertEquals(listOf(ChartSegment(1000, 2000, 80.0, 82.0, false)), s)
 }
 @Test fun invalidSampleHoldsOldValueUntilNewValidSample() {
  val s = chartSegments(listOf(point(1000, 80.0), point(2000, null), point(3000, 83.0)), 1000, 3000, true)
  assertEquals(listOf(ChartSegment(1000, 3000, 80.0, 80.0, true), ChartSegment(3000, 3000, 80.0, 83.0, false)), s)
 }
 @Test fun invalidTailStaysFlatToCurrentTime() {
  val s = chartSegments(listOf(point(1000, 80.0), point(2000, null), point(3000, null)), 1000, 4000, true)
  assertEquals(listOf(ChartSegment(1000, 4000, 80.0, 80.0, true)), s)
 }
 @Test fun packetGapAndTrailingSilenceAreDashedRatherThanDiagonal() {
  val s = chartSegments(listOf(point(1000, 80.0), point(5000, 84.0)), 1000, 7000, true)
  assertEquals(listOf(ChartSegment(1000, 5000, 80.0, 80.0, true), ChartSegment(5000, 5000, 80.0, 84.0, false),
   ChartSegment(5000, 7000, 84.0, 84.0, true)), s)
 }
 @Test fun neverInventsValueBeforeFirstValidReading() {
  assertTrue(chartSegments(listOf(point(1000, null), point(2000, null)), 0, 3000, true).isEmpty())
  val s = chartSegments(listOf(point(1000, null), point(2000, 80.0)), 0, 3000, true)
  assertEquals(2000L, s.first().fromMs)
 }
 @Test fun heartRateAndSpo2HoldIndependently() {
  val p = listOf(point(1000, 80.0, 98.0), point(2000, null, 99.0), point(3000, 82.0, 100.0))
  assertTrue(chartSegments(p, 1000, 3000, true).first().held)
  assertTrue(chartSegments(p, 1000, 3000, false).none { it.held })
 }
 @Test fun clipsLeftBoundaryAndNeverProjectsPastUntil() {
  val s = chartSegments(listOf(point(5000, 80.0), point(7000, 84.0), point(8000, 90.0)), 6000, 7000, true)
  assertEquals(listOf(ChartSegment(6000, 7000, 82.0, 84.0, false)), s)
 }
 @Test fun rollingWindowRetainsOldValuesAndThenAcceptsFreshValue() {
  val p = listOf(point(1000, 80.0, null), point(2000, null, 98.0), point(60000, null, null), point(121000, null, null))
  val trimmed = trimChartPoints(p, 121000)
  assertEquals(4, trimmed.size)
  assertEquals(listOf(ChartSegment(61000, 121000, 80.0, 80.0, true)), chartSegments(trimmed, 61000, 121000, true))
  assertEquals(listOf(ChartSegment(61000, 121000, 98.0, 98.0, true)), chartSegments(trimmed, 61000, 121000, false))
  val fresh = trimChartPoints(trimmed + point(122000, 82.0, 99.0), 122000)
  val s = chartSegments(fresh, 62000, 122000, true)
  assertEquals(ChartSegment(62000, 122000, 80.0, 80.0, true), s.first())
  assertEquals(82.0, s.last().toValue, 0.0)
 }
 @Test fun liveBufferRemainsBounded() {
  val points = (0 until 1000).map { point(100000L + it, 80.0) }
  assertEquals(600, trimChartPoints(points, 101000).size)
 }
 @Test fun chartHoldDoesNotChangeRawPointsOrSessionAverage() {
  val points = listOf(point(1000, 80.0), point(2000, null), point(3000, 82.0))
  val original = points.toList()
  chartSegments(points, 1000, 5000, true)
  assertEquals(original, points)
  assertNull(points[1].hr)
  val a = SessionAccumulator(100000, 1000, "Owen")
  points.forEach { a.add(it, "esp32-01") }
  val summary = a.finish(105000, 5000).summary
  assertEquals(2, summary.hrCount); assertEquals(1, summary.excludedHr)
  assertEquals(81.0, summary.meanHr!!, 0.0)
 }
}
