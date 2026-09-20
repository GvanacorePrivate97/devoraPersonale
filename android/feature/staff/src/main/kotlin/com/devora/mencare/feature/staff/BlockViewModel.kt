package com.devora.mencare.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.BookingRepository
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

data class BlockUiState(
    val reason: BlockReason = BlockReason.PERMESSO,
    val date: LocalDate = LocalDate.now(),
    val from: LocalTime = LocalTime.of(15, 30),
    val to: LocalTime = LocalTime.of(18, 0),
    val conflicts: List<Appointment> = emptyList(),
    val resolvedConflictIds: Set<String> = emptySet(),
    val colleagues: List<Operator> = emptyList(),
    val saved: Boolean = false,
) {
    val totalMinutes: Int get() = (to.toSecondOfDay() - from.toSecondOfDay()) / 60

    val unresolvedConflicts: List<Appointment> get() = conflicts.filterNot { it.id in resolvedConflictIds }

    val canSave: Boolean get() = totalMinutes > 0 && unresolvedConflicts.isEmpty()
}

@HiltViewModel
class BlockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val blockRepository: TimeBlockRepository,
    private val bookingRepository: BookingRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BlockUiState())
    val state: StateFlow<BlockUiState> = _state.asStateFlow()

    private var operatorId: String? = null

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            operatorId = user?.operatorId
            val colleagues = catalogRepository.operators.first()
                .filter { it.id != operatorId }
            _state.update { it.copy(colleagues = colleagues) }
            refreshConflicts()
        }
    }

    fun setReason(reason: BlockReason) = _state.update { it.copy(reason = reason) }

    fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date, resolvedConflictIds = emptySet()) }
        refreshConflicts()
    }

    fun setFrom(from: LocalTime) = setRange(from, if (from >= _state.value.to) from.plusHours(1) else _state.value.to)

    fun setTo(to: LocalTime) {
        val from = _state.value.from
        setRange(from, if (to <= from) from.plusHours(1) else to)
    }

    fun halfDay() = setRange(LocalTime.of(9, 0), LocalTime.of(13, 0))

    fun fullDay() = setRange(LocalTime.of(9, 0), LocalTime.of(19, 0))

    /** Back to a blank form; the colleagues don't change. */
    fun reset() {
        _state.update { BlockUiState(colleagues = it.colleagues) }
        refreshConflicts()
    }

    private fun setRange(from: LocalTime, to: LocalTime) {
        _state.update { it.copy(from = from, to = to, resolvedConflictIds = emptySet()) }
        refreshConflicts()
    }

    private fun refreshConflicts() {
        val opId = operatorId ?: return
        viewModelScope.launch {
            val s = _state.value
            if (s.totalMinutes <= 0) {
                _state.update { it.copy(conflicts = emptyList()) }
                return@launch
            }
            val conflicts = blockRepository.conflictsFor(
                TimeBlock("", opId, s.reason, s.date, TimeRange(s.from, s.to)),
            )
            _state.update { it.copy(conflicts = conflicts) }
        }
    }

    /** Reassign a conflicting appointment to a colleague at the same time. */
    fun reassign(appointmentId: String, colleagueId: String) {
        viewModelScope.launch {
            val apt = _state.value.conflicts.first { it.id == appointmentId }
            bookingRepository.reschedule(appointmentId, apt.start, colleagueId).onSuccess {
                _state.update { it.copy(resolvedConflictIds = it.resolvedConflictIds + appointmentId) }
            }
        }
    }

    /**
     * "Proponi altro orario": Phase 1 marks the conflict handled — the client
     * would get a reschedule proposal via push in Phase 2.
     */
    fun proposeNewTime(appointmentId: String) {
        _state.update { it.copy(resolvedConflictIds = it.resolvedConflictIds + appointmentId) }
    }

    fun save() {
        val opId = operatorId ?: return
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            blockRepository.createBlock(
                TimeBlock(
                    id = "",
                    operatorId = opId,
                    reason = s.reason,
                    date = s.date,
                    range = TimeRange(s.from, s.to),
                ),
            ).onSuccess {
                _state.update { it.copy(saved = true) }
            }
        }
    }
}
