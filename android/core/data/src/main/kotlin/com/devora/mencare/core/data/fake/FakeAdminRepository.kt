package com.devora.mencare.core.data.fake

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.data.repository.AdminRepository
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.CampaignSegment
import com.devora.mencare.core.model.DashboardPeriod
import com.devora.mencare.core.model.DashboardStats
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.OperatorOccupancy
import com.devora.mencare.core.model.PushCampaign
import com.devora.mencare.core.model.UpcomingDay
import com.devora.mencare.core.model.WaitlistStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dashboard KPIs computed live from the store, on a base offset per period so
 * the demo matches the mockup's order of magnitude ("€ 9.840 · +12%") without
 * seeding hundreds of historical appointments.
 */
private data class PeriodBaseline(
    val revenueCents: Long,
    val appointments: Int,
    val noShows: Int,
    val trendPercent: Int,
)

private fun baselineFor(period: DashboardPeriod): PeriodBaseline = when (period) {
    DashboardPeriod.DAY -> PeriodBaseline(32_000L, 17, 0, 8)
    DashboardPeriod.WEEK -> PeriodBaseline(210_000L, 115, 3, 5)
    DashboardPeriod.MONTH -> PeriodBaseline(900_000L, 490, 14, 12)
}

/** "Prossimi 7 giorni", oggi compreso. */
private const val UPCOMING_DAYS = 7L

@Singleton
class FakeAdminRepository @Inject constructor(
    private val store: InMemoryStore,
) : AdminRepository {

    override fun dashboard(period: DashboardPeriod): Flow<DashboardStats> =
        combine(store.appointments, store.clients, store.operators, store.waitlist) { appointments, clients, operators, waitlist ->
            val today = LocalDate.now()
            val (start, end) = when (period) {
                DashboardPeriod.DAY -> today to today
                DashboardPeriod.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong()).let { it to it.plusDays(6) }
                DashboardPeriod.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            }
            val baseline = baselineFor(period)
            val inPeriod = appointments.filter { it.date in start..end }
            val live = inPeriod.filter { it.status != AppointmentStatus.CANCELLED }

            val completed = inPeriod.filter { it.status == AppointmentStatus.COMPLETED }
            val noShows = inPeriod.count { it.status == AppointmentStatus.NO_SHOW } + baseline.noShows
            val revenue = baseline.revenueCents + completed.sumOf { it.totalPriceCents }
            val ticketCount = baseline.appointments + completed.size

            val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
            val perOperator = operators.map { op ->
                val bookedMinutes = live.filter { it.operatorId == op.id }.sumOf { it.durationMinutes }
                val workMinutes = days.sumOf { day ->
                    op.weeklyHours[day.dayOfWeek].orEmpty()
                        .sumOf { java.time.Duration.between(it.start, it.end).toMinutes() }
                }
                OperatorOccupancy(
                    op.id, op.name,
                    if (workMinutes == 0L) 0 else ((bookedMinutes * 100) / workMinutes).toInt().coerceAtMost(100),
                )
            }

            // I prossimi 7 giorni, come li calcola il server: minuti prenotati sui
            // minuti lavorabili di tutti gli operatori, e chi aspetta in lista.
            val upcoming = (0 until UPCOMING_DAYS).map { offset ->
                val day = today.plusDays(offset)
                val salonOpen = store.salon.value.weeklyHours[day.dayOfWeek].orEmpty().isNotEmpty()
                val workable = if (!salonOpen) {
                    0L
                } else {
                    operators.sumOf { op ->
                        op.weeklyHours[day.dayOfWeek].orEmpty()
                            .sumOf { java.time.Duration.between(it.start, it.end).toMinutes() }
                    }
                }
                val booked = appointments
                    .filter {
                        it.date == day && it.status != AppointmentStatus.CANCELLED && it.status != AppointmentStatus.NO_SHOW
                    }
                    .sumOf { it.durationMinutes }
                UpcomingDay(
                    date = day,
                    occupancyPercent = if (workable == 0L) 0 else ((booked * 100) / workable).toInt().coerceAtMost(100),
                    waitlistCount = waitlist.count { it.date == day && it.status == WaitlistStatus.WAITING },
                    closed = workable == 0L,
                )
            }

            DashboardStats(
                period = period,
                revenueCents = revenue,
                revenueTrendPercent = baseline.trendPercent,
                appointmentCount = live.size,
                noShowPercent = if (ticketCount == 0) 0.0 else noShows * 100.0 / ticketCount,
                averageTicketCents = if (ticketCount == 0) 0 else revenue / ticketCount,
                operatorOccupancy = perOperator.sortedByDescending { it.percent },
                upcomingDays = upcoming,
                inactiveClients = clients.filter { it.isInactiveSince(today) },
            )
        }

    override val notificationSettings: Flow<NotificationSettings> = store.notificationSettings

    override suspend fun updateNotificationSettings(settings: NotificationSettings): AppResult<Unit> {
        store.notificationSettings.value = settings
        return AppResult.Success(Unit)
    }

    override val campaigns: Flow<List<PushCampaign>> = store.campaigns

    override suspend fun saveCampaign(campaign: PushCampaign): AppResult<PushCampaign> {
        if (campaign.name.isBlank()) return AppResult.Failure(AppError.Validation("name"))
        if (campaign.body.length > 140) return AppResult.Failure(AppError.Validation("body"))
        val saved = if (campaign.id.isBlank()) campaign.copy(id = store.newId("camp")) else campaign
        val exists = store.campaigns.value.any { it.id == saved.id }
        store.campaigns.value =
            if (exists) store.campaigns.value.map { if (it.id == saved.id) saved else it }
            else store.campaigns.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun reachFor(segment: CampaignSegment): Pair<Int, Int> {
        val today = LocalDate.now()
        val clients = store.clients.value
        val matching = when (segment) {
            CampaignSegment.TUTTI -> clients
            CampaignSegment.INATTIVI_60 -> clients.filter { it.isInactiveSince(today) }
            CampaignSegment.TOP_SPESA -> clients.filter { it.lifetimeSpendCents >= 40_000 }
        }
        // Mockup scale: the fake CRM only holds a handful of records, so pad
        // to the storefront numbers ("198 di 214").
        val size = matching.size + when (segment) {
            CampaignSegment.TUTTI -> 1_272
            CampaignSegment.INATTIVI_60 -> 210
            CampaignSegment.TOP_SPESA -> 80
        }
        val reachable = matching.count { it.marketingOptIn } + when (segment) {
            CampaignSegment.TUTTI -> 1_154
            CampaignSegment.INATTIVI_60 -> 195
            CampaignSegment.TOP_SPESA -> 74
        }
        return reachable to size
    }
}
