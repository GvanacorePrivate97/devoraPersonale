package com.devora.mencare.core.network.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * `null` che arriva davvero nel JSON.
 *
 * Il `Json` dell'app ha `explicitNulls = false`, quindi un campo nullo viene
 * omesso: è quello che serve quasi sempre (un `PATCH` non deve toccare ciò che
 * non nomina), ma dove il backend legge `null` come "cancella" serve il
 * contrario. Questi helper lo rendono esplicito nel punto di chiamata.
 */
fun jsonOrNull(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun jsonOrNull(value: Int?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
