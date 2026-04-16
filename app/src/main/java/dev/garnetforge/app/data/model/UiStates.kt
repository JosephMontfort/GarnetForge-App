package dev.garnetforge.app.data.model

sealed interface SpeedTestState {
    val downloadMbps: Double
    val uploadMbps: Double
    val msg: String

    data object Idle : SpeedTestState {
        override val downloadMbps = 0.0
        override val uploadMbps = 0.0
        override val msg = ""
    }

    data object Running : SpeedTestState {
        override val downloadMbps = 0.0
        override val uploadMbps = 0.0
        override val msg = "Running"
    }

    data class Done(
        override val downloadMbps: Double = 0.0,
        override val uploadMbps: Double = 0.0,
        override val msg: String = "Done",
    ) : SpeedTestState

    data class Error(override val msg: String) : SpeedTestState {
        override val downloadMbps = 0.0
        override val uploadMbps = 0.0
    }
}

sealed interface DiagnosticState {
    val filePath: String
    val msg: String

    data object Idle : DiagnosticState {
        override val filePath = ""
        override val msg = ""
    }

    data object Running : DiagnosticState {
        override val filePath = ""
        override val msg = "Running"
    }

    data class Done(
        override val filePath: String = "",
        override val msg: String = "Done",
    ) : DiagnosticState

    data class Error(
        override val msg: String,
        override val filePath: String = "",
    ) : DiagnosticState
}
