import SwiftUI

/// One choice of a `MultiSelectDropdown`.
struct DropdownOption: Identifiable {
    let id: String
    let label: String
}

/// Multi-select twin of the operator picker: a stone field with a chevron that
/// opens a menu with a checkmark per selected row.
struct MultiSelectDropdown: View {
    let options: [DropdownOption]
    let selectedIds: [String]
    let onToggle: (String) -> Void
    let placeholder: String

    var body: some View {
        Menu {
            ForEach(options) { option in
                Button {
                    onToggle(option.id)
                } label: {
                    if selectedIds.contains(option.id) {
                        Label(option.label, systemImage: "checkmark")
                    } else {
                        Text(option.label)
                    }
                }
            }
        } label: {
            HStack {
                Text(summary.isEmpty ? placeholder : summary)
                    .font(Typo.bodyLarge)
                    .foregroundStyle(summary.isEmpty ? Color.textMuted : Color.ink)
                    .lineLimit(1)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textMuted)
            }
            .padding(.horizontal, 14)
            .frame(height: 56)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var summary: String {
        options.filter { selectedIds.contains($0.id) }.map(\.label).joined(separator: ", ")
    }
}
