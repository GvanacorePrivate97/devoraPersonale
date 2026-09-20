import SwiftUI

// System pickers dressed in brand colours. iOS uses the native wheel/graphical
// pickers where Android used the Material dialogs — same role in the flows.

/// Time picker sheet: confirm/cancel around a wheel picker, 24h.
struct BrandTimePickerDialog: View {
    let initial: LocalTime
    let onDismiss: () -> Void
    let onConfirm: (LocalTime) -> Void

    @State private var selection: Date

    init(initial: LocalTime, onDismiss: @escaping () -> Void, onConfirm: @escaping (LocalTime) -> Void) {
        self.initial = initial
        self.onDismiss = onDismiss
        self.onConfirm = onConfirm
        var components = DateComponents()
        components.hour = initial.hour
        components.minute = initial.minute
        _selection = State(initialValue: Calendar.current.date(from: components) ?? Date())
    }

    var body: some View {
        VStack(spacing: 12) {
            DatePicker("", selection: $selection, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .tint(Color.oliveWood)
            HStack {
                Button(String(localized: "ds_cancel"), action: onDismiss)
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.ink)
                Spacer()
                Button(String(localized: "ds_confirm")) {
                    let c = Calendar.current.dateComponents([.hour, .minute], from: selection)
                    onConfirm(LocalTime(c.hour ?? initial.hour, c.minute ?? initial.minute))
                }
                .font(Typo.titleSmall)
                .foregroundStyle(Color.oliveWood)
            }
            .padding(.horizontal, 20)
        }
        .padding(.vertical, 16)
        .presentationDetents([.height(300)])
        .presentationBackground(Color.bone)
    }
}

/// Date picker sheet: graphical calendar, brand-tinted.
struct BrandDatePickerDialog: View {
    let initial: LocalDate
    let onDismiss: () -> Void
    let onConfirm: (LocalDate) -> Void

    @State private var selection: Date

    init(initial: LocalDate, onDismiss: @escaping () -> Void, onConfirm: @escaping (LocalDate) -> Void) {
        self.initial = initial
        self.onDismiss = onDismiss
        self.onConfirm = onConfirm
        var components = DateComponents()
        components.year = initial.year
        components.month = initial.month
        components.day = initial.day
        _selection = State(initialValue: Calendar.current.date(from: components) ?? Date())
    }

    var body: some View {
        VStack(spacing: 12) {
            DatePicker("", selection: $selection, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(Color.oliveWood)
                .padding(.horizontal, 12)
            HStack {
                Button(String(localized: "ds_cancel"), action: onDismiss)
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.ink)
                Spacer()
                Button(String(localized: "ds_confirm")) {
                    let c = Calendar.current.dateComponents([.year, .month, .day], from: selection)
                    onConfirm(LocalDate(year: c.year ?? initial.year, month: c.month ?? initial.month, day: c.day ?? initial.day))
                }
                .font(Typo.titleSmall)
                .foregroundStyle(Color.oliveWood)
            }
            .padding(.horizontal, 20)
        }
        .padding(.vertical, 16)
        .presentationDetents([.height(460)])
        .presentationBackground(Color.bone)
    }
}
