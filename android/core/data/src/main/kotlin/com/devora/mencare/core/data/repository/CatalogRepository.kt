package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeRange
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/** Everything needed to hire an operator: profile plus the account they sign in with. */
data class NewOperator(
    val name: String,
    val title: String,
    val email: String,
    val phone: String,
    val serviceIds: Set<String>,
    val weeklyHours: Map<DayOfWeek, List<TimeRange>>,
)

interface CatalogRepository {
    val salon: Flow<Salon>
    val services: Flow<List<Service>>
    val operators: Flow<List<Operator>>
    val holidays: Flow<List<Holiday>>

    suspend fun saveService(service: Service): AppResult<Service>

    /**
     * Creates the operator profile *and* the STAFF account linked to it: an operator
     * without a login could never open the app on their first day.
     */
    suspend fun createOperator(operator: NewOperator): AppResult<Operator>

    suspend fun updateSalon(salon: Salon): AppResult<Unit>
    suspend fun updateOperatorHours(operatorId: String, weeklyHours: Map<DayOfWeek, List<TimeRange>>): AppResult<Unit>
    suspend fun updateOperatorServices(operatorId: String, serviceIds: Set<String>): AppResult<Unit>
    suspend fun addHoliday(holiday: Holiday): AppResult<Unit>
    suspend fun removeHoliday(holidayId: String): AppResult<Unit>
}
