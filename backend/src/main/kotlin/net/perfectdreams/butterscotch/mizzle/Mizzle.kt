package net.perfectdreams.butterscotch.mizzle

import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.perfectdreams.butterscotch.mizzle.routes.v1.PostAndroidAnalyticsLaunchRoute
import net.perfectdreams.butterscotch.mizzle.routes.v1.SampleGamesRoute
import net.perfectdreams.butterscotch.mizzle.tables.LaunchedGames
import net.perfectdreams.butterscotch.mizzle.tables.SampleGames
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

class Mizzle(val database: Database) {
    val routes = listOf(
        SampleGamesRoute(this),
        PostAndroidAnalyticsLaunchRoute()
    )

    fun start() {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                SampleGames,
                LaunchedGames
            )
        }

        val server = embeddedServer(Netty, 8080) {
            routing {
                get("/") {
                    call.respondText("Mizzle - Howdy! Loritta is so cute!!")
                }

                for (route in routes) {
                    route.register(this)
                }

                staticFiles("/samples", File("/home/mrpowergamerbr/Projects/ButterscotchAndroid/samples"))
            }
        }
        server.start(wait = true)
    }

    suspend fun <T> transaction(statement: suspend JdbcTransaction.() -> (T)): T {
        return suspendTransaction(database) {
            statement()
        }
    }
}