package net.perfectdreams.butterscotch.mizzle.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object LaunchedGames : LongIdTable() {
    val gameName = text("game_name")
    val wadVersion = integer("wad_version")
    val wadHash = text("wad_hash")
    val appVersion = integer("app_version")
    val gmsVersion = text("gms_version")
    val detectedGMSVersion = text("detected_gms_version")
    val launchedAt = timestampWithTimeZone("launched_at")
}