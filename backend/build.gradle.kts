plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "net.perfectdreams.butterscotch.mizzle"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-server-netty:3.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.34")

    implementation("org.jetbrains.exposed:exposed-core:1.3.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    implementation("org.jetbrains.exposed:exposed-java-time:1.3.0")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("com.zaxxer:HikariCP:7.0.2")
}

tasks.test {
    useJUnitPlatform()
}
