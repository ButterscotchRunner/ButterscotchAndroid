package net.perfectdreams.butterscotch.network

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsLaunchRequest(
    val gameName: String,
    val wadVersion: Int,
    val wadHash: String,
    val appVersion: String,
    val gmsVersion: String,
    val detectedGMSVersion: String
)