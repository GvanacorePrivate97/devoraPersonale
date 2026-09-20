package com.devora.mencare.core.data.fake

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.data.repository.TimeBlockRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeTimeBlockRepository @Inject constructor(
    private val store: InMemoryStore,
) : TimeBlockRepository {

    override fun blocksForOperator(operatorId: String, date: LocalDate): Flow<List<TimeBlock>> =
        store.timeBlocks.map { list ->
            list.filter { it.operatorId == operatorId && it.date == date }.sortedBy { it.range.start }
        }

    override fun blocksForWeek(weekStart: LocalDate): Flow<List<TimeBlock>> =
        store.timeBlocks.map { list ->
            val weekEnd = weekStart.plusDays(7)
            list.filter { it.date >= weekStart && it.date < weekEnd }
        }

    override fun upcomingBlocks(from: LocalDate): Flow<List<TimeBlock>> =
        store.timeBlocks.map { list ->
            list.filter { it.date >= from }.sortedWith(compareBy({ it.date }, { it.range.start }))
        }

    override suspend fun conflictsFor(block: TimeBlock): List<Appointment> =
        store.appointments.value.filter {
            it.operatorId == block.operatorId && it.date == block.date && it.isActive &&
                block.range.overlaps(TimeRange(it.time, it.end.toLocalTime()))
        }

    override suspend fun createBlock(block: TimeBlock): AppResult<TimeBlock> {
        if (conflictsFor(block).isNotEmpty()) {
            return AppResult.Failure(AppError.Validation("conflicts"))
        }
        val saved = if (block.id.isBlank()) block.copy(id = store.newId("blk")) else block
        store.timeBlocks.value += saved
        return AppResult.Success(saved)
    }

    override suspend fun deleteBlock(blockId: String): AppResult<Unit> {
        store.timeBlocks.value = store.timeBlocks.value.filterNot { it.id == blockId }
        return AppResult.Success(Unit)
    }
}
