package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test

class ReadingTest {
 private val payload = """{"device_id":"esp32-01","seq":1,"uptime_ms":5500,"hr":95,"spo2":99,"hr_valid":true,"spo2_valid":true,"finger":true,"red":114774,"ir":128970}"""
 private fun parse(text: String = payload, retained: Boolean = false) = Reading.parse(text, retained, 100000, 10000)
 @Test fun validFirmwareFrameAndFreshness() {
  val r = parse()!!
  assertEquals(95.0, r.heartRate!!, 0.0); assertEquals(99.0, r.spo2!!, 0.0)
  assertTrue(r.isFresh(15000)); assertFalse(r.isFresh(15001)); assertFalse(r.isFresh(9999))
 }
 @Test fun flagsAndFingerGateValues() {
  val noFinger = parse(payload.replace("\"finger\":true", "\"finger\":false"))!!
  assertNull(noFinger.heartRate); assertNull(noFinger.spo2)
  val r = parse(payload.replace("\"hr_valid\":true", "\"hr_valid\":false"))!!
  assertNull(r.heartRate); assertEquals(99.0, r.spo2!!, 0.0)
  assertNull(parse(payload.replace("\"spo2\":99", "\"spo2\":-999"))!!.spo2)
  assertNull(parse(payload.replace("\"spo2\":99", "\"spo2\":101"))!!.spo2)
  assertNull(parse(payload.replace("\"hr\":95", "\"hr\":-999"))!!.heartRate)
 }
 @Test fun rejectsRetainedMalformedAndWrongTypes() {
  assertNull(parse(retained = true)); assertNull(parse("not JSON"))
  assertNull(parse(payload.replace("\"hr\":95", "\"hr\":\"95\"")))
  assertNull(parse(payload.replace("\"seq\":1", "\"seq\":-1")))
  assertNull(parse(payload.replace("\"seq\":1", "\"seq\":1.2")))
  assertNull(parse(payload.replace("\"finger\":true", "\"finger\":1")))
  assertNull(parse("x".repeat(2049)))
 }
 @Test fun rejectsNumbersOutsideFirmwareIntegerContract() {
  assertNull(parse(payload.replace("\"hr\":95", "\"hr\":1e300")))
  assertNull(parse(payload.replace("\"hr\":95", "\"hr\":95.5")))
 }
 @Test fun invalidConfigurationDoesNotConnect() {
  assertNotNull(BrokerConfig().validate())
  val config = BrokerConfig(host = "broker.example.com", username = "test", password = "test")
  assertNull(config.validate())
  assertNotNull(config.copy(topic = "#").validate())
  assertNotNull(config.copy(host = "https://broker").validate())
  assertNotNull(config.copy(port = "70000").validate())
  assertNotNull(config.copy(statusTopic = BrokerConfig().topic).validate())
 }
 @Test fun livePreviewWorksBeforeRecordingAndRejectsStaleOrDisconnected() {
  val point = TrendPoint(10000, 95.0, 95.0, 99.0, 99.0)
  val live = MonitorState(connected = true, acquiring = false, reading = parse(), point = point, nowElapsed = 10000)
  assertTrue(live.fresh); assertTrue(live.canSpeak)
  assertTrue(live.copy(acquiring = true).canSpeak)
  assertFalse(live.copy(connected = false).canSpeak)
  assertFalse(live.copy(nowElapsed = 15001).canSpeak)
  assertFalse(live.copy(point = point.copy(hr = null, spo2 = null)).canSpeak)
 }
}
