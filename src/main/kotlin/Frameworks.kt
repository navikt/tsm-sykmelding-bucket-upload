package no.nav.tsm

import ch.qos.logback.core.util.OptionHelper.getEnv
import com.google.cloud.storage.StorageOptions
import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.nav.tsm.sykmelding.SykmeldingConsumer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.Properties

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        modules(
            kafkaModule()
        )
    }
}


fun kafkaModule() = module {
    single {
        val properties = Properties().apply {
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = getEnv("KAFKA_BROKERS")
            this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
            this[CommonClientConfigs.CLIENT_ID_CONFIG] = getEnv("HOSTNAME")
            this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
            this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
            this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = getEnv("KAFKA_TRUSTSTORE_PATH")
            this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = getEnv("KAFKA_CREDSTORE_PASSWORD")
            this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = getEnv("KAFKA_KEYSTORE_PATH")
            this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = getEnv("KAFKA_CREDSTORE_PASSWORD")
            this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = getEnv("KAFKA_CREDSTORE_PASSWORD")
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "true"
            this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
            this[ConsumerConfig.GROUP_ID_CONFIG] = "sykmelding-bucket-upload-consumer"
        }
        val bucketName = getEnv("TSM_SYKMELDING_BUCKET")
        val storage = StorageOptions.newBuilder().build().service
        SykmeldingConsumer(
            kafkaConsumer = KafkaConsumer<String, String>(properties),
            topics = listOf("tsm.teamsykmelding-sykmeldinger"),
            bucketName = bucketName,
            storage = storage,
        )
    }
}
