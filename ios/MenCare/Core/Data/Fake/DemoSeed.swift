import Foundation

/// Realistic demo data matching the design mockup. Appointments are seeded
/// relative to today so the demo always shows a live-looking agenda.
/// Direct port of the Android `DemoSeed` — keep the two in sync.
enum DemoSeed {

    // Operators
    static let opAntonio = "op_antonio"
    static let opLuca = "op_luca"
    static let opGiulia = "op_giulia"
    static let opSara = "op_sara"

    // Services
    static let svcTaglio = "svc_taglio"
    static let svcTaglioBarba = "svc_taglio_barba"
    static let svcBaby = "svc_baby"
    static let svcRasatura = "svc_rasatura"
    static let svcColore = "svc_colore"

    // Demo accounts
    static let userClient = "user_marco"
    static let userStaff = "user_luca"
    static let userOwner = "user_antonio"
    static let clientMarco = "cli_marco"

    static func seed(_ store: InMemoryStore) {
        let today = LocalDate.today()

        let workWeek: [DayOfWeek] = [.monday, .tuesday, .wednesday, .thursday, .friday, .saturday]
        let salonHours = Dictionary(uniqueKeysWithValues: workWeek.map {
            ($0, [TimeRange(LocalTime(9, 0), LocalTime(19, 0))])
        })
        store.salon = Salon(name: "Men Care", address: "Via Scarlatti 120", city: "Napoli", weeklyHours: salonHours)

        store.services = [
            Service(id: svcTaglio, name: "Shampoo + taglio", durationMinutes: 45, priceCents: 1500, description: "lavaggio e piega"),
            Service(id: svcTaglioBarba, name: "Shampoo + taglio + barba", durationMinutes: 60, priceCents: 2000),
            Service(id: svcBaby, name: "Taglio baby men", durationMinutes: 30, priceCents: 1000, description: "bambini 2–8 anni"),
            Service(id: svcRasatura, name: "Rasatura o sfumatura barba", durationMinutes: 30, priceCents: 700, description: "definizioni a rasoio", featured: true),
            Service(id: svcColore, name: "Sfumatura barba + colore", durationMinutes: 10, priceCents: 1000, featured: true),
        ]

        let fullDay = [TimeRange(LocalTime(9, 0), LocalTime(13, 0)), TimeRange(LocalTime(14, 0), LocalTime(19, 0))]
        let lateDay = [TimeRange(LocalTime(10, 0), LocalTime(19, 0))]
        func hours(_ days: [DayOfWeek], _ ranges: [TimeRange]) -> [DayOfWeek: [TimeRange]] {
            Dictionary(uniqueKeysWithValues: days.map { ($0, ranges) })
        }

        store.operators = [
            Operator(
                id: opAntonio, name: "Antonio De Vito", title: "Titolare", bio: "Master barber · dal 2009",
                specialties: ["Classico", "Rasoio", "Barba"], isOwner: true,
                weeklyHours: hours(Array(workWeek.dropFirst()), fullDay),
                serviceIds: [svcTaglio, svcTaglioBarba, svcRasatura]
            ),
            Operator(
                id: opLuca, name: "Luca Ferrante", title: "Barbiere", bio: "Barbiere · fade & texture",
                specialties: ["Fade", "Texture"],
                weeklyHours: hours(workWeek, fullDay),
                serviceIds: [svcTaglio, svcTaglioBarba, svcRasatura, svcColore]
            ),
            Operator(
                id: opGiulia, name: "Giulia Marchetti", title: "Hair stylist", bio: "Hair stylist · colore e forbici",
                specialties: ["Colore", "Forbici"],
                weeklyHours: hours(Array(workWeek.prefix(5)), lateDay),
                serviceIds: [svcTaglio, svcBaby]
            ),
            Operator(
                id: opSara, name: "Sara Coppola", title: "Barber", bio: "Barber · rasoio tradizionale",
                specialties: ["Barba", "Rasoio"],
                weeklyHours: hours(Array(workWeek.dropFirst(2)), lateDay),
                serviceIds: [svcBaby, svcRasatura, svcColore]
            ),
        ]

        store.users = [
            User(
                id: userClient, firstName: "Marco", lastName: "Esposito", email: "marco.esposito@gmail.com",
                phone: "+39 347 812 4490", role: .client, memberSince: LocalDate(year: 2024, month: 3, day: 12),
                visitCount: 14, clientRecordId: clientMarco
            ),
            User(
                id: userStaff, firstName: "Luca", lastName: "Ferrante", email: "luca.ferrante@mencare.it",
                phone: "+39 340 221 8734", role: .staff, memberSince: LocalDate(year: 2019, month: 5, day: 2),
                operatorId: opLuca
            ),
            User(
                id: userOwner, firstName: "Antonio", lastName: "De Vito", email: "antonio@mencare.it",
                phone: "+39 335 660 1200", role: .owner, memberSince: LocalDate(year: 2009, month: 1, day: 10),
                operatorId: opAntonio
            ),
        ]

        seedClients(store, today: today)
        seedAppointments(store, today: today)

        store.notificationSettings = NotificationSettings(
            reminders: [ReminderRule(id: "rem_1", hoursBefore: 24), ReminderRule(id: "rem_2", hoursBefore: 2)]
        )

        store.campaigns = [
            PushCampaign(
                id: "camp_inattivi",
                name: "Recupero inattivi settembre",
                segment: .inattivi60,
                title: "Ci manchi, {{nome}}!",
                body: "È passato un po' di tempo. Prenota il tuo prossimo taglio: {{link}}",
                reachableCount: 198,
                segmentSize: 214,
                status: .draft
            ),
        ]

        store.notifications = [
            // Cliente
            AppNotification(
                id: "ntf_1", userId: userClient, title: "Promemoria appuntamento",
                body: "Ci vediamo domani alle 17:30 con Antonio. Rispondi per spostare.",
                at: store.now().minusHours(2)
            ),
            AppNotification(
                id: "ntf_2", userId: userClient, title: "Lista d'attesa",
                body: "Si è liberato uno slot venerdì alle 17:30 con Antonio.",
                at: store.now().minusDays(1), read: true
            ),
            // Operatore
            AppNotification(
                id: "ntf_3", userId: userStaff, title: "Nuova prenotazione",
                body: "Marco Esposito ha prenotato Shampoo + taglio domani alle 10:30.",
                at: store.now().minusMinutes(40)
            ),
            AppNotification(
                id: "ntf_4", userId: userStaff, title: "Appuntamento annullato",
                body: "Davide Russo ha annullato l'appuntamento di venerdì alle 16:00.",
                at: store.now().minusHours(5)
            ),
            AppNotification(
                id: "ntf_5", userId: userStaff, title: "Agenda di domani",
                body: "Domani hai 5 appuntamenti, il primo alle 9:30.",
                at: store.now().minusDays(2), read: true
            ),
            // Titolare
            AppNotification(
                id: "ntf_6", userId: userOwner, title: "Nuova prenotazione",
                body: "Marco Esposito ha prenotato con Luca domani alle 10:30.",
                at: store.now().minusMinutes(40)
            ),
            AppNotification(
                id: "ntf_7", userId: userOwner, title: "Appuntamento annullato",
                body: "Davide Russo ha annullato l'appuntamento con Giulia di venerdì alle 16:00.",
                at: store.now().minusHours(5)
            ),
            AppNotification(
                id: "ntf_8", userId: userOwner, title: "Campagna pronta",
                body: "La bozza \"Recupero inattivi settembre\" raggiunge 198 clienti.",
                at: store.now().minusDays(1), read: true
            ),
        ]
    }

    private static func seedClients(_ store: InMemoryStore, today: LocalDate) {
        func cli(
            _ id: String, _ first: String, _ last: String, _ phone: String, _ sinceYear: Int,
            _ visits: Int, _ spend: Int64, noShows: Int = 0, lastVisitDaysAgo: Int?,
            prefServices: [String] = [], prefOp: String? = nil
        ) -> ClientRecord {
            ClientRecord(
                id: id, firstName: first, lastName: last, phone: phone,
                email: "\(first.lowercased()).\(last.lowercased())@gmail.com",
                customerSince: LocalDate(year: sinceYear, month: 3, day: 12),
                visitCount: visits, lifetimeSpendCents: spend, noShowCount: noShows,
                lastVisit: lastVisitDaysAgo.map { today.minusDays($0) },
                preferredServiceIds: prefServices, preferredOperatorId: prefOp
            )
        }

        store.clients = [
            cli(
                clientMarco, "Marco", "Esposito", "+39 347 812 4490", 2024, 14, 12_800, lastVisitDaysAgo: 8,
                prefServices: [svcTaglio, svcRasatura], prefOp: opAntonio
            ),
            cli("cli_davide", "Davide", "Russo", "+39 348 771 2094", 2023, 22, 41_500, lastVisitDaysAgo: 1, prefServices: [svcTaglioBarba], prefOp: opLuca),
            cli("cli_gennaro", "Gennaro", "Aiello", "+39 333 402 5561", 2022, 31, 24_700, noShows: 1, lastVisitDaysAgo: 5, prefServices: [svcRasatura]),
            cli("cli_salvatore", "Salvatore", "Cinque", "+39 339 118 6402", 2021, 45, 88_000, lastVisitDaysAgo: 12, prefServices: [svcTaglioBarba], prefOp: opAntonio),
            cli("cli_antonio_g", "Antonio", "Guida", "+39 320 555 7821", 2025, 3, 2_700, lastVisitDaysAgo: 30),
            cli("cli_ciro", "Ciro", "Espo", "+39 366 902 1145", 2023, 18, 27_000, lastVisitDaysAgo: 15, prefOp: opLuca),
            cli("cli_paolo", "Paolo", "Ferri", "+39 347 220 9310", 2022, 8, 11_200, noShows: 2, lastVisitDaysAgo: 75),
            cli("cli_luigi", "Luigi", "Amato", "+39 331 774 0921", 2024, 5, 6_500, lastVisitDaysAgo: 90),
            cli("cli_franco", "Franco", "Vitale", "+39 338 410 5578", 2021, 12, 16_400, noShows: 3, lastVisitDaysAgo: 68),
            cli("cli_peppe", "Giuseppe", "Riccio", "+39 345 660 2287", 2023, 26, 52_000, lastVisitDaysAgo: 3, prefServices: [svcTaglio], prefOp: opGiulia),
            cli("cli_mario", "Mario", "Sorrentino", "+39 349 802 6634", 2025, 2, 2_200, lastVisitDaysAgo: 110),
            cli("cli_enzo", "Vincenzo", "Longo", "+39 328 917 4450", 2022, 38, 61_000, lastVisitDaysAgo: 9, prefOp: opAntonio),
        ]
    }

    private static func seedAppointments(_ store: InMemoryStore, today: LocalDate) {
        var seq = 0
        func nextId() -> String {
            seq += 1
            return "apt_seed_\(seq)"
        }

        func apt(
            _ clientId: String, _ opId: String, _ svcIds: [String], _ date: LocalDate, _ time: LocalTime,
            _ status: AppointmentStatus, channel: BookingChannel = .app,
            cancelledBy: CancellationActor? = nil, note: String? = nil
        ) -> Appointment {
            let services = store.services.filter { svcIds.contains($0.id) }
            return Appointment(
                id: nextId(), clientId: clientId, operatorId: opId, serviceIds: svcIds,
                start: date.atTime(time),
                durationMinutes: services.reduce(0) { $0 + $1.durationMinutes },
                totalPriceCents: services.reduce(0) { $0 + $1.priceCents },
                status: status, channel: channel, noteForOperator: note,
                cancelledBy: cancelledBy
            )
        }

        // Skip Sunday for anything that must land on an open day.
        func openDay(_ date: LocalDate) -> LocalDate {
            date.dayOfWeek == .sunday ? date.plusDays(1) : date
        }

        var appointments: [Appointment] = []

        // --- Marco (demo client) ---
        appointments.append(apt(clientMarco, opAntonio, [svcTaglio, svcRasatura], openDay(today), LocalTime(17, 30), .confirmed))
        appointments.append(apt(clientMarco, opAntonio, [svcTaglio, svcRasatura], openDay(today.plusWeeks(3)), LocalTime(17, 30), .confirmed))
        appointments.append(apt(clientMarco, opLuca, [svcTaglio], openDay(today.minusDays(21)), LocalTime(18, 0), .completed))
        appointments.append(apt(clientMarco, opLuca, [svcColore], openDay(today.minusDays(60)), LocalTime(17, 0), .completed))
        appointments.append(apt(clientMarco, opAntonio, [svcTaglio, svcRasatura], openDay(today.minusDays(42)), LocalTime(17, 30), .completed))
        appointments.append(apt(clientMarco, opLuca, [svcTaglioBarba], openDay(today.minusDays(84)), LocalTime(11, 0), .completed))
        appointments.append(apt(clientMarco, opAntonio, [svcTaglio], openDay(today.minusDays(100)), LocalTime(10, 0), .cancelled, cancelledBy: .client))

        // --- Luca's agenda today (staff demo) ---
        let agendaDay = openDay(today)
        appointments.append(apt("cli_mario", opLuca, [svcTaglio], agendaDay, LocalTime(9, 0), .confirmed))
        appointments.append(apt("cli_davide", opLuca, [svcTaglioBarba], agendaDay, LocalTime(10, 0), .inProgress))
        appointments.append(apt("cli_gennaro", opLuca, [svcRasatura], agendaDay, LocalTime(11, 30), .confirmed))
        appointments.append(apt("cli_salvatore", opLuca, [svcTaglioBarba], agendaDay, LocalTime(14, 0), .confirmed, note: "Cliente abituale, sfumatura media."))
        appointments.append(apt("cli_antonio_g", opLuca, [svcColore], agendaDay, LocalTime(15, 30), .confirmed, channel: .walkIn))

        // --- Rest of the week across operators (admin weekly agenda) ---
        for dayOffset in 0...5 {
            let d = today.plusDays(dayOffset)
            if d.dayOfWeek == .sunday { continue }
            appointments.append(apt("cli_ciro", opAntonio, [svcTaglioBarba], d, LocalTime(10, 0), .confirmed))
            appointments.append(apt("cli_peppe", opGiulia, [svcTaglio], d, LocalTime(11, 0), .confirmed))
            if dayOffset % 2 == 0 {
                appointments.append(apt("cli_enzo", opAntonio, [svcTaglio], d, LocalTime(15, 0), .confirmed))
                appointments.append(apt("cli_davide", opSara, [svcRasatura], d, LocalTime(16, 0), .confirmed, channel: .phone))
            }
        }

        // --- Antonio fully booked a week from now: the wizard's "Avvisami" page ---
        var fullDay = today.plusDays(7)
        while fullDay.dayOfWeek == .sunday || fullDay.dayOfWeek == .monday { fullDay = fullDay.plusDays(1) }
        let fullDayClients = ["cli_davide", "cli_gennaro", "cli_salvatore", "cli_peppe", "cli_enzo", "cli_ciro"]
        for (i, hour) in [9, 10, 11, 12, 14, 15, 16, 17, 18].enumerated() {
            appointments.append(
                apt(fullDayClients[i % fullDayClients.count], opAntonio, [svcTaglioBarba], fullDay, LocalTime(hour, 0), .confirmed)
            )
        }

        // --- Past month volume for stats ---
        let pastPairs: [(String, String)] = [
            ("cli_davide", opLuca), ("cli_gennaro", opSara), ("cli_salvatore", opAntonio),
            ("cli_peppe", opGiulia), ("cli_enzo", opAntonio),
        ]
        for weekAgo in 1...4 {
            for (i, pair) in pastPairs.enumerated() {
                let d = openDay(today.minusWeeks(weekAgo).plusDays(i % 5))
                let svc = i % 2 == 0 ? [svcTaglio] : [svcTaglioBarba]
                appointments.append(apt(pair.0, pair.1, svc, d, LocalTime(9 + i, 0), .completed))
            }
        }
        // A couple of no-shows for the KPI
        appointments.append(apt("cli_paolo", opLuca, [svcTaglio], openDay(today.minusDays(10)), LocalTime(12, 0), .noShow))
        appointments.append(apt("cli_franco", opSara, [svcRasatura], openDay(today.minusDays(6)), LocalTime(17, 0), .noShow))

        store.appointments = appointments

        // Lunch break for Luca, a training block for Giulia, and a week of
        // holidays so the owner's operator sheet has something to show.
        let holidayStart = today.plusDays(20)
        let fullWorkDay = TimeRange(LocalTime(9, 0), LocalTime(19, 0))
        store.timeBlocks = [
            TimeBlock(
                id: "blk_lunch_luca", operatorId: opLuca, reason: .pausa, date: agendaDay,
                range: TimeRange(LocalTime(13, 0), LocalTime(14, 0)), label: "Pausa pranzo"
            ),
            TimeBlock(
                id: "blk_corso_giulia", operatorId: opGiulia, reason: .corso, date: openDay(today.plusDays(2)),
                range: TimeRange(LocalTime(14, 0), LocalTime(17, 0)), label: "Formazione"
            ),
        ] + (0..<7).map { offset in
            TimeBlock(
                id: "blk_ferie_luca_\(offset)", operatorId: opLuca, reason: .ferie,
                date: holidayStart.plusDays(offset), range: fullWorkDay
            )
        }

        // Marco in attesa sulla giornata piena di Antonio, a qualsiasi orario:
        // una lista d'attesa ha senso solo su un giorno davvero pieno.
        store.waitlist = [
            WaitlistEntry(
                id: "wl_marco", clientId: clientMarco, date: fullDay, time: nil,
                operatorId: opAntonio, serviceIds: [svcTaglio, svcRasatura],
                durationMinutes: 75, totalPriceCents: 2200, position: 1
            ),
        ]
    }
}
