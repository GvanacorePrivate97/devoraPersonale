package com.devora.mencare.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class TimeRange(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "start must be before end" }
    }

    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end

    /** Overlapping part of the two ranges, or null when they don't overlap. */
    fun intersect(other: TimeRange): TimeRange? {
        val from = maxOf(start, other.start)
        val to = minOf(end, other.end)
        return if (from < to) TimeRange(from, to) else null
    }
}

data class Operator(
    val id: String,
    val name: String,
    val title: String,
    val bio: String,
    val specialties: List<String>,
    val isOwner: Boolean = false,
    val weeklyHours: Map<DayOfWeek, List<TimeRange>>,
    val serviceIds: Set<String>,
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

enum class BlockReason { PERMESSO, PAUSA, FERIE, CORSO }

data class TimeBlock(
    val id: String,
    val operatorId: String,
    val reason: BlockReason,
    val date: LocalDate,
    val range: TimeRange,
    val label: String? = null,
)

data class Holiday(
    val id: String,
    val operatorId: String,
    val from: LocalDate,
    val to: LocalDate,
    val label: String,
)
