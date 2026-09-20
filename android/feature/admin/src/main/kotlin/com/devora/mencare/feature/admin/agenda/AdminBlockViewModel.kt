package com.devora.mencare.feature.admin.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.TimeBlockRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AdminBlockUiState(
    val operators: List<Operator> = emptyList(),
    val selectedOperatorId: String? = null,
    val reason: BlockReason = BlockReason.PERMESSO,
    val date: LocalDate = LocalDate.now(),
    val from: LocalTime = LocalTime.of(15, 30),
    val to: LocalTime = LocalTime.of(18, 0),
    val conflicts: List<Appointment> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val totalMinutes: Int get() = (to.toSecondOfDay() - from.toSecondOfDay()) / 60

    /** The repository refuses a block that overlaps live appointments. */
    val canSave: Boolean
        get() = selectedOperatorId != null && totalMinutes > 0 && conflicts.isEmpty() && !saving
}

/**
 * Owner-side twin of the staff block screen: same rules, but the operator is
 * chosen instead of being the signed-in one.
 */
@HiltViewModel
class AdminBlockViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    private val blockRepository: TimeBlockRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBlockUiState())
    val state: StateFlow<AdminBlockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val operators = catalogRepository.operators.first()
            _state.update { it.copy(operators = operators, selectedOperatorId = operators.firstOrNull()?.id) }
            refreshConflicts()
        }
    }

    fun selectOperator(operatorId: String) {
        _state.update { it.copy(selectedOperatorId = operatorId) }
        refreshConflicts()
    }

    fun setReason(reason: BlockReason) = _state.update { it.copy(reason = reason) }

    fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date) }
        refreshConflicts()
    }

    fun setFrom(from: LocalTime) {
        _state.update { s -> s.copy(from = from, to = if (from >= s.to) from.plusHours(1) else s.to) }
        refreshConflicts()
    }

    fun setTo(to: LocalTime) {
        _state.update { s -> s.copy(to = if (to <= s.from) s.from.plusHours(1) else to) }
        refreshConflicts()
    }

    fun halfDay() = setRange(LocalTime.of(9, 0), LocalTime.of(13, 0))

    fun fullDay() = setRange(LocalTime.of(9, 0), LocalTime.of(19, 0))

    private fun setRange(from: LocalTime, to: LocalTime) {
        _state.update { it.copy(from = from, to = to) }
        refreshConflicts()
    }

    private fun refreshConflicts() {
        val s = _state.value
        val operatorId = s.selectedOperatorId ?: return
        if (s.totalMinutes <= 0) return
        viewModelScope.launch {
            val candidate = TimeBlock(
                id = "candidate",
                operatorId = operatorId,
                reason = s.reason,
                date = s.date,
                range = TimeRange(s.from, s.to),
            )
            _state.update { it.copy(conflicts = blockRepository.conflictsFor(candidate)) }
        }
    }

    fun save() {
        val s = _state.value
        val operatorId = s.selectedOperatorId ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            blockRepository.createBlock(
                TimeBlock(
                    id = "",
                    operatorId = operatorId,
                    reason = s.reason,
                    date = s.date,
                    range = TimeRange(s.from, s.to),
                ),
            ).onSuccess { _state.update { it.copy(saving = false, saved = true) } }
            _state.update { it.copy(saving = false) }
        }
    }
}
