package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.ClientSegment
import kotlinx.coroutines.flow.Flow

interface CrmRepository {
    val clients: Flow<List<ClientRecord>>
    fun client(id: String): Flow<ClientRecord?>
    fun search(query: String, segment: ClientSegment): Flow<List<ClientRecord>>
    suspend fun createClient(firstName: String, lastName: String, phone: String): AppResult<ClientRecord>
}
