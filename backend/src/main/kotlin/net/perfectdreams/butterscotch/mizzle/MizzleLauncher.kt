package net.perfectdreams.butterscotch.mizzle

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.IsolationLevel
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database

object MizzleLauncher {
    private val DRIVER_CLASS_NAME = "org.postgresql.Driver"
    private val ISOLATION_LEVEL = IsolationLevel.TRANSACTION_REPEATABLE_READ // We use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!

    @JvmStatic
    fun main(args: Array<String>) {
        val hikariConfig = createHikariConfig() {}
        hikariConfig.jdbcUrl = "jdbc:postgresql://127.0.0.1/mizzle"

        hikariConfig.username = "postgres"
        hikariConfig.password = "postgres"

        val hikariDataSource = HikariDataSource(hikariConfig)

        val m = Mizzle(
            Database.connect(
                hikariDataSource,
                databaseConfig = DatabaseConfig {
                    defaultIsolationLevel = ISOLATION_LEVEL.levelId // Change our default isolation level
                }
            )
        )
        m.start()
    }

    private fun createHikariConfig(builder: HikariConfig.() -> (Unit)): HikariConfig {
        val hikariConfig = HikariConfig()

        hikariConfig.driverClassName = DRIVER_CLASS_NAME

        // https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert
        hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true")

        // Exposed uses autoCommit = false, so we need to set this to false to avoid HikariCP resetting the connection to
        // autoCommit = true when the transaction goes back to the pool, because resetting this has a "big performance impact"
        // https://stackoverflow.com/a/41206003/7271796
        hikariConfig.isAutoCommit = false

        // Useful to check if a connection is not returning to the pool, will be shown in the log as "Apparent connection leak detected"
        hikariConfig.leakDetectionThreshold = 30L * 1000
        hikariConfig.transactionIsolation = ISOLATION_LEVEL.name // We use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!

        hikariConfig.maximumPoolSize = 16
        hikariConfig.poolName = "PuddingPool"

        hikariConfig.apply(builder)

        return hikariConfig
    }
}