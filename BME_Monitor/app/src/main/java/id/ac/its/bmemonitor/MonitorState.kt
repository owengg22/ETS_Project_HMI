package id.ac.its.bmemonitor

data class MonitorState(
 val config: BrokerConfig = BrokerConfig(), val connected: Boolean = false, val acquiring: Boolean = false,
 val message: String = "Data live diterima otomatis saat broker terhubung.", val lastError: String? = null,
 val deviceStatus: String = "Belum diketahui", val reading: Reading? = null, val point: TrendPoint? = null,
 val nowElapsed: Long = 0, val liveStartedElapsed: Long? = null, val trend: List<TrendPoint> = emptyList(), val history: List<Snapshot> = emptyList(),
 val sessions: List<SessionSummary> = emptyList(), val selectedSession: SessionRecord? = null,
 val loadingSession: Boolean = false, val sessionError: String? = null,
 val recordingStarted: Long? = null, val recordingSamples: Int = 0, val savingSession: Boolean = false, val unsavedSession: Boolean = false,
 val lastValid: LastValidValues = LastValidValues(),
 val audioEnabled: Boolean = true
) {
 val fresh: Boolean get() = connected && reading?.isFresh(nowElapsed) == true
 val canSpeak: Boolean get() = fresh && (point?.hr != null || point?.spo2 != null)
}
