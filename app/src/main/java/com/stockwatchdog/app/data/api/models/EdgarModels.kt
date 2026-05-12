package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EdgarTickerCompany(
    @SerialName("cik_str") val cik: Long? = null,
    val ticker: String? = null,
    val title: String? = null
)

@Serializable
data class EdgarCompanyFacts(
    val cik: Long? = null,
    val entityName: String? = null,
    val facts: Map<String, Map<String, EdgarConcept>> = emptyMap()
)

@Serializable
data class EdgarConcept(
    val label: String? = null,
    val description: String? = null,
    val units: Map<String, List<EdgarFact>> = emptyMap()
)

@Serializable
data class EdgarFact(
    val start: String? = null,
    val end: String? = null,
    @SerialName("val") val value: Double? = null,
    val accn: String? = null,
    val fy: Int? = null,
    val fp: String? = null,
    val form: String? = null,
    val filed: String? = null,
    val frame: String? = null
)
