package dev.garnetforge.app.data.model

sealed interface SpeedTestState {
    val downloadMbps: Float
    val uploadMbps: Float
    val msg: String

    data object Idle : SpeedTestState {
        override val downloadMbps = 0f
        override val uploadMbps = 0f
        override val msg = ""
    }

    data object Running : SpeedTestState {
        override val downloadMbps = 0f
        override val uploadMbps = 0f
        override val msg = "Running"
    }

    data class Done(
        override val downloadMbps: Float = 0f,
        override val uploadMbps: Float = 0f,
        override val msg: String = "Done",
    ) : SpeedTestState

    data class Error(override val msg: String) : SpeedTestState {
        override val downloadMbps = 0f
        override val uploadMbps = 0f
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
