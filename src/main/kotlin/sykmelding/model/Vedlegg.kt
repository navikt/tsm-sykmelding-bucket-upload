package no.nav.tsm.sykmelding.model

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
data class Vedlegg(
    @XmlElement val content: Content = Content(),
    @XmlElement val type: String = "",
    @XmlElement val description: String = "",
)

@XmlRootElement
data class VedleggMessage(
    @XmlElement val vedlegg: Vedlegg = Vedlegg(),
    @XmlElement val behandler: BehandlerInfo = BehandlerInfo(),
    @XmlElement val pasientAktorId: String = "",
    @XmlElement val msgId: String = "",
    @XmlElement val pasientFnr: String = "",
    @XmlElement val source: String = "",
)

@XmlRootElement
data class Content(
    @XmlElement val contentType: String = "",
    @XmlElement val content: String = ""
)

@XmlRootElement
data class BehandlerInfo(
    @XmlElement val fornavn: String = "",
    @XmlElement val etternavn: String = "",
    @XmlElement val fnr: String? = null
)