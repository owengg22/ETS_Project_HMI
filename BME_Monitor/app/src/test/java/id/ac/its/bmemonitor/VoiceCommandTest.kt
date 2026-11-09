package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test

class VoiceCommandTest {
 @Test fun acceptsOnlyKnownWholeCommands() {
  assertEquals(VoiceCommand.START, VoiceCommand.parse("Mulai rekam"))
  assertEquals(VoiceCommand.STOP, VoiceCommand.parse("Stop rekam"))
  assertEquals(VoiceCommand.STOP, VoiceCommand.parse("hentikan rekaman"))
  assertEquals(VoiceCommand.READ, VoiceCommand.parse("Bacakan hasil"))
 }
 @Test fun handlesCaseWhitespaceAndFinalPunctuation() {
  assertEquals(VoiceCommand.START, VoiceCommand.parse("  MULAI   REKAM.  "))
  assertEquals(VoiceCommand.READ, VoiceCommand.parse("bacakan hasil!"))
 }
 @Test fun unrelatedNegatedAndCombinedUtterancesCannotTriggerActions() {
  for (text in listOf("", "rekam", "jangan mulai rekam", "mulai rekam lalu stop rekam", "tolong mulai rekam", "bacakan hasil pasien lain"))
   assertNull(text, VoiceCommand.parse(text))
 }
}
