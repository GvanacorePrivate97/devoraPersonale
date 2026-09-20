package com.devora.mencare.feature.client.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.data.network.DataSync
import com.devora.mencare.core.data.network.SyncKeys
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** How far ahead the home looks for a free slot, and how many chips it shows. */
private const val QUICK_SLOT_DAYS = 7L
private const val QUICK_SLOTS_PER_DAY = 2
private const val QUICK_SLOT_COUNT = 4

/** A bookable slot offered on the home: one tap opens the wizard on it. */
data class QuickSlot(
    val date: LocalDate,
    val time: LocalTime,
    /** null = "Qualsiasi operatore". */
    val operatorId: String?,
    val serviceIds: List<String>,
)

data class HomeUiState(
    val user: User? = null,
    val nextAppointment: Appointment? = null,
    val lastCompleted: Appointment? = null,
    val services: Map<String, Service> = emptyMap(),
    val operators: Map<String, Operator> = emptyMap(),
    val unreadCount: Int = 0,
    val quickSlots: List<QuickSlot> = emptyList(),
    /** Una lettura è in corso: serve solo finché non c'è ancora niente da mostrare. */
    val loading: Boolean = false,
    /** L'ultima lettura fallita, con il suo "riprova". */
    val error: AppError? = null,
) {
    /** Prima risposta non ancora arrivata: la home è tutta da disegnare. */
    val isEmpty: Boolean get() = user == null && services.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    catalogRepository: CatalogRepository,
    notificationRepository: NotificationRepository,
    private val sync: DataSync,
) : ViewModel() {

    private val base = combine(
        authRepository.currentUser,
        authRepository.currentUser.flatMapLatest { user ->
            val recordId = user?.clientRecordId
            if (recordId == null) flowOf(emptyList()) else bookingRepository.appointmentsForClient(recordId)
        },
        catalogRepository.services,
        catalogRepository.operators,
        notificationRepository.notifications,
    ) { user, appointments, services, operators, notifications ->
        val now = LocalDateTime.now()
        HomeUiState(
            user = user,
            nextAppointment = appointments
                .filter { it.isActive && it.start >= now.minusMinutes(30) }
                .minByOrNull { it.start },
            lastCompleted = appointments
                .filter { it.status == com.devora.mencare.core.model.AppointmentStatus.COMPLETED }
                .maxByOrNull { it.start },
            services = services.associateBy { it.id },
            operators = operators.associateBy { it.id },
            unreadCount = notifications.count { !it.read },
        )
    }

    // Gli slot costano una scansione dell'agenda: la home si disegna subito e
    // la sezione compare appena il calcolo finisce.
    val state = base
        .flatMapLatest { home ->
            flow {
                emit(home)
                emit(home.copy(quickSlots = quickSlotsFor(home)))
            }
        }
        .combine(sync.state(SyncKeys.CATALOG, SyncKeys.APPOINTMENTS, SyncKeys.NOTIFICATIONS)) { home, status ->
            home.copy(loading = status.loading, error = status.error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** "Riprova": si dimenticano i guasti e si rileggono i dati osservati. */
    fun retry() = sync.retry()

    /**
     * Primi slot liberi del salone, su qualsiasi operatore. I servizi di
     * riferimento sono quelli dell'ultima visita (o uno in evidenza per chi
     * non ha storico): servono solo a dimensionare la durata dello slot.
     */
    private suspend fun quickSlotsFor(home: HomeUiState): List<QuickSlot> {
        val serviceIds = home.lastCompleted?.serviceIds?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(defaultServiceId(home))
        if (serviceIds.isEmpty()) return emptyList()
        return nearestSlots(null, serviceIds)
    }

    private fun defaultServiceId(home: HomeUiState): String? {
        // Solo fra i servizi ancora a listino: uno ritirato serve a dare il nome
        // allo storico, non a dimensionare uno slot da proporre.
        val services = home.services.values.filter { it.active }
        return (services.firstOrNull { it.featured } ?: services.firstOrNull())?.id
    }

    /**
     * Con il backend ogni giorno è una richiesta: prima si chiede quali giorni
     * hanno posto — una sola chiamata per tutta la settimana — e solo su quelli
     * si vanno a prendere gli orari, in parallelo. Prima erano otto richieste in
     * fila per riempire quattro pastiglie.
     */
    private suspend fun nearestSlots(operatorId: String?, serviceIds: List<String>): List<QuickSlot> {
        val today = LocalDate.now()
        val days = bookingRepository
            .availableDays(operatorId, serviceIds, today, today.plusDays(QUICK_SLOT_DAYS))
            .sorted()
            .take(QUICK_SLOT_COUNT)
        if (days.isEmpty()) return emptyList()

        val perDay = coroutineScope {
            days.map { day -> async { day to bookingRepository.availability(operatorId, serviceIds, day).slots } }
                .map { it.await() }
        }
        val found = mutableListOf<QuickSlot>()
        for ((day, slots) in perDay) {
            if (found.size >= QUICK_SLOT_COUNT) break
            slots.take(minOf(QUICK_SLOTS_PER_DAY, QUICK_SLOT_COUNT - found.size))
                .forEach { found += QuickSlot(day, it, operatorId, serviceIds) }
        }
        return found
    }
}
