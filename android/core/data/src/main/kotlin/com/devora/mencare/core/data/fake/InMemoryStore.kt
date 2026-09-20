package com.devora.mencare.core.data.fake

import com.devora.mencare.core.model.AppNotification
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.ClientNotificationPrefs
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.PushCampaign
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.WaitlistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all fake repositories. Every mutation goes through
 * the [MutableStateFlow]s so any screen observing the data updates live.
 */
@Singleton
class InMemoryStore @Inject constructor() {

    val salon = MutableStateFlow(Salon("", "", ""))
    val services = MutableStateFlow<List<Service>>(emptyList())
    val operators = MutableStateFlow<List<Operator>>(emptyList())
    val holidays = MutableStateFlow<List<Holiday>>(emptyList())
    val users = MutableStateFlow<List<User>>(emptyList())
    val clients = MutableStateFlow<List<ClientRecord>>(emptyList())
    val appointments = MutableStateFlow<List<Appointment>>(emptyList())
    val waitlist = MutableStateFlow<List<WaitlistEntry>>(emptyList())
    val timeBlocks = MutableStateFlow<List<TimeBlock>>(emptyList())
    val campaigns = MutableStateFlow<List<PushCampaign>>(emptyList())
    val notificationSettings = MutableStateFlow(NotificationSettings(emptyList()))
    val notifications = MutableStateFlow<List<AppNotification>>(emptyList())

    val currentUser = MutableStateFlow<User?>(null)
    val clientPrefs = MutableStateFlow(ClientNotificationPrefs())

    private val counter = AtomicLong(1000)

    fun newId(prefix: String): String = "${prefix}_${counter.incrementAndGet()}"

    fun now(): LocalDateTime = LocalDateTime.now()

    init {
        DemoSeed.seed(this)
    }
}
