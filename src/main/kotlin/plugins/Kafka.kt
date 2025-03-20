package no.nav.tsm.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.tsm.sykmelding.SykmeldingConsumer

fun Application.setUpKafkaConsumers(sykmeldingConsumer: SykmeldingConsumer) {
    val kafkaConsumerJob = launch(Dispatchers.IO) {
        sykmeldingConsumer.start()
    }

    this.monitor.subscribe(ApplicationStopping) {
        kafkaConsumerJob.cancel()
    }
}
