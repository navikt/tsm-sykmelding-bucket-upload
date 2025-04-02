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
import no.nav.tsm.sykmelding.model.VedleggMessage
import no.nav.tsm.utils.gzip
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class SykmeldingConsumer(
    val kafkaConsumer: KafkaConsumer<String, String>,
    val topics: List<String>,
    val bucketName: String,
    val bucketNameVedlegg: String,
    val storage: Storage
) {

    companion object {
        private val logger = LoggerFactory.getLogger(SykmeldingConsumer::class.java)
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
                logger.error("Error processing messages", ex)
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
                    else -> uploadXml(sykmeldingId, sykmeldingRecord.fellesformat, sykmeldingRecord)
                }
            }
        }
    }

    private fun deleteXml(sykmeldingId: String) {
        logger.info("deleting xml for sykmelding $sykmeldingId")
        storage.delete(BlobId.of(bucketName, sykmeldingId))
        STORAGE_METRIC.labels("delete").inc()
    }

    private fun uploadXml(sykmeldingId: String, fellesformat: String?, sykmeldingRecord: SykmeldingRecord) {
        if (fellesformat == null) {
            STORAGE_METRIC.labels("empty").inc()
            return
        }
        val fellesFormatKanskjeMedVedlegg = if (sykmeldingRecord.vedlegg.isNotEmpty()) {
            val vedleggXmlList = sykmeldingRecord.vedlegg.map { getVedlegg(it, sykmeldingId) }
            leggTilVedleggIFellesformat(fellesformat, vedleggXmlList)
        } else {
            fellesformat
        }

        val blob = BlobInfo.newBuilder(BlobId.of(bucketName, sykmeldingId))
            .setContentType("application/xml")
            .setContentEncoding("gzip")
            .build()
        val compressedData = gzip(fellesFormatKanskjeMedVedlegg)
        storage.create(blob, compressedData)
        STORAGE_METRIC.labels("upload").inc()
    }

    private fun getVedlegg(key: String, sykmeldingId: String): String {
        val vedleggBlob = storage.get(bucketNameVedlegg, key)
        if (vedleggBlob == null) {
            logger.error("Fant ikke vedlegg med key $key, sykmeldingId $sykmeldingId")
            throw RuntimeException("Fant ikke vedlegg med key $key, sykmeldingId $sykmeldingId")
        } else {
            logger.info("Fant vedlegg med key $key, sykmeldingId $sykmeldingId")
            return toXml(objectMapper.readValue<VedleggMessage>(vedleggBlob.getContent()).vedlegg)
        }
    }

    fun toXml(obj: Any): String {
        val jaxbContext = JAXBContext.newInstance(obj::class.java)
        val marshaller = jaxbContext.createMarshaller()
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        val sw = StringWriter()
        marshaller.marshal(obj, sw)
        return sw.toString()
    }

    fun leggTilVedleggIFellesformat(fellesformatXml: String, vedlegg: List<String>): String {
        val insertPoint = fellesformatXml.indexOf("</ns2:Document>") + "</ns2:Document>".length

        if (insertPoint <= 0) throw IllegalArgumentException("Fant ikke <Document>-blokk å legge vedlegg etter")

        val vedleggXml = vedlegg.mapIndexed { index, base64 ->
            """
        <ns2:Document>
            <ns2:DocumentConnection DN="Vedlegg" V="V"/>
            <ns2:RefDoc>
                <ns2:MsgType DN="Vedlegg" V="A"/>
                <ns2:MimeType>application/pdf</ns2:MimeType>
                <ns2:Description>vedlegg${index + 1}.pdf</ns2:Description>
                <ns2:Content>
                    <ns5:Base64Container xmlns:ns5="http://www.kith.no/xmlstds/base64container">$base64</ns5:Base64Container>
                </ns2:Content>
            </ns2:RefDoc>
        </ns2:Document>
        """.trimIndent()
        }.joinToString("\n")

        return fellesformatXml.substring(0, insertPoint) + "\n" + vedleggXml + "\n" + fellesformatXml.substring(insertPoint)
    }

}
