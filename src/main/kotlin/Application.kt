package no.nav.tsm

import io.ktor.server.application.*
import no.nav.tsm.plugins.setUpKafkaConsumers
import org.koin.ktor.ext.get

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureFrameworks()
    configureRouting()
    setUpKafkaConsumers(get())
}

