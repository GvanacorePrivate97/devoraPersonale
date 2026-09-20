package com.devora.mencare.core.model

import java.time.DayOfWeek

/**
 * Collapses consecutive days that share the same hours, the way an opening
 * schedule is normally read: "Mon—Wed 10:00–19:00", "Sunday closed".
 *
 * A day missing from the map — or mapped to no range — counts as closed, so the
 * result always covers the full week in order.
 */
fun Map<DayOfWeek, List<TimeRange>>.groupConsecutiveDays(): List<Pair<List<DayOfWeek>, TimeRange?>> {
    val groups = mutableListOf<Pair<MutableList<DayOfWeek>, TimeRange?>>()
    DayOfWeek.values().sortedBy { it.value }.forEach { day ->
        val range = this[day]?.firstOrNull()
        val last = groups.lastOrNull()
        if (last != null && last.second == range) {
            last.first.add(day)
        } else {
            groups.add(mutableListOf(day) to range)
        }
    }
    return groups.map { it.first.toList() to it.second }
}
