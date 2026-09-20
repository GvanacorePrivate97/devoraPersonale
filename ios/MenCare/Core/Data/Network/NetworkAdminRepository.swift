import Foundation

@MainActor
final class NetworkAdminRepository: AdminRepository {

    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    func dashboard(_ period: DashboardPeriod) async throws -> DashboardStats {
        try await apiThrowing {
            // Incassi, occupazione, scontrino medio e clienti inattivi arrivano
            // calcolati: erano la cosa che le due app inventavano piu' liberamente.
            try await client.send(AdminEndpoint.dashboard(period), as: DashboardDto.self).toDomain()
        }
    }

    func notificationSettings() async throws -> NotificationSettings {
        try await apiThrowing {
            // Interruttori e promemoria stanno su due rotte: la schermata li
            // mostra come una cosa sola, quindi si uniscono qui.
            let switches = try await client.send(
                AdminEndpoint.notificationSettings, as: SalonNotificationSettingsDto.self
            )
            let rules = try await client.send(AdminEndpoint.reminderRules, as: ReminderRulesResponseDto.self)
            return NotificationSettings(
                reminders: rules.rules
                    .map { $0.toDomain() }
                    .sorted { $0.hoursBefore > $1.hoursBefore },
                bookingConfirmation: switches.bookingConfirmation,
                cancellationAlert: switches.cancellationAlert,
                lateOperatorAlert: switches.lateOperatorAlert,
                emptyDayPromos: switches.emptyDayPromos
            )
        }
    }

    func updateNotificationSettings(_ settings: NotificationSettings) async -> AppResult<NotificationSettings> {
        await apiResult {
            _ = try await client.send(
                AdminEndpoint.saveNotificationSettings(
                    SalonNotificationSettingsDto(
                        bookingConfirmation: settings.bookingConfirmation,
                        cancellationAlert: settings.cancellationAlert,
                        lateOperatorAlert: settings.lateOperatorAlert,
                        emptyDayPromos: settings.emptyDayPromos
                    )
                ),
                as: SalonNotificationSettingsDto.self
            )

            // I promemoria si creano e si cancellano uno a uno: si confronta la
            // lista in arrivo con quella sul server e si tocca solo la differenza.
            let existing = try await client.send(AdminEndpoint.reminderRules, as: ReminderRulesResponseDto.self).rules
            let wantedHours = settings.reminders.map(\.hoursBefore)
            var remaining = wantedHours

            for rule in existing {
                if let index = remaining.firstIndex(of: rule.hoursBefore) {
                    remaining.remove(at: index)
                } else {
                    _ = try await client.send(AdminEndpoint.removeReminderRule(rule.id))
                }
            }
            for hours in remaining {
                _ = try await client.send(
                    AdminEndpoint.addReminderRule(ReminderRuleWriteDto(hoursBefore: hours)), as: ReminderRuleDto.self
                )
            }
            return try await notificationSettings()
        }
    }

    func campaigns() async throws -> [PushCampaign] {
        try await apiThrowing {
            try await client.send(AdminEndpoint.campaigns, as: CampaignsResponseDto.self)
                .campaigns.map { $0.toDomain() }
        }
    }

    func saveCampaign(_ campaign: PushCampaign) async -> AppResult<PushCampaign> {
        await apiResult {
            // "Invia ora" non e' uno stato che si scrive: si salva la campagna e
            // poi si chiede l'invio, che il server mette in coda (202).
            let sendNow = campaign.status == .sent
            let body = CampaignWriteDto(
                name: campaign.name,
                segment: WireEnum.campaignSegmentWire(campaign.segment),
                title: campaign.title,
                body: campaign.body,
                scheduledAt: campaign.scheduledAt?.isoLocal,
                repeatWeekly: campaign.repeatWeekly,
                sendCap: campaign.sendCap,
                status: sendNow ? "DRAFT" : (campaign.status == .scheduled ? "SCHEDULED" : "DRAFT")
            )
            let saved = try await client.send(
                campaign.id.isEmpty
                    ? AdminEndpoint.createCampaign(body)
                    : AdminEndpoint.updateCampaign(campaign.id, body),
                as: CampaignDto.self
            )
            guard sendNow else { return saved.toDomain() }
            // La risposta dell'invio non interessa alla schermata: conta che sia
            // stata presa in carico.
            _ = try await client.send(AdminEndpoint.sendCampaign(saved.id))
            var sent = saved.toDomain()
            sent.status = .sent
            return sent
        }
    }

    func reachFor(_ segment: CampaignSegment) async throws -> (reachable: Int, size: Int) {
        try await apiThrowing {
            let dto = try await client.send(AdminEndpoint.reach(segment), as: CampaignReachDto.self)
            return (dto.reachable, dto.segmentSize)
        }
    }
}
