package com.devora.mencare.core.model

import java.time.DayOfWeek

/** The salon itself: one single location, so it has no identity to select. */
data class Salon(
    val name: String,
    val address: String,
    val city: String,
    /** Opening hours of the salon: a day with no ranges is a closing day. */
    val weeklyHours: Map<DayOfWeek, List<TimeRange>> = emptyMap(),
) {
    val closingDays: Set<DayOfWeek>
        get() = DayOfWeek.entries.filterTo(mutableSetOf()) { weeklyHours[it].isNullOrEmpty() }
}
