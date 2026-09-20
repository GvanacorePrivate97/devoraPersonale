package com.devora.mencare.feature.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.data.repository.AdminRepository
import com.devora.mencare.core.model.DashboardPeriod
import com.devora.mencare.core.model.DashboardStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val period: DashboardPeriod = DashboardPeriod.DAY,
    val stats: DashboardStats? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    adminRepository: AdminRepository,
) : ViewModel() {

    private val period = MutableStateFlow(DashboardPeriod.DAY)

    val state = combine(
        period,
        period.flatMapLatest { adminRepository.dashboard(it) },
    ) { period, stats ->
        DashboardUiState(period = period, stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setPeriod(value: DashboardPeriod) {
        period.value = value
    }
}
