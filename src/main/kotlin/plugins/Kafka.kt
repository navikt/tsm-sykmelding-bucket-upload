package no.nav.tsm.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.tsm.sykmelding.SykmeldingConsumer

fun Application.setUpKafkaConsumers() {
    val sykmeldingConsumer: SykmeldingConsumer by dependencies

    val kafkaConsumerJob = launch(Dispatchers.IO) {
        sykmeldingConsumer.start()
    }

    this.monitor.subscribe(ApplicationStopping) {
        kafkaConsumerJob.cancel()
    }
}
