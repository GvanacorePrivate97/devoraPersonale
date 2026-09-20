package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.map
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.di.ApplicationScope
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.NewOperator
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeRange
import com.devora.mencare.core.model.UserRole
import com.devora.mencare.core.network.api.CatalogApi
import com.devora.mencare.core.network.apiCall
import com.devora.mencare.core.network.dto.CatalogDto
import com.devora.mencare.core.network.dto.CreateHolidayBody
import com.devora.mencare.core.network.dto.CreateOperatorBody
import com.devora.mencare.core.network.dto.CreateServiceBody
import com.devora.mencare.core.network.dto.SalonDto
import com.devora.mencare.core.network.dto.ServiceIdsBody
import com.devora.mencare.core.network.dto.UpdateSalonBody
import com.devora.mencare.core.network.dto.UpdateServiceBody
import com.devora.mencare.core.network.dto.WeeklyHoursBody
import com.devora.mencare.core.network.dto.jsonOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/** Quanto tiene viva la lettura del listino dopo l'ultimo schermo che la guardava. */
private const val CATALOG_KEEP_ALIVE_MILLIS = 5_000L

private val EmptySalon = Salon(name = "", address = "", city = "")

/**
 * Listino, squadra, salone e ferie dal backend.
 *
 * `GET /catalog` porta le tre cose insieme, ed è così che le legge anche
 * questa classe: un solo `shareIn` alimenta i tre `Flow` dell'interfaccia, così
 * la home che guarda i servizi e il wizard che guarda gli operatori non fanno
 * due chiamate per la stessa risposta.
 */
@Singleton
class NetworkCatalogRepository @Inject constructor(
    private val api: CatalogApi,
    private val session: Session,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope scope: CoroutineScope,
) : CatalogRepository {

    private val catalog: Flow<CatalogDto> = sync
        .reloading(SyncKeys.CATALOG, CatalogDto(salon = SalonDto("", "", ""))) { api.catalog() }
        .shareIn(scope, SharingStarted.WhileSubscribed(CATALOG_KEEP_ALIVE_MILLIS), replay = 1)

    /**
     * Il telefono del salone non sta nel modello [Salon] ma il `PUT` lo pretende:
     * si ricorda l'ultimo letto per non cancellarlo salvando gli orari.
     */
    @Volatile
    private var lastKnownSalonPhone: String? = null

    override val salon: Flow<Salon> = catalog
        .map { dto ->
            lastKnownSalonPhone = dto.salon.phone
            if (dto.salon.name.isEmpty()) EmptySalon else dto.salon.toModel()
        }
        .distinctUntilChanged()

    override val services: Flow<List<Service>> = catalog
        .map { dto -> dto.services.map { it.toModel() } }
        .distinctUntilChanged()

    /**
     * Anche gli operatori ritirati restano nell'elenco: l'agenda e lo storico
     * rimandano a loro, e senza la riga mostrerebbero un appuntamento senza
     * nome. Non hanno più orari di lavoro, quindi il wizard non li propone
     * comunque come prenotabili.
     */
    override val operators: Flow<List<Operator>> = catalog
        .map { dto -> dto.operators.map { it.toModel() } }
        .distinctUntilChanged()

    /**
     * Le ferie sul backend si chiedono per operatore. Il titolare le vuole tutte
     * — la sua schermata mostra chi è via — l'operatore solo le proprie, e il
     * server non gliene darebbe altre comunque.
     */
    override val holidays: Flow<List<Holiday>> = sync.reloading(SyncKeys.HOLIDAYS, emptyList<Holiday>()) {
        val own = session.operatorId
        when {
            own == null -> emptyList()
            session.role == UserRole.OWNER -> allHolidays()
            else -> api.holidays(own).holidays.map { it.toModel() }
        }
    }

    private suspend fun allHolidays(): List<Holiday> = coroutineScope {
        api.operators().operators
            .map { operator -> async { api.holidays(operator.id).holidays.map { it.toModel() } } }
            .awaitAll()
            .flatten()
            .sortedBy { it.from }
    }

    override suspend fun saveService(service: Service): AppResult<Service> = withContext(dispatchers.io) {
        apiCall {
            val saved = if (service.id.isBlank()) {
                api.createService(
                    CreateServiceBody(
                        name = service.name,
                        durationMinutes = service.durationMinutes,
                        priceCents = service.priceCents,
                        description = service.description,
                        featured = service.featured,
                        active = service.active,
                    ),
                )
            } else {
                api.updateService(
                    service.id,
                    UpdateServiceBody(
                        name = service.name,
                        durationMinutes = service.durationMinutes,
                        priceCents = service.priceCents,
                        // `jsonOrNull`: qui il `null` deve arrivare davvero, è
                        // "togli la descrizione" e non "lasciala com'era".
                        description = jsonOrNull(service.description),
                        featured = service.featured,
                        active = service.active,
                    ),
                )
            }
            saved.toModel()
        }.onSuccess { sync.invalidate() }
    }

    override suspend fun createOperator(operator: NewOperator): AppResult<Operator> =
        withContext(dispatchers.io) {
            apiCall {
                // Profilo e account STAFF nascono insieme: la password
                // provvisoria torna in questa risposta e da nessun'altra parte.
                api.createOperator(
                    CreateOperatorBody(
                        name = operator.name,
                        email = operator.email,
                        phone = operator.phone,
                        title = operator.title.ifBlank { null },
                        weeklyHours = operator.weeklyHours.toHoursDto(),
                        serviceIds = operator.serviceIds.toList(),
                    ),
                ).operator.toModel()
            }.onSuccess { sync.invalidate() }
        }

    override suspend fun updateSalon(salon: Salon): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall {
            api.updateSalon(
                UpdateSalonBody(
                    name = salon.name,
                    address = salon.address,
                    city = salon.city,
                    phone = lastKnownSalonPhone,
                    weeklyHours = salon.weeklyHours.toHoursDto(),
                ),
            )
        }.map { }.onSuccess { sync.invalidate() }
    }

    override suspend fun updateOperatorHours(
        operatorId: String,
        weeklyHours: Map<DayOfWeek, List<TimeRange>>,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        // Il server rifiuta se degli appuntamenti futuri restano fuori orario:
        // è un controllo che l'app non può fare, non ha l'agenda intera.
        apiCall { api.setOperatorHours(operatorId, WeeklyHoursBody(weeklyHours.toHoursDto())) }
            .map { }
            .onSuccess { sync.invalidate() }
    }

    override suspend fun updateOperatorServices(
        operatorId: String,
        serviceIds: Set<String>,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.setOperatorServices(operatorId, ServiceIdsBody(serviceIds.toList())) }
            .map { }
            .onSuccess { sync.invalidate() }
    }

    override suspend fun addHoliday(holiday: Holiday): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall {
            api.addHoliday(
                holiday.operatorId,
                CreateHolidayBody(
                    from = holiday.from.toString(),
                    to = holiday.to.toString(),
                    label = holiday.label.ifBlank { null },
                ),
            )
        }.map { }.onSuccess { sync.invalidate() }
    }

    override suspend fun removeHoliday(holidayId: String): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.deleteHoliday(holidayId) }.onSuccess { sync.invalidate() }
    }
}
