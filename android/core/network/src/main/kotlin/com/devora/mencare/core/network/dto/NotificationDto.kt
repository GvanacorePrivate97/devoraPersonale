package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** La campanella: `/v1/notifications…`. I testi li scrive il server. */

@Serializable
data class AppNotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val kind: String = "",
    /** Payload libero (id dell'appuntamento e simili): l'app oggi non lo usa. */
    val payload: JsonObject = JsonObject(emptyMap()),
    /** Istante ISO con fuso: va portato all'ora del salone per mostrarlo. */
    val at: String,
    val read: Boolean = false,
)

@Serializable
data class NotificationPageDto(
    val notifications: List<AppNotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val nextCursor: String? = null,
)

@Serializable
data class MarkReadDto(val marked: Int = 0, val unreadCount: Int = 0)
