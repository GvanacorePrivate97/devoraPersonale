package com.devora.mencare.feature.admin.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.CAMPAIGN_BODY_MAX
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateRequiredText
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.AdminRepository
import com.devora.mencare.core.model.CampaignSegment
import com.devora.mencare.core.model.CampaignStatus
import com.devora.mencare.core.model.PushCampaign
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class CampaignUiState(
    val id: String = "",
    val name: String = "",
    val segment: CampaignSegment = CampaignSegment.INATTIVI_60,
    val title: String = "",
    val body: String = "",
    val scheduleLater: Boolean = false,
    val scheduledDate: LocalDate = LocalDate.now().plusDays(1),
    val scheduledTime: LocalTime = LocalTime.of(10, 30),
    val repeatWeekly: Boolean = false,
    val sendCap: Int = 4,
    val reachable: Int = 0,
    val segmentSize: Int = 0,
    val nameError: ValidationError? = null,
    val bodyError: ValidationError? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val done: Boolean = false,
) {
    /** Push preview with merge tokens personalised for the sample client. */
    val previewBody: String
        get() = body
            .replace("{{nome}}", "Marco")
            .replace("{{link}}", "mencare.app/prenota")
}

@HiltViewModel
class CampaignViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CampaignUiState())
    val state: StateFlow<CampaignUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Continue the seeded draft if present.
            adminRepository.campaigns.first().firstOrNull { it.status == CampaignStatus.DRAFT }?.let { draft ->
                _state.update {
                    it.copy(
                        id = draft.id,
                        name = draft.name,
                        segment = draft.segment,
                        title = draft.title,
                        body = draft.body,
                        repeatWeekly = draft.repeatWeekly,
                        sendCap = draft.sendCap ?: 4,
                        scheduleLater = draft.scheduledAt != null,
                        scheduledDate = draft.scheduledAt?.toLocalDate() ?: LocalDate.now().plusDays(1),
                        scheduledTime = draft.scheduledAt?.toLocalTime() ?: LocalTime.of(10, 30),
                    )
                }
            }
            refreshReach()
        }
    }

    private fun refreshReach() {
        viewModelScope.launch {
            val (reachable, size) = adminRepository.reachFor(_state.value.segment)
            _state.update { it.copy(reachable = reachable, segmentSize = size) }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = null, saveFailed = false) }
    fun setTitle(value: String) = _state.update { it.copy(title = value, saveFailed = false) }
    fun setBody(value: String) = _state.update {
        it.copy(body = value.take(CAMPAIGN_BODY_MAX), bodyError = null, saveFailed = false)
    }

    fun setSegment(segment: CampaignSegment) {
        _state.update { it.copy(segment = segment) }
        refreshReach()
    }

    fun appendToken(token: String) = _state.update {
        it.copy(body = (it.body + token).take(CAMPAIGN_BODY_MAX))
    }

    fun setScheduleLater(later: Boolean) = _state.update { it.copy(scheduleLater = later) }
    fun setScheduledDate(date: LocalDate) = _state.update { it.copy(scheduledDate = date) }
    fun setScheduledTime(time: LocalTime) = _state.update { it.copy(scheduledTime = time) }
    fun setRepeatWeekly(repeat: Boolean) = _state.update { it.copy(repeatWeekly = repeat) }
    fun setSendCap(cap: Int) = _state.update { it.copy(sendCap = cap) }

    fun send() {
        val s = _state.value
        if (s.saving) return
        // Niente più "Invia" che non fa nulla: nome e messaggio dicono cosa manca.
        val name = validateRequiredText(s.name)
        val body = validateRequiredText(s.body, CAMPAIGN_BODY_MAX)
        val nameError = name.errorOrNull()
        val bodyError = body.errorOrNull()
        if (nameError != null || bodyError != null) {
            _state.update { it.copy(nameError = nameError, bodyError = bodyError, saveFailed = false) }
            return
        }
        val campaign = PushCampaign(
            id = s.id,
            name = name.valueOrNull().orEmpty(),
            segment = s.segment,
            title = s.title.trim(),
            body = body.valueOrNull().orEmpty(),
            scheduledAt = if (s.scheduleLater) LocalDateTime.of(s.scheduledDate, s.scheduledTime) else null,
            repeatWeekly = s.repeatWeekly,
            sendCap = if (s.repeatWeekly) s.sendCap else null,
            reachableCount = s.reachable,
            segmentSize = s.segmentSize,
            status = if (s.scheduleLater) CampaignStatus.SCHEDULED else CampaignStatus.SENT,
        )
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveFailed = false) }
            adminRepository.saveCampaign(campaign)
                .onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }
}
