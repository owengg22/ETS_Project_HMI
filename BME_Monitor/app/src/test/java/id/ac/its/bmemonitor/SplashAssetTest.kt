package id.ac.its.bmemonitor

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.hypot

class SplashAssetTest {
 private val res: File get() = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
 private fun xml(file: File) = DocumentBuilderFactory.newInstance().apply {
  setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
 }.newDocumentBuilder().parse(file)
 private fun png() = File(res, "drawable-nodpi/bme_logo.png").readBytes()
 private fun dimension(bytes: ByteArray, offset: Int) = ByteBuffer.wrap(bytes, offset, 4).int
 private fun dp(value: String) = value.removeSuffix("dp").toDouble()
 @Test fun splashUsesPaddedWrapperInsteadOfRawBitmap() {
  val theme = xml(File(res, "values-v31/styles.xml"))
  val items = theme.getElementsByTagName("item")
  val icon = (0 until items.length).map { items.item(it) }.first { it.attributes.getNamedItem("name").nodeValue == "android:windowSplashScreenAnimatedIcon" }
  assertEquals("@drawable/splash_logo", icon.textContent.trim())
 }
 @Test fun logoFitsEvenTheSmaller160DpMaskWithCorrectAspect() {
  val doc = xml(File(res, "drawable/splash_logo.xml"))
  val size = doc.getElementsByTagName("size").item(0).attributes
  assertEquals(288.0, dp(size.getNamedItem("android:width").nodeValue), 0.01)
  assertEquals(288.0, dp(size.getNamedItem("android:height").nodeValue), 0.01)
  val items = doc.getElementsByTagName("item")
  val item = (0 until items.length).map { items.item(it).attributes }.first { it.getNamedItem("android:drawable")?.nodeValue == "@drawable/bme_logo" }
  val width = dp(item.getNamedItem("android:width").nodeValue)
  val height = dp(item.getNamedItem("android:height").nodeValue)
  assertEquals("center", item.getNamedItem("android:gravity").nodeValue)
  assertTrue("All logo corners must fit the circular safe area", hypot(width / 2, height / 2) < 80.0)
  val image = png()
  assertTrue(abs(width / height - dimension(image, 16).toDouble() / dimension(image, 20)) < 0.005)
 }
 @Test fun suppliedLogoHasEnoughPixelsAndRetainsTransparency() {
  val image = png()
  assertTrue(dimension(image, 16) >= 256 && dimension(image, 20) >= 256)
  assertTrue(image[25].toInt() == 6)
 }
}
