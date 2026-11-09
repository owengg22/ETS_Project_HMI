package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test

class LastValidValuesTest {
 @Test fun holdsChannelsSeparatelyWithoutRefreshingTimestamp() {
  val first = TrendPoint(1000, 80.0, 80.0, 98.0, 98.0)
  val next = TrendPoint(2000, -999.0, null, 99.0, 99.0)
  val held = LastValidValues().accept(first).accept(next)
  assertEquals(HeldValue(80.0, 1000), held.hr)
  assertEquals(HeldValue(99.0, 2000), held.spo2)
  assertEquals(7L, held.hr!!.ageSeconds(8500))
  assertNull(next.hr)
 }
 @Test fun missingFirstSamplesDoNotInventValues() {
  val held = LastValidValues().accept(TrendPoint(1000, -999.0, null, -999.0, null))
  assertNull(held.hr); assertNull(held.spo2)
 }
 @Test fun heldValuesNeverBecomeExtraAverageSamples() {
  val recorder = SessionAccumulator(100000, 1000, "Owen")
  var held = LastValidValues()
  val points = listOf(TrendPoint(1000, 80.0, 80.0, 98.0, 98.0),
   TrendPoint(2000, 150.0, null, -999.0, null, ">120", "Invalid"),
   TrendPoint(3000, -999.0, null, 100.0, 100.0))
  points.forEach { held = held.accept(it); recorder.add(it, "esp32-01") }
  assertEquals(80.0, held.hr!!.value, 0.0)
  val s = recorder.finish(104000, 4000).summary
  assertEquals(1, s.hrCount); assertEquals(2, s.spo2Count)
  assertEquals(80.0, s.meanHr!!, 0.0); assertEquals(99.0, s.meanSpo2!!, 0.0)
  assertEquals(2, s.excludedHr); assertEquals(1, s.excludedSpo2)
 }
 @Test fun reconnectCanShowHeldValueButCannotSpeakItAsFresh() {
  val held = LastValidValues(hr = HeldValue(80.0, 1000))
  val s = MonitorState(connected = false, nowElapsed = 10000, lastValid = held)
  assertFalse(s.fresh); assertFalse(s.canSpeak)
  assertEquals(80.0, s.lastValid.hr!!.value, 0.0)
 }
}
