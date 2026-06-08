package net.perfectdreams.butterscotch.mizzle

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.localPort
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
    val http = HttpClient(Java) {
        expectSuccess = false
    }

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

        val server = embeddedServer(
            Netty,
            configure = {
                connectors.add(EngineConnectorBuilder().apply {
                    host = "0.0.0.0"
                    port = 8080
                })
            }
        ) {
            routing {
                localPort(8080) {
                    get("/") {
                        call.respondText("ButterscotchRunner (Mizzle Backend) - Howdy! Loritta is so cute!!")
                    }

                    for (route in routes) {
                        route.register(this)
                    }
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