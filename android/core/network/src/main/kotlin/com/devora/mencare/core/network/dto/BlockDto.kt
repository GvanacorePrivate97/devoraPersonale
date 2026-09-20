package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable

/** Permessi, pause, ferie e corsi: `/v1/blocks…`. */

@Serializable
data class TimeBlockDto(
    val id: String,
    val operatorId: String,
    val reason: String,
    val date: String,
    val start: String,
    val end: String,
    val label: String? = null,
)

@Serializable
data class BlocksEnvelope(val blocks: List<TimeBlockDto> = emptyList())

@Serializable
data class CreateBlockBody(
    val operatorId: String? = null,
    val reason: String,
    val date: String,
    val start: String,
    val end: String,
    val label: String? = null,
)
