package no.nav.tsm

import io.ktor.server.application.*
import no.nav.tsm.plugins.setUpKafkaConsumers

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureFrameworks()
    configureRouting()
    setUpKafkaConsumers()
}

