package no.nav.tsm.sykmelding

data class SykmeldingRecord(
    val vedlegg: List<String>,
    val fellesformat: String?
)
