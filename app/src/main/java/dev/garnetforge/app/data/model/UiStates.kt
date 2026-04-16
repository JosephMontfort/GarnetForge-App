package dev.garnetforge.app.data.model

sealed interface SpeedTestState {
    val downloadMbps: Double get() = 0.0
    val uploadMbps: Double get() = 0.0
    val msg: String get() = ""

    data object Idle : SpeedTestState
    data object Running : SpeedTestState
    data class Result(
        override val downloadMbps: Double,
        override val uploadMbps: Double,
        override val msg: String = "Speed test complete",
    ) : SpeedTestState
    data class Error(override val msg: String) : SpeedTestState
}

sealed interface DiagnosticState {
    val filePath: String get() = ""
    val msg: String get() = ""

    data object Idle : DiagnosticState
    data object Running : DiagnosticState
    data class Ready(override val filePath: String) : DiagnosticState
    data class Sharing(override val filePath: String) : DiagnosticState
    data class Error(override val msg: String) : DiagnosticState
}
