import SwiftUI
import Observation

/// Hours-before choices of the reminder at `index`: days for the first, hours for the others.
private func reminderOptions(_ index: Int) -> [Int] {
    index == 0 ? [24, 48] : [2, 4]
}

private func reminderLabel(_ hours: Int) -> String {
    if hours == 24 { return L("ntf_one_day_before") }
    return hours > 24 ? L("ntf_days_before", hours / 24) : L("ntf_hours_before", hours)
}

/// Anche questa scheda leggeva il repository dentro `body`. Adesso ha il suo
/// view model: le impostazioni arrivano dall'API (interruttori e promemoria,
/// due rotte diverse unite in un oggetto solo) e ogni modifica torna indietro.
@MainActor
@Observable
final class NotificationSettingsViewModel {

    private let admin: AdminRepository

    let state = LoadState()
    private(set) var settings = NotificationSettings(reminders: [])
    private(set) var saving = false
    var saveError: String?

    init(admin: AdminRepository) {
        self.admin = admin
    }

    func load() async {
        await state.run { settings = try await admin.notificationSettings() }
    }

    /// Ogni interruttore scrive subito: e' una schermata senza tasto "Salva",
    /// quindi l'esito va mostrato li' per li'.
    func update(_ transform: (inout NotificationSettings) -> Void) {
        var updated = settings
        transform(&updated)
        // Aggiornamento ottimista: l'interruttore si muove subito, e se il
        // server rifiuta si rimette com'era.
        let previous = settings
        settings = updated
        saving = true
        saveError = nil
        Task {
            switch await admin.updateNotificationSettings(updated) {
            case .success(let stored): settings = stored
            case .failure(let error):
                settings = previous
                saveError = error.displayMessage
            }
            saving = false
        }
    }
}

struct NotificationSettingsTab: View {
    @State var viewModel: NotificationSettingsViewModel

    var body: some View {
        Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
            content(viewModel.settings)
        }
        .task { await viewModel.load() }
    }

    private func content(_ settings: NotificationSettings) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                FormErrorBanner(message: viewModel.saveError)
                BrandSectionLabel(text: L("ntf_reminders"))
                remindersPanel(settings)

                BrandSectionLabel(text: L("ntf_types"))
                    .padding(.top, 4)
                typesPanel(settings)

                previewPanel
                    .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 24)
            .readableWidth()
        }
    }

    private func update(_ transform: (inout NotificationSettings) -> Void) {
        viewModel.update(transform)
    }

    private func remindersPanel(_ settings: NotificationSettings) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(settings.reminders.enumerated()), id: \.element.id) { index, rule in
                if index > 0 {
                    Rectangle().fill(Color.bone.opacity(0.1)).frame(height: 1)
                }
                VStack(spacing: 10) {
                    HStack {
                        Text(L("ntf_nth_reminder", index + 1))
                            .font(Typo.bodyLarge)
                            .foregroundStyle(Color.bone)
                        Spacer()
                        Button {
                            update { settings in
                                settings.reminders.removeAll { $0.id == rule.id }
                            }
                        } label: {
                            Image(systemName: "trash")
                                .font(.system(size: 15))
                                .foregroundStyle(Color.oliveWood)
                                .frame(width: 36, height: 36)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L("ntf_remove_reminder"))
                    }
                    let options = reminderOptions(index)
                    SegmentedTabs(
                        options: options.map(reminderLabel),
                        selectedIndex: options.firstIndex(of: rule.hoursBefore) ?? -1,
                        onSelect: { selectedIndex in
                            update { settings in
                                settings.reminders = settings.reminders.map {
                                    var updated = $0
                                    if $0.id == rule.id { updated.hoursBefore = options[selectedIndex] }
                                    return updated
                                }
                            }
                        }
                    )
                }
                .padding(.vertical, 12)
            }
            Button {
                update { settings in
                    // Parte da un'opzione che nessun altro promemoria usa ancora, se c'è.
                    let options = reminderOptions(settings.reminders.count)
                    let hours = options.first { h in !settings.reminders.contains { $0.hoursBefore == h } } ?? options[0]
                    settings.reminders.append(
                        ReminderRule(id: "rem_\(settings.reminders.count + 1)_\(Int(Date().timeIntervalSince1970 * 1000))", hoursBefore: hours)
                    )
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "plus").font(.system(size: 13))
                    Text(L("ntf_add_reminder")).font(Typo.titleSmall)
                }
                .foregroundStyle(Color.bone)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.bone.opacity(0.25), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .padding(.vertical, 10)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 18).fill(Color.ink))
    }

    private func typesPanel(_ settings: NotificationSettings) -> some View {
        VStack(spacing: 0) {
            toggleRow(L("ntf_confirmation"), hint: L("ntf_confirmation_hint"), value: settings.bookingConfirmation) { on in
                update { $0.bookingConfirmation = on }
            }
            Rectangle().fill(Color.stoneBorder).frame(height: 1)
            toggleRow(L("ntf_cancellation"), hint: nil, value: settings.cancellationAlert) { on in
                update { $0.cancellationAlert = on }
            }
            Rectangle().fill(Color.stoneBorder).frame(height: 1)
            toggleRow(L("ntf_late"), hint: L("ntf_late_hint"), value: settings.lateOperatorAlert) { on in
                update { $0.lateOperatorAlert = on }
            }
            Rectangle().fill(Color.stoneBorder).frame(height: 1)
            toggleRow(L("ntf_promo_empty"), hint: L("ntf_promo_empty_hint"), value: settings.emptyDayPromos) { on in
                update { $0.emptyDayPromos = on }
            }
        }
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func toggleRow(_ title: String, hint: String?, value: Bool, onChange: @escaping (Bool) -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(Typo.bodyLarge).foregroundStyle(Color.ink)
                if let hint {
                    Text(hint).font(Typo.jost(12)).foregroundStyle(Color.textMuted)
                }
            }
            Spacer()
            BrandSwitch(isOn: Binding(get: { value }, set: onChange))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var previewPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("ntf_preview").uppercased())
                .font(Typo.jost(11, weight: .medium))
                .kerning(1.3)
                .foregroundStyle(Color.oliveWood)
            HStack(spacing: 11) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.ink)
                    .frame(width: 34, height: 34)
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("ntf_preview_sender"))
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.ink)
                    Text(L("ntf_preview_body"))
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.ink)
                }
                Spacer()
                Text(L("ntf_preview_now"))
                    .font(Typo.jost(11))
                    .foregroundStyle(Color.textMuted)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.oliveWood, lineWidth: 1.5))
    }
}
