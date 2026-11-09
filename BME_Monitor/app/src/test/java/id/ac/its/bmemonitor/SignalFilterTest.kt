package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test

class SignalFilterTest {
 private fun reading(hr: Double?, t: Long, finger: Boolean = true, sp: Double? = 98.0) =
  Reading("esp32-01", t, t, hr, sp, finger, 100000 + t, t, hr ?: -999.0, sp ?: -999.0)
 private fun warmed(hr: Double = 80.0) = SignalFilter().apply {
  repeat(3) { apply(reading(hr, (it + 1) * 1000L)) }
 }
 @Test fun warmupRequiresThreeConsistentSamples() {
  val f = SignalFilter()
  assertNull(f.apply(reading(80.0, 1000)).hr)
  assertNull(f.apply(reading(81.0, 2000)).hr)
  assertEquals(82.0, f.apply(reading(82.0, 3000)).hr!!, 0.0)
 }
 @Test fun allValuesAbove120AreRejectedEvenWhenStable() {
  val f = warmed()
  listOf(120.01, 121.0, 150.0, 200.0, 200.0, 200.0).forEachIndexed { i, n ->
   val p = f.apply(reading(n, 4000L + i * 1000))
   assertNull(p.hr); assertEquals(n, p.rawHr!!, 0.0); assertTrue(p.hrReason!!.contains(">120"))
  }
  assertEquals(80.0, f.apply(reading(80.0, 10000)).hr!!, 0.0)
 }
 @Test fun exactly120CanBeAcceptedWithoutClamping() {
  val f = warmed(120.0)
  assertEquals(120.0, f.apply(reading(120.0, 4000)).hr!!, 0.0)
 }
 @Test fun highValueCannotCountTowardWarmup() {
  val f = SignalFilter()
  f.apply(reading(80.0, 1000)); f.apply(reading(81.0, 2000)); f.apply(reading(150.0, 3000))
  assertNull(f.apply(reading(82.0, 4000)).hr)
  assertNull(f.apply(reading(83.0, 5000)).hr)
  assertEquals(84.0, f.apply(reading(84.0, 6000)).hr!!, 0.0)
 }
 @Test fun isolatedJumpWithinLimitIsExcludedAndDoesNotPolluteBaseline() {
  val f = warmed(); val p = f.apply(reading(120.0, 4000))
  assertNull(p.hr); assertEquals(120.0, p.rawHr!!, 0.0); assertEquals("Lonjakan belum terkonfirmasi", p.hrReason)
  assertEquals(81.0, f.apply(reading(81.0, 5000)).hr!!, 0.0)
 }
 @Test fun persistentStepWithinLimitNeedsThreeConsistentFrames() {
  val f = warmed(60.0)
  assertNull(f.apply(reading(100.0, 4000)).hr)
  assertNull(f.apply(reading(101.0, 5000)).hr)
  assertEquals(102.0, f.apply(reading(102.0, 6000)).hr!!, 0.0)
 }
 @Test fun inconsistentCandidatesDoNotPromoteJump() {
  val f = warmed(60.0)
  for ((i, n) in listOf(100.0, 119.0, 100.0).withIndex()) assertNull(f.apply(reading(n, 4000L + i * 1000)).hr)
 }
 @Test fun missingFrameInterruptsPendingStep() {
  val f = warmed(60.0)
  f.apply(reading(100.0, 4000)); f.apply(reading(null, 5000))
  assertNull(f.apply(reading(100.0, 6000)).hr)
  assertNull(f.apply(reading(101.0, 7000)).hr)
 }
 @Test fun staleGapDeviceChangeAndFingerLossRestartWarmup() {
  val f = warmed()
  assertNull(f.apply(reading(80.0, 10000)).hr)
  assertNull(f.apply(reading(80.0, 11000).copy(deviceId = "esp32-02")).hr)
  val p = f.apply(reading(80.0, 12000, false))
  assertNull(p.hr); assertNull(p.spo2)
 }
 @Test fun channelsAreIndependentAndInvalidNumbersRejected() {
  val f = warmed()
  val high = f.apply(reading(150.0, 4000))
  assertNull(high.hr); assertEquals(98.0, high.spo2!!, 0.0)
  for ((i, n) in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -999.0).withIndex()) {
   val p = f.apply(reading(n, 5000L + i * 1000, sp = 101.0))
   assertNull(p.hr); assertNull(p.spo2)
  }
 }
}
