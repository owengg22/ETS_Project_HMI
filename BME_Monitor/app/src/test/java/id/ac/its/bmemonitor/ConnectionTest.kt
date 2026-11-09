package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test
import org.eclipse.paho.client.mqttv3.MqttException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ConnectionTest {
 private val config = """{"host":"test.s1.eu.hivemq.cloud","port":"8883","username":"test-user","password":"test-secret","topic":"test/vitals","statusTopic":"test/status"}"""
 @Test fun credentialsReplaceBrokerSettingsButPreserveProfile() {
  val c = CredentialDefaults.apply(config, BrokerConfig(name = "Profil", email = "a@example.com", password = "old"))
  assertEquals("test.s1.eu.hivemq.cloud", c.host)
  assertEquals("test-secret", c.password)
  assertEquals("test-user", c.username)
  assertEquals("Profil", c.name); assertEquals("a@example.com", c.email)
  assertNull(c.validate())
 }
 @Test fun bootstrapFingerprintIsStableAndChangesWhenConfigChanges() {
  assertEquals(CredentialDefaults.fingerprint(config), CredentialDefaults.fingerprint(config))
  assertNotEquals(CredentialDefaults.fingerprint(config), CredentialDefaults.fingerprint(config.replace("test-secret", "new-secret")))
 }
 @Test fun rejectsInvalidPackagedConfig() {
  assertThrows(IllegalArgumentException::class.java) { CredentialDefaults.apply(config.replace("test/vitals", "#"), BrokerConfig()) }
 }
 @Test fun distinguishesAuthenticationSubscriptionAndDns() {
  assertTrue(mqttFailure("Koneksi", MqttException(4)).contains("Username atau password"))
  assertTrue(mqttFailure("Koneksi", MqttException(5)).contains("tidak diizinkan"))
  assertTrue(mqttFailure("Subscription", MqttException(128)).contains("subscribe"))
  assertTrue(mqttFailure("Koneksi", MqttException(UnknownHostException("private detail"))).contains("DNS"))
 }
 @Test fun showsTlsTimeoutWithoutLeakingExceptionMessage() {
  val tls = mqttFailure("Koneksi", MqttException(SSLHandshakeException("secret-marker")))
  assertTrue(tls.contains("TLS")); assertFalse(tls.contains("secret-marker"))
  assertTrue(mqttFailure("Koneksi", SocketTimeoutException()).contains("timeout"))
 }
}
