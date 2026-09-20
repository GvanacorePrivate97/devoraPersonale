package com.devora.mencare.core.data.fake

import com.devora.mencare.core.model.AppNotification
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.core.model.BookingChannel
import com.devora.mencare.core.model.CampaignSegment
import com.devora.mencare.core.model.CampaignStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.PushCampaign
import com.devora.mencare.core.model.ReminderRule
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.UserRole
import com.devora.mencare.core.model.WaitlistEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Realistic demo data matching the design mockup. Appointments are seeded
 * relative to today so the demo always shows a live-looking agenda.
 */
object DemoSeed {

    // Operators
    const val OP_ANTONIO = "op_antonio"
    const val OP_LUCA = "op_luca"
    const val OP_GIULIA = "op_giulia"
    const val OP_SARA = "op_sara"

    // Services
    const val SVC_TAGLIO = "svc_taglio"
    const val SVC_TAGLIO_BARBA = "svc_taglio_barba"
    const val SVC_BABY = "svc_baby"
    const val SVC_RASATURA = "svc_rasatura"
    const val SVC_COLORE = "svc_colore"

    // Demo accounts
    const val USER_CLIENT = "user_marco"
    const val USER_STAFF = "user_luca"
    const val USER_OWNER = "user_antonio"
    const val CLIENT_MARCO = "cli_marco"

    @Suppress("LongMethod")
    fun seed(store: InMemoryStore) {
        val today = LocalDate.now()

        val salonHours = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        ).associateWith { listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))) }

        store.salon.value = Salon("Men Care", "Via Scarlatti 120", "Napoli", salonHours)

        store.services.value = listOf(
            Service(SVC_TAGLIO, "Shampoo + taglio", 45, 1500, "lavaggio e piega"),
            Service(SVC_TAGLIO_BARBA, "Shampoo + taglio + barba", 60, 2000),
            Service(SVC_BABY, "Taglio baby men", 30, 1000, "bambini 2–8 anni"),
            Service(SVC_RASATURA, "Rasatura o sfumatura barba", 30, 700, "definizioni a rasoio", featured = true),
            Service(SVC_COLORE, "Sfumatura barba + colore", 10, 1000, featured = true),
        )

        val fullDay = listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), TimeRange(LocalTime.of(14, 0), LocalTime.of(19, 0)))
        val lateDay = listOf(TimeRange(LocalTime.of(10, 0), LocalTime.of(19, 0)))
        val workWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

        store.operators.value = listOf(
            Operator(
                OP_ANTONIO, "Antonio De Vito", "Titolare", "Master barber · dal 2009",
                listOf("Classico", "Rasoio", "Barba"), isOwner = true,
                weeklyHours = workWeek.drop(1).associateWith { fullDay },
                serviceIds = setOf(SVC_TAGLIO, SVC_TAGLIO_BARBA, SVC_RASATURA),
            ),
            Operator(
                OP_LUCA, "Luca Ferrante", "Barbiere", "Barbiere · fade & texture",
                listOf("Fade", "Texture"),
                weeklyHours = workWeek.associateWith { fullDay },
                serviceIds = setOf(SVC_TAGLIO, SVC_TAGLIO_BARBA, SVC_RASATURA, SVC_COLORE),
            ),
            Operator(
                OP_GIULIA, "Giulia Marchetti", "Hair stylist", "Hair stylist · colore e forbici",
                listOf("Colore", "Forbici"),
                weeklyHours = workWeek.take(5).associateWith { lateDay },
                serviceIds = setOf(SVC_TAGLIO, SVC_BABY),
            ),
            Operator(
                OP_SARA, "Sara Coppola", "Barber", "Barber · rasoio tradizionale",
                listOf("Barba", "Rasoio"),
                weeklyHours = workWeek.drop(2).associateWith { lateDay },
                serviceIds = setOf(SVC_BABY, SVC_RASATURA, SVC_COLORE),
            ),
        )

        store.users.value = listOf(
            User(
                USER_CLIENT, "Marco", "Esposito", "marco.esposito@gmail.com", "+39 347 812 4490",
                UserRole.CLIENT, memberSince = LocalDate.of(2024, 3, 12), visitCount = 14,
                clientRecordId = CLIENT_MARCO,
            ),
            User(
                USER_STAFF, "Luca", "Ferrante", "luca.ferrante@mencare.it", "+39 340 221 8734",
                UserRole.STAFF, memberSince = LocalDate.of(2019, 5, 2),
                operatorId = OP_LUCA,
            ),
            User(
                USER_OWNER, "Antonio", "De Vito", "antonio@mencare.it", "+39 335 660 1200",
                UserRole.OWNER, memberSince = LocalDate.of(2009, 1, 10),
                operatorId = OP_ANTONIO,
            ),
        )

        seedClients(store, today)
        seedAppointments(store, today)

        store.notificationSettings.value = NotificationSettings(
            reminders = listOf(ReminderRule("rem_1", 24), ReminderRule("rem_2", 2)),
        )

        store.campaigns.value = listOf(
            PushCampaign(
                id = "camp_inattivi",
                name = "Recupero inattivi settembre",
                segment = CampaignSegment.INATTIVI_60,
                title = "Ci manchi, {{nome}}!",
                body = "È passato un po' di tempo. Prenota il tuo prossimo taglio: {{link}}",
                reachableCount = 198,
                segmentSize = 214,
                status = CampaignStatus.DRAFT,
            ),
        )

        store.notifications.value = listOf(
            // Cliente
            AppNotification(
                "ntf_1", USER_CLIENT, "Promemoria appuntamento",
                "Ci vediamo domani alle 17:30 con Antonio. Rispondi per spostare.",
                store.now().minusHours(2),
            ),
            AppNotification(
                "ntf_2", USER_CLIENT, "Lista d'attesa",
                "Si è liberato uno slot venerdì alle 17:30 con Antonio.",
                store.now().minusDays(1), read = true,
            ),
            // Operatore
            AppNotification(
                "ntf_3", USER_STAFF, "Nuova prenotazione",
                "Marco Esposito ha prenotato Shampoo + taglio domani alle 10:30.",
                store.now().minusMinutes(40),
            ),
            AppNotification(
                "ntf_4", USER_STAFF, "Appuntamento annullato",
                "Davide Russo ha annullato l'appuntamento di venerdì alle 16:00.",
                store.now().minusHours(5),
            ),
            AppNotification(
                "ntf_5", USER_STAFF, "Agenda di domani",
                "Domani hai 5 appuntamenti, il primo alle 9:30.",
                store.now().minusDays(2), read = true,
            ),
            // Titolare
            AppNotification(
                "ntf_6", USER_OWNER, "Nuova prenotazione",
                "Marco Esposito ha prenotato con Luca domani alle 10:30.",
                store.now().minusMinutes(40),
            ),
            AppNotification(
                "ntf_7", USER_OWNER, "Appuntamento annullato",
                "Davide Russo ha annullato l'appuntamento con Giulia di venerdì alle 16:00.",
                store.now().minusHours(5),
            ),
            AppNotification(
                "ntf_8", USER_OWNER, "Campagna pronta",
                "La bozza \"Recupero inattivi settembre\" raggiunge 198 clienti.",
                store.now().minusDays(1), read = true,
            ),
        )
    }

    private fun seedClients(store: InMemoryStore, today: LocalDate) {
        fun cli(
            id: String, first: String, last: String, phone: String, sinceYear: Int,
            visits: Int, spend: Long, noShows: Int = 0, lastVisitDaysAgo: Long?,
            prefServices: List<String> = emptyList(), prefOp: String? = null,
        ) = ClientRecord(
            id, first, last, phone, "${first.lowercase()}.${last.lowercase()}@gmail.com",
            LocalDate.of(sinceYear, 3, 12), visits, spend, noShows,
            lastVisitDaysAgo?.let { today.minusDays(it) }, prefServices, prefOp,
        )

        store.clients.value = listOf(
            cli(
                CLIENT_MARCO, "Marco", "Esposito", "+39 347 812 4490", 2024, 14, 12_800, 0, 8,
                prefServices = listOf(SVC_TAGLIO, SVC_RASATURA), prefOp = OP_ANTONIO,
            ),
            cli("cli_davide", "Davide", "Russo", "+39 348 771 2094", 2023, 22, 41_500, 0, 1, prefServices = listOf(SVC_TAGLIO_BARBA), prefOp = OP_LUCA),
            cli("cli_gennaro", "Gennaro", "Aiello", "+39 333 402 5561", 2022, 31, 24_700, 1, 5, prefServices = listOf(SVC_RASATURA)),
            cli("cli_salvatore", "Salvatore", "Cinque", "+39 339 118 6402", 2021, 45, 88_000, 0, 12, prefServices = listOf(SVC_TAGLIO_BARBA), prefOp = OP_ANTONIO),
            cli("cli_antonio_g", "Antonio", "Guida", "+39 320 555 7821", 2025, 3, 2_700, 0, 30),
            cli("cli_ciro", "Ciro", "Espo", "+39 366 902 1145", 2023, 18, 27_000, 0, 15, prefOp = OP_LUCA),
            cli("cli_paolo", "Paolo", "Ferri", "+39 347 220 9310", 2022, 8, 11_200, 2, 75),
            cli("cli_luigi", "Luigi", "Amato", "+39 331 774 0921", 2024, 5, 6_500, 0, 90),
            cli("cli_franco", "Franco", "Vitale", "+39 338 410 5578", 2021, 12, 16_400, 3, 68),
            cli("cli_peppe", "Giuseppe", "Riccio", "+39 345 660 2287", 2023, 26, 52_000, 0, 3, prefServices = listOf(SVC_TAGLIO), prefOp = OP_GIULIA),
            cli("cli_mario", "Mario", "Sorrentino", "+39 349 802 6634", 2025, 2, 2_200, 0, 110),
            cli("cli_enzo", "Vincenzo", "Longo", "+39 328 917 4450", 2022, 38, 61_000, 0, 9, prefOp = OP_ANTONIO),
        )
    }

    @Suppress("LongMethod")
    /**
     * Visite del mese passato e due assenze: servono a dashboard e scheda
     * cliente per avere numeri credibili appena si apre l'app.
     */
    private fun pastMonthVolume(
        today: LocalDate,
        openDay: (LocalDate) -> LocalDate,
        apt: (String, String, List<String>, LocalDate, LocalTime, AppointmentStatus) -> Appointment,
    ): List<Appointment> {
        val completed = (1..4L).flatMap { weekAgo ->
            listOf(
                "cli_davide" to OP_LUCA, "cli_gennaro" to OP_SARA, "cli_salvatore" to OP_ANTONIO,
                "cli_peppe" to OP_GIULIA, "cli_enzo" to OP_ANTONIO,
            ).mapIndexed { i, (clientId, operatorId) ->
                val date = openDay(today.minusWeeks(weekAgo).plusDays(i.toLong() % 5))
                val services = if (i % 2 == 0) listOf(SVC_TAGLIO) else listOf(SVC_TAGLIO_BARBA)
                apt(clientId, operatorId, services, date, LocalTime.of(9 + i, 0), AppointmentStatus.COMPLETED)
            }
        }
        val noShows = listOf(
            apt(
                "cli_paolo", OP_LUCA, listOf(SVC_TAGLIO), openDay(today.minusDays(10)),
                LocalTime.of(12, 0), AppointmentStatus.NO_SHOW,
            ),
            apt(
                "cli_franco", OP_SARA, listOf(SVC_RASATURA), openDay(today.minusDays(6)),
                LocalTime.of(17, 0), AppointmentStatus.NO_SHOW,
            ),
        )
        return completed + noShows
    }

    private fun seedAppointments(store: InMemoryStore, today: LocalDate) {
        var seq = 0
        fun id() = "apt_seed_${++seq}"

        fun apt(
            clientId: String, opId: String, svcIds: List<String>, date: LocalDate, time: LocalTime,
            status: AppointmentStatus, channel: BookingChannel = BookingChannel.APP,
            cancelledBy: CancellationActor? = null, note: String? = null,
        ): Appointment {
            val services = store.services.value.filter { it.id in svcIds }
            return Appointment(
                id(), clientId, opId, svcIds, date.atTime(time),
                durationMinutes = services.sumOf { it.durationMinutes },
                totalPriceCents = services.sumOf { it.priceCents },
                status = status, channel = channel,
                cancelledBy = cancelledBy, noteForOperator = note,
            )
        }

        // Skip Sunday for anything that must land on an open day.
        fun openDay(date: LocalDate): LocalDate =
            if (date.dayOfWeek == DayOfWeek.SUNDAY) date.plusDays(1) else date

        val appointments = mutableListOf<Appointment>()

        // --- Marco (demo client) ---
        appointments += apt(
            CLIENT_MARCO, OP_ANTONIO, listOf(SVC_TAGLIO, SVC_RASATURA),
            openDay(today), LocalTime.of(17, 30), AppointmentStatus.CONFIRMED,
        )
        appointments += apt(
            CLIENT_MARCO, OP_ANTONIO, listOf(SVC_TAGLIO, SVC_RASATURA),
            openDay(today.plusWeeks(3)), LocalTime.of(17, 30), AppointmentStatus.CONFIRMED,
        )
        appointments += apt(CLIENT_MARCO, OP_LUCA, listOf(SVC_TAGLIO), openDay(today.minusDays(21)), LocalTime.of(18, 0), AppointmentStatus.COMPLETED)
        appointments += apt(CLIENT_MARCO, OP_LUCA, listOf(SVC_COLORE), openDay(today.minusDays(60)), LocalTime.of(17, 0), AppointmentStatus.COMPLETED)
        appointments += apt(CLIENT_MARCO, OP_ANTONIO, listOf(SVC_TAGLIO, SVC_RASATURA), openDay(today.minusDays(42)), LocalTime.of(17, 30), AppointmentStatus.COMPLETED)
        appointments += apt(CLIENT_MARCO, OP_LUCA, listOf(SVC_TAGLIO_BARBA), openDay(today.minusDays(84)), LocalTime.of(11, 0), AppointmentStatus.COMPLETED)
        appointments += apt(
            CLIENT_MARCO, OP_ANTONIO, listOf(SVC_TAGLIO), openDay(today.minusDays(100)),
            LocalTime.of(10, 0), AppointmentStatus.CANCELLED, cancelledBy = CancellationActor.CLIENT,
        )

        // --- Luca's agenda today (staff demo) ---
        val agendaDay = openDay(today)
        appointments += apt("cli_mario", OP_LUCA, listOf(SVC_TAGLIO), agendaDay, LocalTime.of(9, 0), AppointmentStatus.CONFIRMED)
        appointments += apt("cli_davide", OP_LUCA, listOf(SVC_TAGLIO_BARBA), agendaDay, LocalTime.of(10, 0), AppointmentStatus.IN_PROGRESS)
        appointments += apt("cli_gennaro", OP_LUCA, listOf(SVC_RASATURA), agendaDay, LocalTime.of(11, 30), AppointmentStatus.CONFIRMED)
        appointments += apt(
            "cli_salvatore", OP_LUCA, listOf(SVC_TAGLIO_BARBA), agendaDay, LocalTime.of(14, 0),
            AppointmentStatus.CONFIRMED, note = "Cliente abituale, sfumatura media.",
        )
        appointments += apt(
            "cli_antonio_g", OP_LUCA, listOf(SVC_COLORE), agendaDay, LocalTime.of(15, 30),
            AppointmentStatus.CONFIRMED, channel = BookingChannel.WALK_IN,
        )

        // --- Rest of the week across operators (admin weekly agenda) ---
        for (dayOffset in 0..5L) {
            val d = today.plusDays(dayOffset)
            if (d.dayOfWeek == DayOfWeek.SUNDAY) continue
            appointments += apt("cli_ciro", OP_ANTONIO, listOf(SVC_TAGLIO_BARBA), d, LocalTime.of(10, 0), AppointmentStatus.CONFIRMED)
            appointments += apt("cli_peppe", OP_GIULIA, listOf(SVC_TAGLIO), d, LocalTime.of(11, 0), AppointmentStatus.CONFIRMED)
            if (dayOffset % 2 == 0L) {
                appointments += apt("cli_enzo", OP_ANTONIO, listOf(SVC_TAGLIO), d, LocalTime.of(15, 0), AppointmentStatus.CONFIRMED)
                appointments += apt("cli_davide", OP_SARA, listOf(SVC_RASATURA), d, LocalTime.of(16, 0), AppointmentStatus.CONFIRMED, channel = BookingChannel.PHONE)
            }
        }

        // --- Antonio fully booked a week from now: the wizard's "Avvisami" page ---
        var fullDay = today.plusDays(7)
        while (fullDay.dayOfWeek == DayOfWeek.SUNDAY || fullDay.dayOfWeek == DayOfWeek.MONDAY) fullDay = fullDay.plusDays(1)
        val fullDayClients = listOf("cli_davide", "cli_gennaro", "cli_salvatore", "cli_peppe", "cli_enzo", "cli_ciro")
        listOf(9, 10, 11, 12, 14, 15, 16, 17, 18).forEachIndexed { i, hour ->
            appointments += apt(
                fullDayClients[i % fullDayClients.size], OP_ANTONIO, listOf(SVC_TAGLIO_BARBA), fullDay,
                LocalTime.of(hour, 0), AppointmentStatus.CONFIRMED,
            )
        }

        // Lo storico del mese e i no-show stanno a parte: sono la materia prima
        // delle statistiche, non l'agenda che si vede a schermo.
        appointments += pastMonthVolume(today, ::openDay, ::apt)

        store.appointments.value = appointments

        // Lunch break for Luca, a training block for Giulia, and a week of
        // holidays so the owner's operator sheet has something to show.
        val holidayStart = today.plusDays(20)
        val fullWorkDay = TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))
        store.timeBlocks.value = listOf(
            TimeBlock(
                "blk_lunch_luca", OP_LUCA, BlockReason.PAUSA, agendaDay,
                TimeRange(LocalTime.of(13, 0), LocalTime.of(14, 0)), label = "Pausa pranzo",
            ),
            TimeBlock(
                "blk_corso_giulia", OP_GIULIA, BlockReason.CORSO, openDay(today.plusDays(2)),
                TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0)), label = "Formazione",
            ),
        ) + (0L until 7L).map { offset ->
            TimeBlock(
                "blk_ferie_luca_$offset", OP_LUCA, BlockReason.FERIE,
                holidayStart.plusDays(offset), fullWorkDay,
            )
        }

        // Marco in attesa sulla giornata piena di Antonio, a qualsiasi orario:
        // una lista d'attesa ha senso solo su un giorno davvero pieno.
        store.waitlist.value = listOf(
            WaitlistEntry(
                "wl_marco", CLIENT_MARCO, fullDay, null, OP_ANTONIO,
                listOf(SVC_TAGLIO, SVC_RASATURA), 75, 2200, position = 1,
            ),
        )
    }
}
