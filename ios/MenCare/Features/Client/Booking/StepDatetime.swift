import SwiftUI

struct StepDatetime: View {
    @Bindable var viewModel: BookingViewModel

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    MonthCalendar(viewModel: viewModel)
                    if let date = viewModel.selectedDate {
                        HStack {
                            BrandSectionLabel(text: "\(formatDateShort(date)) · \(formatDuration(viewModel.totalDurationMinutes))")
                            Spacer()
                            if !viewModel.selectedDayFull {
                                legendDot(.stone, L("wizard_slot_free"))
                                legendDot(.oliveWood, L("wizard_slot_chosen"))
                                    .padding(.leading, 10)
                            }
                        }
                        .padding(.top, 18)
                        Group {
                            if viewModel.slotsLoading {
                                ProgressView()
                                    .tint(.oliveWood)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 80)
                            } else if viewModel.selectedDayFull {
                                fullyBookedPanel
                            } else if viewModel.slots.isEmpty {
                                Text(L("wizard_no_slots"))
                                    .font(Typo.bodyMedium)
                                    .foregroundStyle(Color.textMuted)
                            } else {
                                slotGrid
                            }
                        }
                        .padding(.top, 10)
                        if viewModel.takenSlot != nil && !viewModel.selectedDayFull {
                            waitlistPrompt
                                .padding(.top, 11)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
                .readableWidth()
            }
            // Sul giorno al completo "Avvisami" sta fisso in fondo, come la barra
            // degli altri step: non finisce mai sotto il bordo dello schermo.
            if viewModel.selectedDayFull {
                fullyBookedActions
            }
            BottomBarReveal(visible: viewModel.selectedSlot != nil) {
                DarkTotalBar(
                    caption: viewModel.selectedDate.map { date in
                        "\(formatDateShort(date)) · \(viewModel.selectedSlot.map(formatTime) ?? "")"
                    } ?? "",
                    value: "\(formatDuration(viewModel.totalDurationMinutes)) · \(formatPriceCompact(viewModel.totalPriceCents))",
                    ctaLabel: L("wizard_continue"),
                    action: viewModel.continueFromDatetime
                )
            }
        }
    }

    private func legendDot(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(label).font(Typo.jost(10)).foregroundStyle(Color.textMuted)
        }
    }

    private var slotGrid: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 9), count: 4)
        return LazyVGrid(columns: columns, spacing: 9) {
            ForEach(viewModel.slots, id: \.self) { slot in
                let isSelected = slot == viewModel.selectedSlot
                Button {
                    viewModel.selectSlot(slot)
                } label: {
                    Text(formatTime(slot))
                        .font(Typo.titleSmall)
                        .foregroundStyle(isSelected ? Color.bone : Color.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(isSelected ? Color.oliveWood : Color.stone)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// The chosen day is fully booked: instead of an empty grid, an
    /// illustration and "Avvisami", which queues the client for any time of
    /// that day.
    private var fullyBookedPanel: some View {
        VStack(spacing: 0) {
            FullyBookedIllustration()
                .frame(maxWidth: 210)
                .frame(maxWidth: .infinity)
            Text(L("wizard_full_title"))
                .font(Typo.cormorant(26, weight: .regular))
                .foregroundStyle(Color.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 12)
            Text(L("wizard_full_body"))
                .font(Typo.bodyMedium)
                .foregroundStyle(Color.textMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
    }

    /// "Avvisami", or the joined state once the client is in line.
    private var fullyBookedActions: some View {
        VStack(spacing: 8) {
            if let joined = viewModel.dayWaitlistEntry {
                Text(L("wizard_full_joined", joined.position))
                    .font(Typo.titleMedium)
                    .foregroundStyle(Color.oliveWood)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.oliveWood, lineWidth: 1.5))
                Text(L("wizard_full_joined_hint"))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)
            } else {
                AccentButton(
                    text: L("wizard_full_cta"),
                    action: viewModel.joinDayWaitlist,
                    loading: viewModel.joiningWaitlist,
                    height: 54,
                    corner: 16,
                    leadingSystemImage: "bell"
                )
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .readableWidth()
        .background(Color.bone)
    }

    /// Offered only when the chosen slot was taken while confirming.
    private var waitlistPrompt: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("wizard_waitlist_prompt", viewModel.takenSlot.map(formatTime) ?? ""))
                    .font(Typo.bodyMedium)
                    .foregroundStyle(Color.ink)
                Text(L("wizard_waitlist_push_hint"))
                    .font(Typo.jost(11))
                    .foregroundStyle(Color.textMuted)
            }
            Spacer()
            Button(action: viewModel.joinWaitlist) {
                Text(
                    viewModel.waitlistJoinedPosition.map { L("wizard_waitlist_joined", $0) }
                        ?? L("wizard_waitlist_cta")
                )
                .font(Typo.jost(12.5, weight: .medium))
                .foregroundStyle(Color.oliveWood)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.waitlistJoinedPosition != nil)
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
    }
}

private struct MonthCalendar: View {
    @Bindable var viewModel: BookingViewModel

    var body: some View {
        let month = viewModel.month
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(formatMonthYear(month.firstDay).capitalizedFirst)
                    .font(Typo.cormorant(26, weight: .regular))
                    .foregroundStyle(Color.ink)
                Spacer()
                roundArrow("chevron.left", enabled: month > .current()) {
                    viewModel.loadMonth(month.plusMonths(-1))
                }
                roundArrow("chevron.right", enabled: true, dark: true) {
                    viewModel.loadMonth(month.plusMonths(1))
                }
            }
            HStack {
                ForEach(Array(["L", "M", "M", "G", "V", "S", "D"].enumerated()), id: \.offset) { _, day in
                    Text(day)
                        .font(Typo.jost(10, weight: .medium))
                        .kerning(1)
                        .foregroundStyle(Color.textMuted)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, 6)
            let firstDay = month.firstDay
            let leadingEmpty = firstDay.dayOfWeek.rawValue - 1
            let cells: [LocalDate?] = Array(repeating: nil, count: leadingEmpty) +
                (1...firstDay.lengthOfMonth).map { LocalDate(year: month.year, month: month.month, day: $0) }
            let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)
            LazyVGrid(columns: columns, spacing: 0) {
                ForEach(Array(cells.enumerated()), id: \.offset) { _, day in
                    dayCell(day)
                }
            }
        }
    }

    private func roundArrow(_ systemImage: String, enabled: Bool, dark: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle()
                .fill(dark ? Color.ink : Color.stone)
                .frame(width: 34, height: 34)
                .overlay(
                    Image(systemName: systemImage)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(dark ? Color.bone : Color.ink.opacity(enabled ? 1 : 0.3))
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    @ViewBuilder
    private func dayCell(_ day: LocalDate?) -> some View {
        if let day {
            let available = viewModel.availableDays.contains(day)
            // Fully booked: still tappable (it offers the waitlist), outlined instead of filled.
            let full = viewModel.fullyBookedDays.contains(day)
            let selectable = available || full
            let isSelected = day == viewModel.selectedDate
            Button {
                viewModel.selectDate(day)
            } label: {
                // Il quadrato lo detta Color.clear, come nelle celle vuote: con
                // l'aspectRatio sul contenuto la cella si stringeva sul numero e
                // il pallino finiva fuori dal riquadro.
                Color.clear
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(isSelected ? Color.oliveWood : (available ? Color.stone : Color.clear))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(full && !isSelected ? Color.stoneBorder : Color.clear, lineWidth: 1)
                            )
                            .padding(3)
                    )
                    .overlay(
                        VStack(spacing: 3) {
                            Text("\(day.day)")
                                .font(Typo.titleSmall)
                                .foregroundStyle(
                                    isSelected ? Color.bone : (selectable ? Color.ink : Color.textMuted.opacity(0.5))
                                )
                            if available {
                                Circle()
                                    .fill(isSelected ? Color.bone : Color.oliveWood)
                                    .frame(width: 4, height: 4)
                            }
                        }
                    )
            }
            .buttonStyle(.plain)
            .disabled(!selectable)
        } else {
            Color.clear.aspectRatio(1, contentMode: .fit)
        }
    }
}

