package net.perfectdreams.butterscotch.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

object ButterscotchUtils {
    val http by lazy {
        HttpClient(CIO) {
            expectSuccess = false
        }
    }
}