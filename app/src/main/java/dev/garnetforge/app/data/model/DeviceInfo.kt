package dev.garnetforge.app.data.model

data class DeviceInfo(
    val deviceName: String    = "Unknown Device",
    val codename: String      = "",
    val socModel: String      = "",
    val kernelVersion: String = "",
    val androidVersion: String= "",
    val romName: String       = "",
    val cpuHardware: String   = "",
)
