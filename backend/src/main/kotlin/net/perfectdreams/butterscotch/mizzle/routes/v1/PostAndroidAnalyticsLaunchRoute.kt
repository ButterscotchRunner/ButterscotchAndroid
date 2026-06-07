package net.perfectdreams.butterscotch.mizzle.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class PostAndroidAnalyticsLaunchRoute : APIv1Route("/analytics/android/launch") {
    override suspend fun onRequest(call: ApplicationCall) {
        call.respond(HttpStatusCode.NoContent)
    }
}