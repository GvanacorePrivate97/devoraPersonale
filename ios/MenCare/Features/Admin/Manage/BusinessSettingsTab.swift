import SwiftUI
import Observation

private let defaultOpening = TimeRange(LocalTime(9, 0), LocalTime(19, 0))

@MainActor
@Observable
final class BusinessSettingsViewModel {

    private let catalog: CatalogRepository

    /// One opening range per day; a missing day is a closing day.
    var hours: [DayOfWeek: TimeRange] = [:]
    var dirty = false
    var saved = false
    var saving = false
    /// L'esito del salvataggio non si butta più via: prima un fallimento
    /// lasciava la schermata identica a un successo.
    var saveError: String?

    let state = LoadState()
    /// Il salone completo serve al salvataggio: `PUT /catalog/salon` riscrive
    /// nome, indirizzo e orari tutti insieme.
    private var salon = Salon(name: "", address: "", city: "")

    init(catalog: CatalogRepository) {
        self.catalog = catalog
    }

    func load() async {
        await state.run {
            let loaded = try await catalog.catalog().salon
            salon = loaded
            hours = Dictionary(uniqueKeysWithValues: loaded.weeklyHours.compactMap { day, ranges in
                ranges.first.map { (day, $0) }
            })
            dirty = false
        }
    }

    func setOpen(_ day: DayOfWeek, _ open: Bool) {
        saveError = nil
        if open {
            hours[day] = defaultOpening
        } else {
            hours[day] = nil
        }
        dirty = true
        saved = false
    }

    func setFrom(_ day: DayOfWeek, _ from: LocalTime) {
        saveError = nil
        let current = hours[day] ?? defaultOpening
        let to = from < current.end ? current.end : from.plusMinutes(60)
        hours[day] = TimeRange(from, to)
        dirty = true
        saved = false
    }

    func setTo(_ day: DayOfWeek, _ to: LocalTime) {
        saveError = nil
        let current = hours[day] ?? defaultOpening
        let from = current.start < to ? current.start : to.minusMinutes(60)
        hours[day] = TimeRange(from, to)
        dirty = true
        saved = false
    }

    func save() {
        guard !saving else { return }
        // Un giorno aperto con apertura >= chiusura non è salvabile: i setter lo
        // impediscono già, questo è il controllo di sicurezza prima di scrivere.
        if hours.contains(where: { $0.value.start >= $0.value.end }) {
            saveError = L("validation_form_invalid")
            return
        }
        var updated = salon
        updated.weeklyHours = hours.mapValues { [$0] }
        saveError = nil
        saving = true
        Task {
            let result = await catalog.updateSalon(updated)
            saving = false
            switch result {
            case .success:
                salon = updated
                dirty = false
                saved = true
            case .failure(let error):
                saved = false
                // Il server rifiuta gli orari che lascerebbero fuori appuntamenti
                // gia' presi: quel messaggio va letto, non nascosto.
                saveError = error.displayMessage
            }
        }
    }
}

private struct EditingDay: Identifiable {
    let day: DayOfWeek
    let isStart: Bool
    var id: String { "\(day.rawValue)-\(isStart)" }
}

/// "Orari" tab: the salon's own opening hours, which bound every
/// operator's agenda.
struct BusinessSettingsTab: View {
    @State var viewModel: BusinessSettingsViewModel
    @State private var editing: EditingDay?

    var body: some View {
        Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
            content(viewModel)
        }
        .task { await viewModel.load() }
    }

    private func content(_ viewModel: BusinessSettingsViewModel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                BrandSectionLabel(text: L("business_hours"))
                Text(L("business_hours_hint"))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
                ForEach(DayOfWeek.allCases, id: \.self) { day in
                    dayRow(viewModel, day)
                }
                FormErrorBanner(message: viewModel.saveError)
                // In tab there is no nav bar to hold the save action.
                if viewModel.dirty {
                    AccentButton(
                        text: L("business_save_hours"), action: viewModel.save,
                        loading: viewModel.saving, height: 54, corner: 16
                    )
                    .padding(.top, 6)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 24)
            .readableWidth()
        }
        .background(Color.bone)
        .sheet(item: $editing) { edit in
            let range = viewModel.hours[edit.day]
            BrandTimePickerDialog(
                initial: (edit.isStart ? range?.start : range?.end) ?? LocalTime(9, 0),
                onDismiss: { editing = nil },
                onConfirm: { time in
                    if edit.isStart {
                        viewModel.setFrom(edit.day, time)
                    } else {
                        viewModel.setTo(edit.day, time)
                    }
                    editing = nil
                }
            )
        }
    }

    private func dayRow(_ viewModel: BusinessSettingsViewModel, _ day: DayOfWeek) -> some View {
        let range = viewModel.hours[day]
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(formatDayGroup([day]))
                    .font(Typo.jost(15, weight: .medium))
                    .foregroundStyle(Color.ink)
                Spacer()
                Text(L(range == nil ? "business_closed" : "business_open"))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
                    .padding(.trailing, 10)
                BrandSwitch(isOn: Binding(
                    get: { viewModel.hours[day] != nil },
                    set: { viewModel.setOpen(day, $0) }
                ))
            }
            if let range {
                HStack(spacing: 9) {
                    PickerTile(label: L("business_from"), value: formatTime(range.start)) {
                        editing = EditingDay(day: day, isStart: true)
                    }
                    PickerTile(label: L("business_to"), value: formatTime(range.end)) {
                        editing = EditingDay(day: day, isStart: false)
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
    }
}
