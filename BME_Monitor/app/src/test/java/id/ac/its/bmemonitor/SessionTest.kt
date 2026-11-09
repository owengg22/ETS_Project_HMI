package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test

class SessionTest {
 private fun record(): SessionRecord {
  val a = SessionAccumulator(100000, 1000, "Owen")
  a.add(TrendPoint(1000, 200.0, null, 98.0, 98.0, "Lonjakan"), "esp32-01")
  a.add(TrendPoint(2000, 80.0, 80.0, -999.0, null, null, "Invalid"), "esp32-01")
  a.add(TrendPoint(3000, 200.0, 200.0, 100.0, 100.0), "esp32-01")
  return a.finish(103000, 4000)
 }
 @Test fun meansUseSeparateAcceptedCountsWithoutZeroImputation() {
  val r = record(); val s = r.summary
  assertEquals(140.0, s.meanHr!!, 0.0); assertEquals(99.0, s.meanSpo2!!, 0.0)
  assertEquals(2, s.hrCount); assertEquals(2, s.spo2Count)
  assertEquals(1, s.excludedHr); assertEquals(1, s.excludedSpo2); assertEquals(1, s.highHrCount)
  assertEquals(3000, s.durationMs); assertEquals(0, r.points.first().elapsedMs)
 }
 @Test fun emptyRecordingHasNoInventedAverage() {
  val s = SessionAccumulator(0, 1000, "").finish(2000, 3000).summary
  assertNull(s.meanHr); assertNull(s.meanSpo2); assertEquals(0, s.samples)
 }
 @Test fun codecPreservesRawReasonsAndSampleTiming() {
  val r = record(); val restored = SessionCodec.decode(SessionCodec.encode(r))
  assertEquals(r, restored)
  assertEquals(200.0, restored.points.first().rawHr!!, 0.0)
  assertNull(restored.points.first().hr)
 }
 @Test fun refusesFramesBeforeStartDifferentDevicesAndOverLimit() {
  val a = SessionAccumulator(0, 1000, "")
  assertFalse(a.add(TrendPoint(999, 80.0, 80.0, 98.0, 98.0), "a"))
  assertTrue(a.add(TrendPoint(1000, 80.0, 80.0, 98.0, 98.0), "a"))
  assertFalse(a.add(TrendPoint(1001, 80.0, 80.0, 98.0, 98.0), "b"))
  repeat(SessionAccumulator.MAX_SAMPLES - 1) { assertTrue(a.add(TrendPoint(1001L + it, 80.0, 80.0, 98.0, 98.0), "a")) }
  assertFalse(a.add(TrendPoint(10000, 80.0, 80.0, 98.0, 98.0), "a"))
 }
 @Test fun rejectsSessionFileWithFutureTimestamp() {
  val r = record(); val text = SessionCodec.encode(r).replace("\"t\":2000", "\"t\":999999")
  assertThrows(IllegalArgumentException::class.java) { SessionCodec.decode(text) }
 }
 @Test fun speechNeverReadsInvalidSentinelsOrStaleValues() {
  val p = TrendPoint(1000, -999.0, null, 98.0, 98.0, "Invalid")
  assertFalse(SessionCodec.speech(p, true).contains("999"))
  assertFalse(SessionCodec.speech(p, false).contains("98"))
  val high = SessionCodec.speech(p.copy(rawHr = 200.0, hr = 200.0), true)
  assertTrue(high.contains("200")); assertTrue(high.contains("120"))
 }
}
