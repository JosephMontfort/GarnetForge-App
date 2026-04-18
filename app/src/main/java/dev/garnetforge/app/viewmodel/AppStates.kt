package dev.garnetforge.app

sealed class SpeedTestState {
    object Idle    : SpeedTestState()
    data class Running(val phase: String = "download", val fraction: Float = 0f, val currentMbps: Float = 0f) : SpeedTestState()
    data class Done(val downloadMbps: Float, val uploadMbps: Float) : SpeedTestState()
    data class Error(val msg: String) : SpeedTestState()
}

sealed class DiagnosticState {
    object Idle    : DiagnosticState()
    object Running : DiagnosticState()
    data class Done(val filePath: String) : DiagnosticState()
    data class Error(val msg: String) : DiagnosticState()
}
