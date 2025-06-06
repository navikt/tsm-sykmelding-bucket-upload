package no.nav.tsm.sykmelding

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import io.prometheus.client.Counter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.tsm.utils.gzip
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class SykmeldingConsumer(
    val kafkaConsumer: KafkaConsumer<String, String>,
    val topics: List<String>,
    val bucketName: String,
    val storage: Storage
) {

    companion object {
        private val logger = LoggerFactory.getLogger(SykmeldingConsumer::class.java)
        val securelog: Logger = LoggerFactory.getLogger("securelog")
        private val STORAGE_METRIC = Counter.Builder()
            .namespace("tsm")
            .name("sykmelding_bucket_upload")
            .help("counts number for files uploaded or not to gcp")
            .labelNames("type").create()
    }

    private val objectMapper = jacksonObjectMapper().apply {
        registerModules(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    suspend fun start() = coroutineScope {
        kafkaConsumer.subscribe(topics)
        while (isActive) {
            try {
                consumeMessages()
            } catch (ex: Exception) {
                logger.error("Error processing messages ${ex.stackTrace} ${ex.message}", ex)
                kafkaConsumer.unsubscribe()
                delay(60.seconds)
            }
        }
        kafkaConsumer.unsubscribe()
    }

    private suspend fun consumeMessages() = coroutineScope {
        kafkaConsumer.subscribe(topics)
        while (isActive) {
            val records = kafkaConsumer.poll(10.seconds.toJavaDuration())
            records.forEach { record ->
                val sykmeldingId = record.key()
                val sykmeldingRecord: SykmeldingRecord? = record.value()?.let { objectMapper.readValue(it) }
                when (sykmeldingRecord) {
                    null -> deleteXml(sykmeldingId)
                    else -> uploadXml(sykmeldingId, sykmeldingRecord.fellesformat)
                }
            }
        }
    }

    private fun deleteXml(sykmeldingId: String) {
        logger.info("deleting xml for sykmelding $sykmeldingId")
        storage.delete(BlobId.of(bucketName, sykmeldingId))
        STORAGE_METRIC.labels("delete").inc()
    }

    private fun uploadXml(sykmeldingId: String, fellesformat: String?) {
        try {
            if (fellesformat == null) {
                STORAGE_METRIC.labels("empty").inc()
                return
            }
            val sykmeldingInBucket = storage.get(bucketName, sykmeldingId)
            if (sykmeldingInBucket == null) {
                val blob = BlobInfo.newBuilder(BlobId.of(bucketName, "$sykmeldingId/sykmelding.xml"))
                    .setContentType("application/xml")
                    .setContentEncoding("gzip")
                    .build()
                val compressedData = gzip(fellesformat)
                storage.create(blob, compressedData)
                STORAGE_METRIC.labels("upload").inc()
            }

        } catch (ex: Exception) {
            logger.error("Error uploading xml for sykmeldingId $sykmeldingId ${ex.message} ${ex.stackTrace}", ex)
        }
    }
}
