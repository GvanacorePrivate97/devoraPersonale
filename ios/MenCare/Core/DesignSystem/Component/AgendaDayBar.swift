import SwiftUI

/// Navigazione del giorno in agenda — una sola, per il titolare e per
/// l'operatore.
///
/// Le due agende navigavano il tempo in due modi diversi: il titolare con due
/// frecce nell'intestazione, l'operatore con una striscia di giorni, e da
/// nessuna delle due si tornava a oggi se non contando i tap all'indietro.
/// Questa barra è il pattern unico: frecce, data, striscia, e **"Oggi"**, che
/// compare solo quando serve — cioè solo quando il giorno scelto non è oggi.
///
/// Vive sulla banda scura, quindi l'accento è `.oliveLight`, l'oro del marchio.
/// Gemella di `AgendaDayBar.kt` su Android.
struct AgendaDayBar: View {
    let selected: LocalDate
    let onSelect: (LocalDate) -> Void
    var today: LocalDate = .today()
    var daysVisible: Int = 6

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                DayArrow(systemName: "chevron.left", label: L("ds_agenda_previous_day")) {
                    onSelect(selected.minusDays(1))
                }
                Text(formatDateLong(selected).capitalizedFirst)
                    .font(Typo.headlineSmall)
                    .foregroundStyle(Color.bone)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                DayArrow(systemName: "chevron.right", label: L("ds_agenda_next_day")) {
                    onSelect(selected.plusDays(1))
                }
                // "Oggi" non occupa spazio quando siamo già su oggi.
                if selected != today {
                    TodayButton { onSelect(today) }
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                }
            }
            DayStrip(
                selected: selected,
                today: today,
                daysVisible: daysVisible,
                onSelect: onSelect
            )
        }
        .animation(.easeInOut(duration: 0.18), value: selected == today)
    }
}

/// Il bottone "Oggi": pill d'accento, alta 34 pt.
private struct TodayButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(L("ds_agenda_today").uppercased())
                .font(Typo.overline)
                .sectionTracking()
                .foregroundStyle(Color.bone)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(RoundedRectangle(cornerRadius: 11).fill(Color.oliveWood))
        }
        .buttonStyle(.plain)
    }
}

private struct DayArrow: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.bone)
                .frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 11).fill(Color.bone.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// La striscia dei giorni. Oggi porta sempre il punto d'oro sotto il numero,
/// anche quando è il giorno selezionato: così "dove sono" e "dov'è oggi"
/// restano due informazioni distinte invece di annullarsi a vicenda.
private struct DayStrip: View {
    let selected: LocalDate
    let today: LocalDate
    let daysVisible: Int
    let onSelect: (LocalDate) -> Void

    var body: some View {
        // La finestra tiene il giorno scelto al centro, mai sul bordo.
        let start = selected.minusDays(daysVisible / 2)
        HStack(spacing: 6) {
            ForEach(Array(0..<daysVisible), id: \.self) { offset in
                let day = start.plusDays(offset)
                let isSelected = day == selected
                let isToday = day == today
                Button {
                    onSelect(day)
                } label: {
                    VStack(spacing: 2) {
                        Text(shortDayName(day))
                            .font(Typo.overline)
                            .sectionTracking()
                            .foregroundStyle(isSelected ? Color.bone : Color.oliveLight)
                            .lineLimit(1)
                        Text("\(day.day)")
                            .font(Typo.meta)
                            .foregroundStyle(Color.bone)
                            .lineLimit(1)
                        Circle()
                            .fill(isToday ? Color.oliveLight : Color.clear)
                            .frame(width: 4, height: 4)
                            .padding(.top, 1)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 13)
                            .fill(isSelected ? Color.oliveWood : Color.clear)
                    )
                    .overlay {
                        if isToday && !isSelected {
                            RoundedRectangle(cornerRadius: 13)
                                .strokeBorder(Color.oliveLight, lineWidth: 1)
                        }
                    }
                }
                .buttonStyle(.plain)
                // Una casella, una lettura: "lun 20, oggi".
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(
                    "\(shortDayName(day)) \(day.day)" + (isToday ? ", oggi" : "")
                )
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }

    private func shortDayName(_ day: LocalDate) -> String {
        String(formatDayNameShort(day).uppercased().prefix(3))
    }
}
