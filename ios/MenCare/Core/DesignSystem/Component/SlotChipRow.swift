import SwiftUI

private let chipCorner: CGFloat = 13
private let chipHeight: CGFloat = 46
private let chipMinWidth: CGFloat = 84

/// Gli orari liberi di una giornata, tutti, scorrendo in orizzontale: con la
/// riga a larghezza fissa se ne vedevano solo i primi.
///
/// `label` formatta l'ora — come ogni componente qui, la formattazione resta a
/// chi chiama — e `emptyLabel` è la riga che prende il posto delle chip quando
/// non c'è niente di prenotabile.
struct SlotChipRow: View {
    let slots: [LocalTime]
    let selected: LocalTime?
    let onSelect: (LocalTime) -> Void
    let label: (LocalTime) -> String
    let emptyLabel: String

    var body: some View {
        if slots.isEmpty {
            Text(emptyLabel)
                .font(Typo.bodySmall)
                .foregroundStyle(Color.textMuted)
                .frame(maxWidth: .infinity)
                .frame(height: chipHeight)
                .background(RoundedRectangle(cornerRadius: chipCorner).fill(Color.stone))
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(slots, id: \.self) { slot in
                        let isSelected = slot == selected
                        Button {
                            onSelect(slot)
                        } label: {
                            Text(label(slot))
                                .font(Typo.titleSmall)
                                .foregroundStyle(isSelected ? Color.bone : Color.ink)
                                .padding(.horizontal, 14)
                                .frame(minWidth: chipMinWidth)
                                .frame(height: chipHeight)
                                .background(
                                    RoundedRectangle(cornerRadius: chipCorner)
                                        .fill(isSelected ? Color.ink : Color.stone)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}
