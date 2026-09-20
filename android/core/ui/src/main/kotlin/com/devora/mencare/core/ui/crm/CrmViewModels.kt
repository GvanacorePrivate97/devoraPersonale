package com.devora.mencare.core.ui.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.ClientSegment
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CrmListUiState(
    val query: String = "",
    val segment: ClientSegment = ClientSegment.TUTTI,
    val clients: List<ClientRecord> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CrmListViewModel @Inject constructor(
    private val crmRepository: CrmRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val segment = MutableStateFlow(ClientSegment.TUTTI)

    val state = combine(
        query,
        segment,
        combine(query, segment) { q, s -> q to s }.flatMapLatest { (q, s) -> crmRepository.search(q, s) },
    ) { q, s, results ->
        CrmListUiState(query = q, segment = s, clients = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrmListUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSegment(value: ClientSegment) {
        segment.value = value
    }
}

data class CrmDetailUiState(
    val client: ClientRecord? = null,
    val history: List<Appointment> = emptyList(),
    val services: Map<String, Service> = emptyMap(),
    val operators: Map<String, Operator> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CrmDetailViewModel @Inject constructor(
    crmRepository: CrmRepository,
    bookingRepository: BookingRepository,
    catalogRepository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    val state = combine(
        crmRepository.client(clientId),
        bookingRepository.appointmentsForClient(clientId),
        catalogRepository.services,
        catalogRepository.operators,
    ) { client, appointments, services, operators ->
        CrmDetailUiState(
            client = client,
            history = appointments.sortedByDescending { it.start },
            services = services.associateBy { it.id },
            operators = operators.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrmDetailUiState())
}
