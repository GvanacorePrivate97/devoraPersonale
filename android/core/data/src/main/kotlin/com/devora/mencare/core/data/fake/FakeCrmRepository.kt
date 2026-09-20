package com.devora.mencare.core.data.fake

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.ClientSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeCrmRepository @Inject constructor(
    private val store: InMemoryStore,
) : CrmRepository {

    override val clients: Flow<List<ClientRecord>> =
        store.clients.map { list -> list.sortedBy { it.lastName } }

    override fun client(id: String): Flow<ClientRecord?> =
        combine(store.clients, store.appointments) { list, appointments ->
            list.firstOrNull { it.id == id }?.withHabits(appointments.filter { it.clientId == id })
        }

    /**
     * Stessa regola del server: l'operatore più frequente fra le visite
     * completate (a parità il più recente) e la media per difetto dei giorni
     * fra l'una e l'altra.
     */
    private fun ClientRecord.withHabits(history: List<Appointment>): ClientRecord {
        val completed = history.filter { it.status == AppointmentStatus.COMPLETED }.sortedByDescending { it.start }
        val counts = completed.groupingBy { it.operatorId }.eachCount()
        var favorite: String? = null
        var best = 0
        for (apt in completed) {
            val count = counts.getValue(apt.operatorId)
            if (count > best) {
                best = count
                favorite = apt.operatorId
            }
        }
        val days = completed.map { it.date }.distinct().sorted()
        val average = if (days.size < 2) {
            null
        } else {
            (days.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b) }.sum() / (days.size - 1)).toInt()
        }
        return copy(favoriteOperatorId = favorite, averageDaysBetweenVisits = average)
    }

    override fun search(query: String, segment: ClientSegment): Flow<List<ClientRecord>> =
        store.clients.map { list ->
            val today = LocalDate.now()
            val q = query.trim()
            list.asSequence()
                .filter { c ->
                    q.isBlank() || c.fullName.contains(q, ignoreCase = true) ||
                        c.phone.replace(" ", "").contains(q.replace(" ", "")) ||
                        c.email.contains(q, ignoreCase = true)
                }
                .filter { c ->
                    when (segment) {
                        ClientSegment.TUTTI -> true
                        ClientSegment.FEDELI -> c.isLoyal(today)
                        ClientSegment.INATTIVI_60 -> c.isInactiveSince(today)
                        ClientSegment.NO_SHOW -> c.noShowCount > 0
                        ClientSegment.TOP_SPESA -> c.lifetimeSpendCents >= 40_000
                    }
                }
                .sortedBy { it.lastName }
                .toList()
        }

    override suspend fun createClient(firstName: String, lastName: String, phone: String): AppResult<ClientRecord> {
        if (firstName.isBlank() || lastName.isBlank()) return AppResult.Failure(AppError.Validation("name"))
        val record = ClientRecord(
            id = store.newId("cli"),
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            phone = phone.trim(),
            email = "",
            customerSince = LocalDate.now(),
            visitCount = 0,
            lifetimeSpendCents = 0,
            noShowCount = 0,
            lastVisit = null,
        )
        store.clients.value += record
        return AppResult.Success(record)
    }
}
