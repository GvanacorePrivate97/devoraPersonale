package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.TimeBlock
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TimeBlockRepository {
    fun blocksForOperator(operatorId: String, date: LocalDate): Flow<List<TimeBlock>>
    fun blocksForWeek(weekStart: LocalDate): Flow<List<TimeBlock>>

    /** Blocks from [from] onwards for every operator — holidays and courses. */
    fun upcomingBlocks(from: LocalDate): Flow<List<TimeBlock>>

    /** Active appointments overlapping the candidate block — must be empty before saving. */
    suspend fun conflictsFor(block: TimeBlock): List<Appointment>

    /** Fails with Validation("conflicts") if overlapping appointments remain. */
    suspend fun createBlock(block: TimeBlock): AppResult<TimeBlock>

    suspend fun deleteBlock(blockId: String): AppResult<Unit>
}
