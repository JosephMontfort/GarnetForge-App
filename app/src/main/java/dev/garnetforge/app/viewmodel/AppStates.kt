package dev.garnetforge.app

sealed class SpeedTestState {
    object Idle    : SpeedTestState()
    object Running : SpeedTestState()
    data class Done(val downloadMbps: Float, val uploadMbps: Float) : SpeedTestState()
    data class Error(val msg: String) : SpeedTestState()
}

sealed class DiagnosticState {
    object Idle    : DiagnosticState()
    object Running : DiagnosticState()
    data class Done(val filePath: String) : DiagnosticState()
    data class Error(val msg: String) : DiagnosticState()
}
