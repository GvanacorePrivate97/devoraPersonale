package com.devora.mencare.core.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val italian = Locale.ITALIAN

/** "€ 22,00" */
fun formatPrice(cents: Long): String {
    val euros = cents / 100
    val rest = cents % 100
    return "€ %,d,%02d".format(italian, euros, rest)
}

/** "€ 22" or "€ 22,50" — compact form used in lists. */
fun formatPriceCompact(cents: Long): String {
    val euros = cents / 100
    val rest = cents % 100
    return if (rest == 0L) "€ %,d".format(italian, euros) else formatPrice(cents)
}

/** "75 min" */
fun formatDuration(minutes: Int): String = "$minutes min"

/** "1h 15m" for agenda summaries. */
fun formatDurationLong(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(time: LocalTime): String = time.format(timeFormatter)

/** "venerdì 11 settembre" */
fun formatDateLong(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", italian))

/** "Ven 11 set" */
fun formatDateShort(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", italian)).replace(".", "")

/** "settembre 2026" */
fun formatMonthYear(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMMM yyyy", italian))

fun formatDateTime(dt: LocalDateTime): String =
    "${formatDateShort(dt.toLocalDate())} · ${formatTime(dt.toLocalTime())}"

/** "Sabato" for one day, "Lun — Mer" for a run of them. */
fun formatDayGroup(days: List<DayOfWeek>): String {
    if (days.isEmpty()) return ""
    fun full(day: DayOfWeek) =
        day.getDisplayName(TextStyle.FULL, italian).replaceFirstChar { it.uppercase() }
    fun short(day: DayOfWeek) =
        day.getDisplayName(TextStyle.SHORT, italian)
            .trimEnd('.')
            .replaceFirstChar { it.uppercase() }
    return if (days.size == 1) full(days.first()) else "${short(days.first())} — ${short(days.last())}"
}
