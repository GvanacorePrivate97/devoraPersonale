import Foundation

// Un namespace per area dell'API: i percorsi stanno qui e da nessun'altra
// parte, cosi' un cambio di contratto si vede in un file solo. Ogni percorso e'
// gia' relativo a `/v1`, che sta nella base URL.

enum AuthEndpoint {
    static func register(_ body: RegisterRequestDto) -> ApiRequest {
        .post("auth/register", body: body, authenticated: false)
    }

    static func login(_ body: LoginRequestDto) -> ApiRequest {
        .post("auth/login", body: body, authenticated: false)
    }

    static func social(_ body: SocialLoginRequestDto) -> ApiRequest {
        .post("auth/social", body: body, authenticated: false)
    }

    static func logout(_ body: RefreshRequestDto) -> ApiRequest {
        .post("auth/logout", body: body, authenticated: false)
    }

    static func passwordResetRequest(_ body: PasswordResetRequestDto) -> ApiRequest {
        .post("auth/password/reset-request", body: body, authenticated: false)
    }

    static var me: ApiRequest { .get("auth/me") }

    static func updateMe(_ body: ProfileRequestDto) -> ApiRequest {
        .patch("auth/me", body: body)
    }

    static func changePassword(_ body: ChangePasswordRequestDto) -> ApiRequest {
        .post("auth/me/password", body: body)
    }

    static var notificationPrefs: ApiRequest { .get("auth/me/notification-prefs") }

    static func saveNotificationPrefs(_ body: NotificationPrefsDto) -> ApiRequest {
        .put("auth/me/notification-prefs", body: body)
    }

    static let avatarPath = "auth/me/avatar"
    static var deleteAvatar: ApiRequest { .delete(avatarPath) }
}

enum CatalogEndpoint {
    static var catalog: ApiRequest { .get("catalog") }
    static var services: ApiRequest { .get("catalog/services") }
    static var operators: ApiRequest { .get("catalog/operators") }

    static func createService(_ body: ServiceWriteDto) -> ApiRequest {
        .post("catalog/services", body: body)
    }

    static func updateService(_ id: String, _ body: ServiceWriteDto) -> ApiRequest {
        .patch("catalog/services/\(id)", body: body)
    }

    static func setServiceOperators(_ id: String, _ body: ServiceOperatorsDto) -> ApiRequest {
        .patch("catalog/services/\(id)/operators", body: body)
    }

    static func createOperator(_ body: OperatorWriteDto) -> ApiRequest {
        .post("catalog/operators", body: body)
    }

    static func setOperatorHours(_ id: String, _ body: OperatorHoursDto) -> ApiRequest {
        .put("catalog/operators/\(id)/hours", body: body)
    }

    static func setOperatorServices(_ id: String, _ body: OperatorServicesDto) -> ApiRequest {
        .put("catalog/operators/\(id)/services", body: body)
    }

    static func updateSalon(_ body: SalonWriteDto) -> ApiRequest {
        .put("catalog/salon", body: body)
    }

    static func holidays(operatorId: String) -> ApiRequest {
        .get("catalog/operators/\(operatorId)/holidays")
    }

    static func addHoliday(operatorId: String, _ body: HolidayWriteDto) -> ApiRequest {
        .post("catalog/operators/\(operatorId)/holidays", body: body)
    }

    static func deleteHoliday(_ id: String) -> ApiRequest {
        .delete("catalog/holidays/\(id)")
    }
}

enum BookingEndpoint {
    static func availability(
        operatorId: String?, serviceIds: [String], date: LocalDate, ignoreAppointmentId: String? = nil
    ) -> ApiRequest {
        .get(
            "booking/availability",
            query: [
                "operatorId": operatorId,
                "serviceIds": serviceIds.joined(separator: ","),
                "date": date.iso,
                "ignoreAppointmentId": ignoreAppointmentId,
            ]
        )
    }

    static func days(
        operatorId: String?, serviceIds: [String], from: LocalDate, to: LocalDate, ignoreAppointmentId: String? = nil
    ) -> ApiRequest {
        .get(
            "booking/days",
            query: [
                "operatorId": operatorId,
                "serviceIds": serviceIds.joined(separator: ","),
                "from": from.iso,
                "to": to.iso,
                "ignoreAppointmentId": ignoreAppointmentId,
            ]
        )
    }

    /// Non usata: la prima disponibilita' di ogni operatore le due app la
    /// compongono da `/booking/days` + `/booking/availability`, perche' un
    /// metodo in piu' sul repository farebbe divergere il contratto fra Android
    /// e iOS. La rotta resta mappata qui per quando si decidera' di adottarla
    /// su entrambe insieme.
    static func nextAvailability(serviceIds: [String]) -> ApiRequest {
        .get("booking/next-availability", query: ["serviceIds": serviceIds.joined(separator: ",")])
    }

    static var myAppointments: ApiRequest { .get("appointments/me") }

    static func appointments(operatorId: String?, date: LocalDate) -> ApiRequest {
        .get("appointments", query: ["operatorId": operatorId, "date": date.iso])
    }

    static func week(_ weekStart: LocalDate) -> ApiRequest {
        .get("appointments/week", query: ["weekStart": weekStart.iso])
    }

    static func appointment(_ id: String) -> ApiRequest { .get("appointments/\(id)") }

    static func book(_ body: BookRequestDto) -> ApiRequest { .post("appointments", body: body) }

    static func reschedule(_ id: String, _ body: RescheduleRequestDto) -> ApiRequest {
        .patch("appointments/\(id)/schedule", body: body)
    }

    static func cancel(_ id: String) -> ApiRequest { .post("appointments/\(id)/cancel") }

    static func setStatus(_ id: String, _ body: StatusRequestDto) -> ApiRequest {
        .post("appointments/\(id)/status", body: body)
    }

    static var myWaitlist: ApiRequest { .get("waitlist/me") }

    static func joinWaitlist(_ body: JoinWaitlistRequestDto) -> ApiRequest {
        .post("waitlist", body: body)
    }

    static func leaveWaitlist(_ id: String) -> ApiRequest { .delete("waitlist/\(id)") }
}

enum BlocksEndpoint {
    static func day(operatorId: String?, date: LocalDate) -> ApiRequest {
        .get("blocks", query: ["operatorId": operatorId, "date": date.iso])
    }

    static func week(operatorId: String?, weekStart: LocalDate) -> ApiRequest {
        .get("blocks/week", query: ["operatorId": operatorId, "weekStart": weekStart.iso])
    }

    static func conflicts(operatorId: String?, date: LocalDate, start: LocalTime, end: LocalTime) -> ApiRequest {
        .get(
            "blocks/conflicts",
            query: ["operatorId": operatorId, "date": date.iso, "start": start.iso, "end": end.iso]
        )
    }

    static func create(_ body: BlockWriteDto) -> ApiRequest { .post("blocks", body: body) }

    static func delete(_ id: String) -> ApiRequest { .delete("blocks/\(id)") }
}

enum CrmEndpoint {
    static func clients(query: String?, segment: ClientSegment, limit: Int?, cursor: String?) -> ApiRequest {
        .get(
            "crm/clients",
            query: [
                "query": (query?.isEmpty ?? true) ? nil : query,
                "segment": WireEnum.clientSegmentWire(segment),
                "limit": limit.map(String.init),
                "cursor": cursor,
            ]
        )
    }

    static func client(_ id: String) -> ApiRequest { .get("crm/clients/\(id)") }

    static func createClient(_ body: CreateClientRequestDto) -> ApiRequest {
        .post("crm/clients", body: body)
    }
}

enum AdminEndpoint {
    static func dashboard(_ period: DashboardPeriod) -> ApiRequest {
        .get("admin/dashboard", query: ["period": WireEnum.dashboardPeriodWire(period)])
    }

    static var notificationSettings: ApiRequest { .get("admin/notification-settings") }

    static func saveNotificationSettings(_ body: SalonNotificationSettingsDto) -> ApiRequest {
        .put("admin/notification-settings", body: body)
    }

    static var reminderRules: ApiRequest { .get("admin/reminder-rules") }

    static func addReminderRule(_ body: ReminderRuleWriteDto) -> ApiRequest {
        .post("admin/reminder-rules", body: body)
    }

    static func removeReminderRule(_ id: String) -> ApiRequest {
        .delete("admin/reminder-rules/\(id)")
    }

    static var campaigns: ApiRequest { .get("admin/campaigns") }

    static func reach(_ segment: CampaignSegment) -> ApiRequest {
        .get("admin/campaigns/reach", query: ["segment": WireEnum.campaignSegmentWire(segment)])
    }

    static func createCampaign(_ body: CampaignWriteDto) -> ApiRequest {
        .post("admin/campaigns", body: body)
    }

    static func updateCampaign(_ id: String, _ body: CampaignWriteDto) -> ApiRequest {
        .patch("admin/campaigns/\(id)", body: body)
    }

    static func sendCampaign(_ id: String) -> ApiRequest { .post("admin/campaigns/\(id)/send") }
}

enum NotificationsEndpoint {
    static func list(limit: Int?, cursor: String?) -> ApiRequest {
        .get("notifications", query: ["limit": limit.map(String.init), "cursor": cursor])
    }

    static var readAll: ApiRequest { .post("notifications/read-all") }

    static func read(_ id: String) -> ApiRequest { .post("notifications/\(id)/read") }
}
