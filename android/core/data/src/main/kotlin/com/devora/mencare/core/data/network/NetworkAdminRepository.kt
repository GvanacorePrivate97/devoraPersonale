package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.AdminRepository
import com.devora.mencare.core.model.CampaignSegment
import com.devora.mencare.core.model.CampaignStatus
import com.devora.mencare.core.model.DashboardPeriod
import com.devora.mencare.core.model.DashboardStats
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.PushCampaign
import com.devora.mencare.core.network.api.AdminApi
import com.devora.mencare.core.network.apiCall
import com.devora.mencare.core.network.dto.CampaignBody
import com.devora.mencare.core.network.dto.CampaignReachDto
import com.devora.mencare.core.network.dto.CreateReminderRuleBody
import com.devora.mencare.core.network.dto.jsonOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

private val EmptyDashboard = DashboardStats(
    period = DashboardPeriod.WEEK,
    revenueCents = 0,
    revenueTrendPercent = 0,
    appointmentCount = 0,
    noShowPercent = 0.0,
    averageTicketCents = 0,
    operatorOccupancy = emptyList(),
    upcomingDays = emptyList(),
    inactiveClients = emptyList(),
)

/**
 * Area titolare dal backend.
 *
 * I numeri del cruscotto non si ricostruiscono più in app: incassi,
 * occupazione, no-show e scontrino medio sono conti su tutto lo storico, e
 * l'app ne ha in mano solo una manciata di righe. Lo stesso vale per i
 * destinatari raggiungibili di una campagna, che il server conta davvero
 * invece di stimarli.
 */
@Singleton
class NetworkAdminRepository @Inject constructor(
    private val api: AdminApi,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
) : AdminRepository {

    override fun dashboard(period: DashboardPeriod): Flow<DashboardStats> =
        sync.reloading(SyncKeys.DASHBOARD, EmptyDashboard.copy(period = period)) {
            api.dashboard(period.name).toModel()
        }

    /**
     * Gli interruttori e i promemoria sono due rotte distinte ma una sola
     * schermata: si leggono insieme e si scrivono insieme.
     */
    override val notificationSettings: Flow<NotificationSettings> =
        sync.reloading(SyncKeys.SETTINGS, NotificationSettings(emptyList())) {
            coroutineScope {
                val switches = async { api.notificationSettings() }
                val rules = async { api.reminderRules().rules }
                switches.await().toModel(rules.await())
            }
        }

    override suspend fun updateNotificationSettings(settings: NotificationSettings): AppResult<Unit> =
        withContext(dispatchers.io) {
            apiCall {
                api.updateNotificationSettings(settings.toDto())
                syncReminderRules(settings)
            }.onSuccess { sync.invalidate() }
        }

    /**
     * Un promemoria è soltanto "N ore prima": l'insieme delle ore *è* lo stato.
     * Si confrontano quelle, non gli id, perché la schermata crea le righe
     * nuove con un id inventato in locale che il server non ha mai visto.
     */
    private suspend fun syncReminderRules(settings: NotificationSettings) {
        val current = api.reminderRules().rules
        val wanted = settings.reminders.map { it.hoursBefore }.toSet()
        current.filter { it.hoursBefore !in wanted }.forEach { api.removeReminderRule(it.id) }
        val existing = current.map { it.hoursBefore }.toSet()
        (wanted - existing).forEach { api.addReminderRule(CreateReminderRuleBody(it)) }
    }

    override val campaigns: Flow<List<PushCampaign>> =
        sync.reloading(SyncKeys.CAMPAIGNS, emptyList<PushCampaign>()) {
            api.campaigns().campaigns.map { it.toModel() }
        }

    override suspend fun saveCampaign(campaign: PushCampaign): AppResult<PushCampaign> =
        withContext(dispatchers.io) {
            val body = campaign.toBody()
            apiCall {
                val saved = if (campaign.id.isBlank()) {
                    api.createCampaign(body)
                } else {
                    api.updateCampaign(campaign.id, body)
                }
                saved.toModel()
            }.onSuccess { sync.invalidate() }
        }

    override suspend fun reachFor(segment: CampaignSegment): Pair<Int, Int> = withContext(dispatchers.io) {
        val reach = sync.load<CampaignReachDto?>(SyncKeys.CAMPAIGNS, null) { api.campaignReach(segment.name) }
        (reach?.reachable ?: 0) to (reach?.segmentSize ?: 0)
    }
}

private fun PushCampaign.toBody() = CampaignBody(
    name = name,
    segment = segment.name,
    title = title,
    body = body,
    // Orario da muro senza secondi: è la forma che il backend riconosce come
    // "ora scelta dal titolare" e riporta nel fuso del salone.
    scheduledAt = jsonOrNull(scheduledAt?.toMinuteText()),
    repeatWeekly = repeatWeekly,
    sendCap = jsonOrNull(sendCap),
    // Il server accetta solo bozza o programmata: una campagna già inviata non
    // si riporta indietro con un salvataggio.
    status = status.takeIf { it != CampaignStatus.SENT }?.name,
)

private fun LocalDateTime.toMinuteText(): String = truncatedTo(ChronoUnit.MINUTES).toString()
