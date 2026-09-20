package com.devora.mencare.feature.admin.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

private val DEFAULT_OPENING = TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))

data class BusinessSettingsUiState(
    val salon: Salon? = null,
    /** One opening range per day; a missing day is a closing day. */
    val hours: Map<DayOfWeek, TimeRange> = emptyMap(),
    /**
     * Fasce oltre la prima (turno spezzato): l'editor mostra solo la prima, ma
     * le altre viaggiano con il salvataggio invece di essere buttate via.
     */
    val extraHours: Map<DayOfWeek, List<TimeRange>> = emptyMap(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class BusinessSettingsViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BusinessSettingsUiState())
    val state: StateFlow<BusinessSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val salon = catalogRepository.salon.first()
            _state.update {
                it.copy(salon = salon, hours = hoursOf(salon), extraHours = extraHoursOf(salon))
            }
        }
    }

    private fun hoursOf(salon: Salon): Map<DayOfWeek, TimeRange> =
        salon.weeklyHours.mapNotNull { (day, ranges) -> ranges.firstOrNull()?.let { day to it } }.toMap()

    private fun extraHoursOf(salon: Salon): Map<DayOfWeek, List<TimeRange>> =
        salon.weeklyHours.mapValues { (_, ranges) -> ranges.drop(1) }.filterValues { it.isNotEmpty() }

    fun setOpen(day: DayOfWeek, open: Boolean) = _state.update { s ->
        val hours = if (open) s.hours + (day to DEFAULT_OPENING) else s.hours - day
        // Chiudere un giorno porta via anche l'eventuale seconda fascia.
        val extra = if (open) s.extraHours else s.extraHours - day
        s.copy(hours = hours, extraHours = extra, dirty = true, saved = false, saveFailed = false)
    }

    fun setFrom(day: DayOfWeek, from: LocalTime) = _state.update { s ->
        val current = s.hours[day] ?: DEFAULT_OPENING
        val to = if (from < current.end) current.end else from.plusHours(1)
        s.copy(hours = s.hours + (day to TimeRange(from, to)), dirty = true, saved = false, saveFailed = false)
    }

    fun setTo(day: DayOfWeek, to: LocalTime) = _state.update { s ->
        val current = s.hours[day] ?: DEFAULT_OPENING
        val from = if (current.start < to) current.start else to.minusHours(1)
        s.copy(hours = s.hours + (day to TimeRange(from, to)), dirty = true, saved = false, saveFailed = false)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val salon = s.salon ?: return
        val weeklyHours = s.hours.mapValues { (day, range) -> listOf(range) + s.extraHours[day].orEmpty() }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveFailed = false) }
            catalogRepository
                .updateSalon(salon.copy(weeklyHours = weeklyHours))
                .onSuccess {
                    val updated = catalogRepository.salon.first()
                    _state.update {
                        it.copy(salon = updated, saving = false, dirty = false, saved = true)
                    }
                }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }
}
