package no.nav.tsm.sykmelding

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.apache.kafka.clients.consumer.KafkaConsumer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import io.prometheus.client.Counter

class SykmeldingConsumer(
    val kafkaConsumer: KafkaConsumer<String, String>,
    val topics: List<String>,
    val bucketName: String,
    val storage: Storage
) {

    companion object {
        private val STORAGE_METRIC = Counter.Builder()
            .namespace("tsm")
            .name("sykmelding-bucket-upload")
            .labelNames("type").create()
    }

    private val objectMapper = jacksonObjectMapper().apply {
        registerModules(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    suspend fun start() = coroutineScope {
        while (isActive) {
            val records = kafkaConsumer.poll(10.seconds.toJavaDuration())
            records.forEach { record ->
                val sykmeldingId = record.key()
                val sykmeldingRecord: SykmeldingRecord? = record.value().let { objectMapper.readValue(it) }

                when (sykmeldingRecord) {
                    null -> deleteXml(sykmeldingId)
                    else -> uploadXml(sykmeldingId, sykmeldingRecord.fellesformat)
                }
            }
        }
    }

    private fun deleteXml(sykmeldingId: String) {
        storage.delete(BlobId.of(bucketName, sykmeldingId))
        STORAGE_METRIC.labels("delete").inc()
    }

    private fun uploadXml(sykmeldingId: String, fellesformat: String?) {
        if (fellesformat != null) {
            val blob = BlobInfo.newBuilder(BlobId.of(bucketName, sykmeldingId))
                .setContentType("application/xml")
                .build()
            storage.create(blob, fellesformat.encodeToByteArray())
            STORAGE_METRIC.labels("upload").inc()
        } else {
            STORAGE_METRIC.labels("empty").inc()
        }
    }
}
