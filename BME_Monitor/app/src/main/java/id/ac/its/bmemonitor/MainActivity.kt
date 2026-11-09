package id.ac.its.bmemonitor

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.animation.ValueAnimator
import android.os.Build
import android.view.animation.PathInterpolator
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  if (Build.VERSION.SDK_INT >= 31) {
   splashScreen.setOnExitAnimationListener { splash ->
    if (!ValueAnimator.areAnimatorsEnabled()) splash.remove() else {
     splash.animate().alpha(0f).setDuration(220).setInterpolator(PathInterpolator(0.4f, 0f, 1f, 1f))
      .withEndAction { splash.remove() }.start()
    }
   }
  }
  enableEdgeToEdge()
  setContent {
   val dark = isSystemInDarkTheme()
   val scheme = if (dark) darkColorScheme(primary = Color(0xFF7AD3ED), onPrimary = Color(0xFF003545),
    tertiary = Color(0xFFFFA9CB), background = Color(0xFF091A23), surface = Color(0xFF10252F),
    onSurface = Color(0xFFE7F3F7), onBackground = Color(0xFFE7F3F7), onSurfaceVariant = Color(0xFFB9CFDA),
    secondaryContainer = Color(0xFF203D4A), onSecondaryContainer = Color(0xFFD6EDF7),
    outlineVariant = Color(0xFF4B6571), error = Color(0xFFFFB3B9))
   else lightColorScheme(primary = Color(0xFF006786), onPrimary = Color.White, tertiary = Color(0xFF963059),
    background = Color(0xFFF2F7FA), surface = Color.White, onSurface = Color(0xFF17364A), onBackground = Color(0xFF17364A),
    onSurfaceVariant = Color(0xFF49616E), secondaryContainer = Color(0xFFDCEEF6), onSecondaryContainer = Color(0xFF17364A),
    outlineVariant = Color(0xFFB8CBD4), error = Color(0xFFA62336))
   MaterialTheme(colorScheme = scheme) { MonitorApp() }
  }
 }
}

@Composable
private fun MonitorApp(vm: MonitorViewModel = viewModel()) {
 val state by vm.state.collectAsStateWithLifecycle()
 val lifecycle = LocalLifecycleOwner.current.lifecycle
 val context = LocalContext.current
 var audioStatus by remember { mutableStateOf("Menyiapkan suara Indonesia offline...") }
 val snack = remember { SnackbarHostState() }
 val scope = rememberCoroutineScope()
 val entry = remember { Animatable(0f) }
 LaunchedEffect(Unit) { entry.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
 val homeScroll = rememberScrollState(); val historyScroll = rememberScrollState(); val settingsScroll = rememberScrollState()
 val audio = remember(context) { AudioController(context) { audioStatus = it } }
 var listening by remember { mutableStateOf(false) }
 var voiceStatus by remember { mutableStateOf("") }
 val handleCommand by rememberUpdatedState<(VoiceCommand) -> Unit>({ command ->
  val current = vm.state.value
  val notice = when (command) {
   VoiceCommand.START -> if (current.acquiring) "Rekaman sudah berjalan." else { vm.startAcquisition(); vm.state.value.message }
   VoiceCommand.STOP -> if (!current.acquiring) "Tidak ada rekaman yang berjalan." else { audio.stop(); vm.stopAcquisition(); vm.state.value.message }
   VoiceCommand.READ -> when {
    !current.audioEnabled -> "Aktifkan audio di Pengaturan untuk membacakan hasil."
    !current.canSpeak -> "Data valid terbaru belum tersedia."
    !audio.speak(SessionCodec.speech(current.point, current.fresh)) -> audioStatus
    else -> "Membacakan data terbaru."
   }
  }
  scope.launch { snack.showSnackbar(notice) }
 })
 val voice = remember(context) { VoiceController(context, { active, text -> listening = active; voiceStatus = text }, { handleCommand(it) }) }
 DisposableEffect(voice) { onDispose { voice.cancel() } }
 val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
  if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { audio.stop(); voice.start() }
  else voiceStatus = "Izin mikrofon diperlukan untuk perintah suara. Tombol manual tetap tersedia."
 }
 val startVoice: () -> Unit = {
  if (listening) voice.cancel() else {
   audio.stop()
   if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start()
   else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
  }
 }

 DisposableEffect(audio) { onDispose { audio.close() } }
 LaunchedEffect(state.audioEnabled) { audio.enabled = state.audioEnabled }
 LaunchedEffect(state.fresh, state.point?.hr == null, state.point?.spo2 == null) { if (!state.fresh || state.point?.hr == null || state.point?.spo2 == null) audio.stop() }
 LaunchedEffect(vm, lifecycle, audio) {
  lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   vm.sounds.collect { event -> if (SystemClock.elapsedRealtime() - event.elapsedMs < 2000) audio.play(event.kind) }
  }
 }
 DisposableEffect(lifecycle, vm, audio, voice) {
  val observer = LifecycleEventObserver { _, event ->
   if (event == Lifecycle.Event.ON_START) vm.setForeground(true)
   if (event == Lifecycle.Event.ON_STOP) { voice.cancel(); audio.stop(); vm.setForeground(false, stopRecording = (context as? Activity)?.isChangingConfigurations != true) }
  }
  lifecycle.addObserver(observer)
  vm.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
  onDispose { lifecycle.removeObserver(observer); audio.stop() }
 }
 var tab by rememberSaveable { mutableIntStateOf(0) }
 BackHandler(enabled = tab != 0) {
  if (tab == 1 && (state.selectedSession != null || state.loadingSession || state.sessionError != null)) vm.clearSelection() else tab = 0
 }
 Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
  NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
   val icons = listOf(Icons.Default.Favorite, Icons.AutoMirrored.Filled.List, Icons.Default.Settings)
   listOf("Pantau", "Riwayat", "Pengaturan").forEachIndexed { i, label ->
    NavigationBarItem(selected = tab == i, onClick = { voice.cancel(); tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label) })
   }
  }
 }) { padding ->
  BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
   val wide = maxWidth >= 600.dp
   val metricsSideBySide = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.3f
   val scroll = when(tab) { 0 -> homeScroll; 1 -> historyScroll; else -> settingsScroll }
   LaunchedEffect(state.selectedSession?.summary?.id, state.loadingSession) { if (tab == 1) historyScroll.scrollTo(0) }
   Column(Modifier.align(Alignment.TopCenter).widthIn(max = 900.dp).fillMaxWidth().verticalScroll(scroll)
    .imePadding().padding(if (wide) 28.dp else 16.dp).graphicsLayer { alpha = entry.value; translationY = (1 - entry.value) * 8.dp.toPx() }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
     Surface(shape = RoundedCornerShape(14.dp), color = Color.White) { Image(painterResource(R.drawable.bme_logo), "BME", Modifier.padding(8.dp).size(40.dp)) }
     Spacer(Modifier.width(12.dp))
     Column(Modifier.weight(1f)) {
      Text("Project HMI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(state.config.name.ifBlank { "Pemantauan sensor" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
    when (tab) {
     0 -> MonitorContent(state, metricsSideBySide, vm::startAcquisition,
      { audio.stop(); vm.stopAcquisition() }, vm::retrySave, {
       if (!audio.speak(SessionCodec.speech(state.point, state.fresh))) {
        val notice = audioStatus
        scope.launch { snack.showSnackbar(notice) }
       }
      }, { voice.cancel(); tab = 2 }, startVoice, listening, voiceStatus)
     1 -> HistoryContent(state, vm::selectSession, vm::clearSelection)
     else -> SettingsContent(state, audioStatus, vm::setAudioEnabled, vm::saveConfig)
    }
    Spacer(Modifier.height(4.dp))
   }
  }
 }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
 Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
 }
}
private fun number(n: Double?, decimals: Int = 0): String = n?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "-"
private fun time(t: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")).format(Date(t))
private fun duration(ms: Long): String {
 val seconds = ms.coerceAtLeast(0) / 1000
 return if (seconds >= 3600) "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun Metric(title: String, value: Double?, unit: String, high: Boolean = false, modifier: Modifier = Modifier, held: String? = null) {
 Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
   Text(title, style = MaterialTheme.typography.titleSmall)
   Text(number(value), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
   Text(unit, style = MaterialTheme.typography.bodyMedium)
   held?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
   if (high) Text(">120 BPM", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
  }
 }
}

@Composable
private fun MonitorContent(s: MonitorState, sideBySide: Boolean, start: () -> Unit, stop: () -> Unit,
 retry: () -> Unit, speak: () -> Unit, settings: () -> Unit, voice: () -> Unit, listening: Boolean, voiceStatus: String) {
 Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
  Text(if (s.connected) "Terhubung" else "Menghubungkan...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
  Text(s.reading?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it.receivedAt)) } ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
 }
 if (s.lastError != null) Panel {
  Text("Koneksi gagal", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
  TextButton(onClick = settings) { Text("Periksa koneksi") }
 }
 val currentHr = s.point?.hr.takeIf { s.fresh }; val currentSp = s.point?.spo2.takeIf { s.fresh }
 val hr = currentHr ?: s.lastValid.hr?.value; val sp = currentSp ?: s.lastValid.spo2?.value
 val heldHr = s.lastValid.hr?.takeIf { currentHr == null }?.let { "Terakhir · ${it.ageSeconds(s.nowElapsed)} dtk lalu" }
 val heldSp = s.lastValid.spo2?.takeIf { currentSp == null }?.let { "Terakhir · ${it.ageSeconds(s.nowElapsed)} dtk lalu" }
 if (sideBySide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  Metric("Detak jantung", hr, "BPM", hr != null && hr > 120, Modifier.weight(1f), heldHr)
  Metric("Oksigen", sp, "% SpO\u2082", modifier = Modifier.weight(1f), held = heldSp)
 } else {
  Metric("Detak jantung", hr, "BPM", hr != null && hr > 120, Modifier.fillMaxWidth(), heldHr)
  Metric("Oksigen", sp, "% SpO\u2082", modifier = Modifier.fillMaxWidth(), held = heldSp)
 }
 Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
  Text(when {
   !s.connected -> "Menunggu koneksi"
   s.reading == null -> "Menunggu sensor"
   !s.fresh -> "Data belum diperbarui"
   !s.reading.finger -> "Letakkan jari pada sensor"
   currentHr == null || currentSp == null -> if (hr != null || sp != null) "Menampilkan nilai terakhir" else "Membaca sensor..."
   else -> "Live"
  }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  TextButton(onClick = speak, enabled = s.audioEnabled && s.canSpeak, modifier = Modifier.heightIn(min = 48.dp)) { Text("Bacakan") }
 }
 Panel {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
   Text(if (s.acquiring) "Sedang merekam" else "Rekaman", style = MaterialTheme.typography.titleSmall)
   if (s.acquiring) Text(duration(s.nowElapsed - (s.recordingStarted ?: s.nowElapsed)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
  }
  Button(onClick = if (s.acquiring) stop else start,
   enabled = s.acquiring || (s.connected && !s.savingSession && !s.unsavedSession),
   modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (s.acquiring) "Stop & simpan" else "Mulai rekam") }
  if (s.savingSession) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Menyimpan...", style = MaterialTheme.typography.bodySmall) }
  if (s.unsavedSession && !s.savingSession) Button(onClick = retry) { Text("Ulangi simpan") }
 }
 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
  OutlinedButton(onClick = voice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
   Text(if (listening) "Batalkan mikrofon" else "Perintah suara")
  }
  Text("Mulai rekam · Stop rekam · Bacakan hasil", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  if (voiceStatus.isNotBlank()) Text(voiceStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
 }
 val origin = s.liveStartedElapsed ?: s.nowElapsed
 val end = maxOf(s.nowElapsed, origin + 10000)
 val startTime = maxOf(origin, s.nowElapsed - 60000)
 Panel { TrendChart(s.trend, startTime, end, heartRate = true, originMs = origin, holdUntilMs = s.nowElapsed) }
 Panel { TrendChart(s.trend, startTime, end, heartRate = false, originMs = origin, holdUntilMs = s.nowElapsed) }
}

@Composable
private fun HistoryContent(s: MonitorState, select: (String) -> Unit, back: () -> Unit) {
 Text("Riwayat sesi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
 if (s.loadingSession || s.sessionError != null || s.selectedSession != null) {
  TextButton(onClick = back) { Text("Kembali ke daftar sesi") }
  if (s.loadingSession) CircularProgressIndicator()
  s.sessionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  s.selectedSession?.let { record -> SessionDetail(record) }
  return
 }
 if (s.savingSession) Text("Sesi terakhir sedang disimpan...")
 if (s.sessions.isEmpty()) Panel { Text("Belum ada rekaman sesi."); Text("Rekam pengukuran dari halaman Pantau.") }
 var historyLimit by rememberSaveable { mutableIntStateOf(50) }
 s.sessions.take(historyLimit).forEach { summary ->
  Panel(Modifier.clickable(onClickLabel = "Lihat detail sesi", role = Role.Button) { select(summary.id) }) {
   Text(time(summary.startedAt), fontWeight = FontWeight.Bold)
   Text("Rerata ${number(summary.meanHr, 1)} BPM | ${number(summary.meanSpo2, 1)}% SpO\u2082", style = MaterialTheme.typography.titleMedium)
   Text("${duration(summary.durationMs)} | ${summary.samples} sampel | ${summary.name}", style = MaterialTheme.typography.bodySmall)
  }
 }
 if (s.sessions.size > historyLimit) OutlinedButton(onClick = { historyLimit += 50 }) { Text("Tampilkan sesi lainnya") }
 if (s.history.isNotEmpty()) {
  Text("Snapshot versi sebelumnya", style = MaterialTheme.typography.titleMedium)
  s.history.forEach { old -> Panel {
   Text(time(old.time)); Text("${number(old.hr)} BPM | ${number(old.spo2)}% SpO\u2082")
   Text("Pengukuran tersimpan", style = MaterialTheme.typography.bodySmall)
  } }
 }
}

@Composable
private fun SessionDetail(record: SessionRecord) {
 val s = record.summary
 var details by remember(record.summary.id) { mutableStateOf(false) }
 var raw by remember(record.summary.id) { mutableStateOf(false) }
 Panel {
  Text(time(s.startedAt), fontWeight = FontWeight.Bold)
  Text("${duration(s.durationMs)} | ${s.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Text("Rata-rata", style = MaterialTheme.typography.titleMedium)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
   Column(Modifier.weight(1f)) {
    Text(number(s.meanHr, 1), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Text("BPM", style = MaterialTheme.typography.labelMedium)
   }
   Column(Modifier.weight(1f)) {
    Text(number(s.meanSpo2, 1), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
    Text("% SpO\u2082", style = MaterialTheme.typography.labelMedium)
   }
  }
 }
 Panel { TrendChart(record.points, 0, s.durationMs.coerceAtLeast(1), heartRate = true) }
 Panel { TrendChart(record.points, 0, s.durationMs.coerceAtLeast(1), heartRate = false) }
 TextButton(onClick = { details = !details }) { Text(if (details) "Tutup detail" else "Detail pengukuran") }
 if (details) Panel {
  Text("${s.samples} sampel | ${s.device}", fontWeight = FontWeight.SemiBold)
  Text("BPM: ${s.hrCount} digunakan, ${s.excludedHr} disisihkan.")
  Text("SpO\u2082: ${s.spo2Count} digunakan, ${s.excludedSpo2} disisihkan.")
  Text("${s.highHrCount} sampel BPM >120.")
  Text("Rata-rata dihitung dari sampel yang diterima filter.", style = MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick = { raw = !raw }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (raw) "Tutup data mentah" else "Data mentah") }
  if (raw) {
   Text("30 sampel terakhir", style = MaterialTheme.typography.labelMedium)
   record.points.takeLast(30).forEach { p ->
    Text("${number(p.elapsedMs / 1000.0, 1)} s | BPM ${number(p.rawHr)} -> ${number(p.hr)} | SpO\u2082 ${number(p.rawSpo2)} -> ${number(p.spo2)}", style = MaterialTheme.typography.bodySmall)
    p.hrReason?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    p.spo2Reason?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    HorizontalDivider()
   }
  }
 }
}

@Composable
private fun SettingsContent(s: MonitorState, audioStatus: String, audioChanged: (Boolean) -> Unit, save: (BrokerConfig) -> Unit) {
 var form by remember(s.config) { mutableStateOf(s.config) }
 Text("Pengaturan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
 Panel {
  Row(verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) { Text("Suara aplikasi", fontWeight = FontWeight.Bold); Text("Bunyi status dan pembacaan hasil", style = MaterialTheme.typography.bodySmall) }
   Switch(checked = s.audioEnabled, onCheckedChange = audioChanged, modifier = Modifier.semantics { contentDescription = "Suara aplikasi" })
  }
  Text(audioStatus, style = MaterialTheme.typography.bodySmall)
 }
 Panel {
  Text("Profil lokal", style = MaterialTheme.typography.titleMedium)
  OutlinedTextField(form.name, { form = form.copy(name = it) }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(form.email, { form = form.copy(email = it) }, label = { Text("Email (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
 }
 Panel {
  Text("Koneksi HiveMQ", style = MaterialTheme.typography.titleMedium)
  OutlinedTextField(form.host, { form = form.copy(host = it) }, label = { Text("Hostname broker") }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(form.port, { form = form.copy(port = it) }, label = { Text("Port TLS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(form.username, { form = form.copy(username = it) }, label = { Text("Username MQTT") }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(form.password, { form = form.copy(password = it) }, label = { Text("Password MQTT") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(form.topic, { form = form.copy(topic = it) }, label = { Text("Topic vitals lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(form.statusTopic, { form = form.copy(statusTopic = it) }, label = { Text("Topic status lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
  Text(s.message, style = MaterialTheme.typography.bodySmall)
  s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
  Button(onClick = { save(form) }, enabled = !s.acquiring && !s.savingSession && !s.unsavedSession, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Simpan & hubungkan") }
 }
 Text("Perintah suara memakai mikrofon dan layanan pengenalan sistem HP, yang dapat memerlukan internet. Mikrofon hanya aktif setelah tombol ditekan; aplikasi tidak menyimpan rekaman suara.", style = MaterialTheme.typography.bodySmall)
 Text("Versi 1.4.3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
