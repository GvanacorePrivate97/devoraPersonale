package com.devora.mencare.core.data.fake

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.NewOperator
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeRange
import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.UserRole
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeCatalogRepository @Inject constructor(
    private val store: InMemoryStore,
) : CatalogRepository {

    override val salon: Flow<Salon> = store.salon
    override val services: Flow<List<Service>> = store.services
    override val operators: Flow<List<Operator>> = store.operators
    override val holidays: Flow<List<Holiday>> = store.holidays

    override suspend fun saveService(service: Service): AppResult<Service> {
        if (service.name.isBlank()) return AppResult.Failure(AppError.Validation("name"))
        if (service.durationMinutes <= 0) return AppResult.Failure(AppError.Validation("duration"))
        val saved = if (service.id.isBlank()) service.copy(id = store.newId("svc")) else service
        val existing = store.services.value.any { it.id == saved.id }
        store.services.value =
            if (existing) store.services.value.map { if (it.id == saved.id) saved else it }
            else store.services.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun createOperator(operator: NewOperator): AppResult<Operator> {
        if (operator.name.isBlank()) return AppResult.Failure(AppError.Validation("name"))
        if (operator.email.isBlank()) return AppResult.Failure(AppError.Validation("email"))
        if (store.users.value.any { it.email.equals(operator.email.trim(), ignoreCase = true) }) {
            return AppResult.Failure(AppError.EmailAlreadyRegistered)
        }
        val profile = Operator(
            id = store.newId("op"),
            name = operator.name.trim(),
            title = operator.title.trim().ifBlank { "Barbiere" },
            bio = operator.title.trim(),
            specialties = emptyList(),
            weeklyHours = operator.weeklyHours,
            serviceIds = operator.serviceIds,
        )
        store.operators.value += profile
        // The account is what lets the new hire actually sign in.
        val parts = profile.name.split(' ').filter { it.isNotBlank() }
        store.users.value += User(
            id = store.newId("user"),
            firstName = parts.firstOrNull().orEmpty(),
            lastName = parts.drop(1).joinToString(" "),
            email = operator.email.trim(),
            phone = operator.phone.trim(),
            role = UserRole.STAFF,
            memberSince = LocalDate.now(),
            operatorId = profile.id,
        )
        return AppResult.Success(profile)
    }

    override suspend fun updateSalon(salon: Salon): AppResult<Unit> {
        if (salon.name.isBlank()) return AppResult.Failure(AppError.Validation("name"))
        store.salon.value = salon
        return AppResult.Success(Unit)
    }

    override suspend fun updateOperatorHours(
        operatorId: String,
        weeklyHours: Map<DayOfWeek, List<TimeRange>>,
    ): AppResult<Unit> {
        store.operators.value = store.operators.value.map {
            if (it.id == operatorId) it.copy(weeklyHours = weeklyHours) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun updateOperatorServices(operatorId: String, serviceIds: Set<String>): AppResult<Unit> {
        store.operators.value = store.operators.value.map {
            if (it.id == operatorId) it.copy(serviceIds = serviceIds) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun addHoliday(holiday: Holiday): AppResult<Unit> {
        val saved = if (holiday.id.isBlank()) holiday.copy(id = store.newId("hol")) else holiday
        store.holidays.value += saved
        return AppResult.Success(Unit)
    }

    override suspend fun removeHoliday(holidayId: String): AppResult<Unit> {
        store.holidays.value = store.holidays.value.filterNot { it.id == holidayId }
        return AppResult.Success(Unit)
    }
}
