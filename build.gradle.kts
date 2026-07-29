
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
}

group = "no.nav.tsm"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.di)
    implementation(ktorLibs.serialization.jackson)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)

    implementation(tsmKtorLibs.core)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.prometheus.client)
    implementation(libs.prometheus.hotspot)
    implementation(libs.google.cloud.storage)
    implementation(libs.apache.kafka)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.test.junit)
}
