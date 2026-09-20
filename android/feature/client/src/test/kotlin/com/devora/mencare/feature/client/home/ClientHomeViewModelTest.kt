package com.devora.mencare.feature.client.home

import app.cash.turbine.test
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.data.fake.DemoSeed
import com.devora.mencare.core.data.fake.FakeAuthRepository
import com.devora.mencare.core.data.fake.FakeBookingRepository
import com.devora.mencare.core.data.fake.FakeCatalogRepository
import com.devora.mencare.core.data.fake.FakeNotificationRepository
import com.devora.mencare.core.data.fake.InMemoryStore
import com.devora.mencare.core.data.network.DataSync
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

private const val MAX_CHIPS = 4
private const val MAX_PER_DAY = 2

class ClientHomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: InMemoryStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * I repository finti non passano da `DataSync` — non c'è niente che possa
     * fallire — ma la ViewModel lo chiede lo stesso per lo stato di
     * caricamento: qui gli si dà un'istanza che gira sui dispatcher del test.
     */
    private fun dataSync() = DataSync(
        object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private fun viewModel() = ClientHomeViewModel(
        FakeAuthRepository(store),
        FakeBookingRepository(store),
        FakeCatalogRepository(store),
        FakeNotificationRepository(store),
        dataSync(),
    )

    private fun signIn(user: User) {
        store.currentUser.value = user
    }

    @Test
    fun `quick slots span all operators and stay inside the caps`() = runTest(dispatcher) {
        signIn(store.users.value.first { it.id == DemoSeed.USER_CLIENT })
        val lastVisit = store.appointments.value
            .filter { it.clientId == DemoSeed.CLIENT_MARCO && it.status == AppointmentStatus.COMPLETED }
            .maxByOrNull { it.start }!!

        viewModel().state.test {
            advanceUntilIdle()
            val slots = expectMostRecentItem().quickSlots

            assertTrue(slots.isNotEmpty())
            assertTrue(slots.size <= MAX_CHIPS)
            assertTrue(slots.groupBy { it.date }.values.all { it.size <= MAX_PER_DAY })
            // Global availability: no chip is pinned to one operator.
            assertTrue(slots.all { it.operatorId == null })
            assertTrue(slots.all { it.serviceIds == lastVisit.serviceIds })
            assertTrue(slots.all { !it.date.isBefore(LocalDate.now()) })
            // Ordered: the nearest slot first.
            assertEquals(slots.sortedBy { it.date.atTime(it.time) }, slots)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a client without history is offered a featured service with any operator`() = runTest(dispatcher) {
        signIn(
            User(
                id = "user_nuovo",
                firstName = "Nuovo",
                lastName = "Cliente",
                email = "nuovo@example.com",
                phone = "+39 000 000 0000",
                role = UserRole.CLIENT,
                memberSince = LocalDate.now(),
                clientRecordId = "cli_nuovo",
            ),
        )

        viewModel().state.test {
            advanceUntilIdle()
            val slots = expectMostRecentItem().quickSlots

            assertTrue(slots.isNotEmpty())
            assertTrue(slots.all { it.operatorId == null })
            assertEquals(listOf(DemoSeed.SVC_RASATURA), slots.first().serviceIds)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
