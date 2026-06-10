package net.perfectdreams.butterscotch.mizzle.config

import kotlinx.serialization.Serializable

@Serializable
data class MizzleConfig(
    val samplesPath: String,
    val database: DatabaseConfig
) {
    @Serializable
    data class DatabaseConfig(
        val database: String,
        val address: String,
        val username: String,
        val password: String
    )
}