package com.devora.mencare.core.model

import java.time.LocalDate

enum class ClientSegment { TUTTI, FEDELI, INATTIVI_60, NO_SHOW, TOP_SPESA }

data class ClientRecord(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val customerSince: LocalDate,
    val visitCount: Int,
    val lifetimeSpendCents: Long,
    val noShowCount: Int,
    val lastVisit: LocalDate?,
    val preferredServiceIds: List<String> = emptyList(),
    val preferredOperatorId: String? = null,
    val marketingOptIn: Boolean = true,
    /** Habits from the client sheet (`GET /crm/clients/:id`): null in lists. */
    val favoriteOperatorId: String? = null,
    val averageDaysBetweenVisits: Int? = null,
) {
    val fullName: String get() = "$firstName $lastName"
    val initials: String get() = "${firstName.firstOrNull() ?: ' '}${lastName.firstOrNull() ?: ' '}".trim()

    fun isInactiveSince(today: LocalDate, days: Long = 60): Boolean =
        lastVisit == null || lastVisit.plusDays(days) < today

    fun isLoyal(today: LocalDate): Boolean =
        visitCount >= 10 && !isInactiveSince(today)
}

/** Time window the owner dashboard aggregates over. */
enum class DashboardPeriod { DAY, WEEK, MONTH }

data class DashboardStats(
    val period: DashboardPeriod,
    val revenueCents: Long,
    val revenueTrendPercent: Int,
    /** Non-cancelled appointments of every operator in the period. */
    val appointmentCount: Int,
    val noShowPercent: Double,
    val averageTicketCents: Long,
    val operatorOccupancy: List<OperatorOccupancy>,
    /** The next 7 days from today, whatever the period: where there is room to fill. */
    val upcomingDays: List<UpcomingDay>,
    val inactiveClients: List<ClientRecord>,
)

data class OperatorOccupancy(val operatorId: String, val operatorName: String, val percent: Int)

/**
 * One of the next 7 days on the dashboard: how full the salon is, how many
 * clients asked "Avvisami" for it, and whether it is closed.
 */
data class UpcomingDay(
    val date: LocalDate,
    val occupancyPercent: Int,
    val waitlistCount: Int,
    val closed: Boolean,
)
