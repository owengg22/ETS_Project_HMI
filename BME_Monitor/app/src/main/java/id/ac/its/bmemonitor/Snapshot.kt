package id.ac.its.bmemonitor

data class Snapshot(val time: Long, val hr: Double?, val spo2: Double?, val device: String, val name: String)
