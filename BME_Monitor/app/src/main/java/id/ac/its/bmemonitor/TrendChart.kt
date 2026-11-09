package id.ac.its.bmemonitor

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import java.util.Locale

@Composable
fun TrendChart(points: List<TrendPoint>, startMs: Long, endMs: Long, heartRate: Boolean, originMs: Long = startMs, holdUntilMs: Long = endMs) {
 val density = LocalDensity.current
 val colors = MaterialTheme.colorScheme
 val line = if (heartRate) colors.primary else colors.tertiary
 val label = colors.onSurfaceVariant
 val range = max(1L, endMs - startMs)
 val segments = remember(points, startMs, endMs, holdUntilMs, heartRate) { chartSegments(points, startMs, minOf(endMs, holdUntilMs), heartRate) }
 val yMax = if (heartRate) max(140.0, ceil(((points.mapNotNull { it.hr }.maxOrNull() ?: 120.0) + 10) / 20) * 20) else 100.0
 val unit = if (heartRate) "BPM" else "%"
 val labelSize = with(density) { 11.sp.toPx() }
 val labelPaint = remember(labelSize) { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = labelSize } }
 val left = max(labelPaint.measureText(yMax.toInt().toString()), labelPaint.measureText(unit)) + with(density) { 12.dp.toPx() }
 val right = with(density) { 12.dp.toPx() }
 val top = labelSize + with(density) { 12.dp.toPx() }
 val bottom = labelSize + with(density) { 16.dp.toPx() }
 var selected by remember { mutableStateOf<Long?>(null) }
 val latestPoints by rememberUpdatedState(points)
 val latestStart by rememberUpdatedState(startMs)
 val latestEnd by rememberUpdatedState(endMs)
 val picked = points.find { it.elapsedMs == selected && it.elapsedMs in startMs..endMs }
 val title = if (heartRate) "Detak jantung" else "SpO\u2082"
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
  Box(contentAlignment = Alignment.Center) {
   Canvas(Modifier.fillMaxWidth().height(if (density.fontScale > 1.4f) 270.dp else 200.dp)
    .semantics { contentDescription = "Grafik $title. Sumbu Y $unit. Sumbu X waktu dalam detik. Garis putus-putus menahan nilai terakhir, bukan sampel baru." }
    .pointerInput(left, right) {
     detectTapGestures { tap ->
      val width = (size.width - left - right).coerceAtLeast(1f)
      val target = latestStart + ((tap.x - left) / width).coerceIn(0f, 1f) * (latestEnd - latestStart)
      selected = latestPoints.filter { it.elapsedMs in latestStart..latestEnd }.minByOrNull { abs(it.elapsedMs - target) }?.elapsedMs
     }
    }) {
    val width = (size.width - left - right).coerceAtLeast(1f)
    val height = (size.height - top - bottom).coerceAtLeast(1f)
    fun x(t: Long) = left + ((t - startMs).toDouble() / range * width).toFloat()
    fun y(v: Double) = top + height - (v / yMax * height).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.toArgb(); textSize = labelSize }
    paint.textAlign = Paint.Align.RIGHT
    drawContext.canvas.nativeCanvas.drawText(unit, left - 8.dp.toPx(), labelSize, paint)
    for (i in 0..4) {
     val value = yMax * i / 4
     drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(left, y(value)), Offset(left + width, y(value)), 0.7.dp.toPx())
     drawContext.canvas.nativeCanvas.drawText(value.toInt().toString(), left - 8.dp.toPx(), y(value) + paint.textSize / 3, paint)
    }
    val ticks = if (density.fontScale > 1.4f) 2 else 4
    for (i in 0..ticks) {
     val t = startMs + range * i / ticks
     paint.textAlign = when(i) { 0 -> Paint.Align.LEFT; ticks -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
     val seconds = (t - originMs) / 1000.0
     val caption = String.format(Locale.getDefault(), if (range < 5000) "%.1f" else "%.0f", seconds)
     drawContext.canvas.nativeCanvas.drawText(caption, x(t), top + height + labelSize + 8.dp.toPx(), paint)
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    segments.forEach { segment ->
     drawLine(line.copy(alpha = if (segment.held) 0.65f else 1f),
      Offset(x(segment.fromMs), y(segment.fromValue)), Offset(x(segment.toMs), y(segment.toValue)),
      strokeWidth = 2.5.dp.toPx(), pathEffect = if (segment.held) dash else null)
    }
    val stride = max(1, points.size / 120)
    points.forEachIndexed { index, point ->
     val value = if (heartRate) point.hr else point.spo2
     if (value != null && point.elapsedMs in startMs..endMs && index % stride == 0) {
      val cx = x(point.elapsedMs); val cy = y(value)
      if (heartRate && point.highHr) {
       val radius = 3.5.dp.toPx()
       drawPath(Path().apply { moveTo(cx, cy - radius); lineTo(cx - radius, cy + radius); lineTo(cx + radius, cy + radius); close() }, colors.error)
      } else drawCircle(line, 2.dp.toPx(), Offset(cx, cy))
     }
    }
    picked?.let { point -> drawLine(label.copy(alpha = 0.6f), Offset(x(point.elapsedMs), top), Offset(x(point.elapsedMs), top + height), 1.dp.toPx()) }
   }
   if (segments.isEmpty() && points.none { it.elapsedMs in startMs..minOf(endMs, holdUntilMs) && (if (heartRate) it.hr else it.spo2) != null }) Text("Menunggu data", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
  }
  Text("Waktu (s)", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium, color = label)
  picked?.let { point ->
   val value = if (heartRate) point.hr else point.spo2
   val seconds = (point.elapsedMs - originMs) / 1000.0
   Text(String.format(Locale.getDefault(), "%.1f s  |  ", seconds) + (value?.let { String.format(Locale.getDefault(), "%.1f %s", it, unit) } ?: "Data tidak valid"), style = MaterialTheme.typography.labelMedium, color = line)
  }
 }
}
