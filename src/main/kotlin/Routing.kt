package no.nav.tsm

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.tsm.sykmelding.nais.registerNaisApi

fun Application.configureRouting() {
    routing { registerNaisApi() }
}
