import SwiftUI

/// Navigation row for the dark band: back chevron, centred title, optional
/// trailing action — the mockup's own top bar.
struct BrandTopBar<Trailing: View>: View {
    let title: String
    var onBack: (() -> Void)?
    var backLabel: String?
    @ViewBuilder var trailing: Trailing

    init(
        title: String,
        onBack: (() -> Void)? = nil,
        backLabel: String? = nil,
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) {
        self.title = title
        self.onBack = onBack
        self.backLabel = backLabel
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: 0) {
            HStack(spacing: 4) {
                if let onBack {
                    Button(action: onBack) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .medium))
                            if let backLabel {
                                Text(backLabel)
                                    .font(Typo.jost(13, weight: .medium))
                                    .lineLimit(1)
                            }
                        }
                        .foregroundStyle(Color.oliveWood)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(width: 88, alignment: .leading)
            Text(title)
                .font(Typo.jost(15, weight: .medium))
                .foregroundStyle(Color.bone)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
            HStack { trailing }
                .frame(width: 88, alignment: .trailing)
        }
        .frame(height: 48)
    }
}

/// Text action sitting in a dark band (the mockup's "Annulla" / "Salva" / "Modifica").
struct BarAction: View {
    let text: String
    let action: () -> Void
    var color: Color = .bone

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Typo.jost(13, weight: .medium))
                .foregroundStyle(color)
                .lineLimit(1)
                .padding(.vertical, 8)
                .padding(.horizontal, 2)
        }
        .buttonStyle(.plain)
    }
}

/// Four-step progress rail of the booking wizard.
struct WizardSteps: View {
    let labels: [String]
    let currentIndex: Int

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                let done = index <= currentIndex
                VStack(alignment: .leading, spacing: 6) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(done ? Color.oliveWood : Color.bone.opacity(0.16))
                        .frame(height: 3)
                    Text(label.uppercased())
                        .font(Typo.jost(9, weight: .medium))
                        .kerning(0.9)
                        .foregroundStyle(done ? Color.oliveWood : Color.bone)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

/// Pill segmented control — "Prossimi · 2 / Passati · 14" and friends.
struct SegmentedTabs: View {
    let options: [String]
    let selectedIndex: Int
    let onSelect: (Int) -> Void
    var onDark: Bool = true
    var selectedContainer: Color = .oliveWood

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                let selected = index == selectedIndex
                Button {
                    onSelect(index)
                } label: {
                    Text(option)
                        .font(Typo.titleSmall)
                        .foregroundStyle(selected || onDark ? Color.bone : Color.ink)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(
                            RoundedRectangle(cornerRadius: 11)
                                .fill(selected ? selectedContainer : Color.clear)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(onDark ? Color.bone.opacity(0.1) : Color.stone)
        )
    }
}

/// Standalone selectable chip used for filters, categories and quick choices.
///
/// `fill` makes it take the whole width it is offered, so a row of chips comes
/// out in equal sizes; the label then shrinks (and, with `maxLines: 2`, wraps)
/// instead of being truncated.
struct BrandChip: View {
    let text: String
    let selected: Bool
    let action: () -> Void
    var onDark: Bool = false
    var fill: Bool = false
    var maxLines: Int = 1

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Typo.titleSmall)
                .foregroundStyle(selected || onDark ? Color.bone : Color.ink)
                .lineLimit(maxLines)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(fill ? 0.7 : 1)
                .padding(.horizontal, fill ? 6 : 16)
                .frame(maxWidth: fill ? .infinity : nil, minHeight: fill ? (maxLines > 1 ? 58 : 42) : nil)
                .padding(.vertical, fill ? 0 : 11)
                .contentShape(Rectangle())
                .background(
                    RoundedRectangle(cornerRadius: 11)
                        .fill(selected ? Color.oliveWood : (onDark ? Color.bone.opacity(0.1) : Color.stone))
                )
        }
        .buttonStyle(.plain)
    }
}

/// Rounded Stone container that most list rows and panels sit in.
struct StoneCard<Content: View>: View {
    var onTap: (() -> Void)?
    var corner: CGFloat = 18
    var container: Color = .stone
    @ViewBuilder let content: Content

    init(
        onTap: (() -> Void)? = nil,
        corner: CGFloat = 18,
        container: Color = .stone,
        @ViewBuilder content: () -> Content
    ) {
        self.onTap = onTap
        self.corner = corner
        self.container = container
        self.content = content()
    }

    var body: some View {
        let card = VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: corner).fill(container))
        if let onTap {
            Button(action: onTap) { card }.buttonStyle(.plain)
        } else {
            card
        }
    }
}

/// Outlined variant used for highlighted panels (staff notes, conflict warnings).
struct AccentOutlinedCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.oliveWood, lineWidth: 1.5))
    }
}

/// Uppercase section heading used above every block of content.
struct BrandSectionLabel: View {
    let text: String
    var color: Color = .ink

    var body: some View {
        Text(text.uppercased())
            .font(Typo.jost(11, weight: .medium))
            .kerning(1.8)
            .foregroundStyle(color)
    }
}

/// KPI tile: big value under a small uppercase caption, both centred. The
/// caption wraps rather than truncating; put tiles side by side in an HStack
/// with `.fixedSize(horizontal: false, vertical: true)` so they share a height.
struct StatTile: View {
    let label: String
    let value: String
    var container: Color = .stone
    var contentColor: Color = .ink
    var borderColor: Color?

    var body: some View {
        VStack(spacing: 4) {
            Text(label.uppercased())
                .font(Typo.jost(9, weight: .medium))
                .kerning(1.3)
                .foregroundStyle(contentColor.opacity(0.7))
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            Text(value)
                .font(Typo.headlineMedium)
                .foregroundStyle(contentColor)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 10)
        .padding(.vertical, 13)
        .background(RoundedRectangle(cornerRadius: 16).fill(container))
        .overlay(
            borderColor.map { RoundedRectangle(cornerRadius: 16).strokeBorder($0, lineWidth: 1) }
        )
    }
}

/// Olive Wood toggle.
struct BrandSwitch: View {
    @Binding var isOn: Bool

    var body: some View {
        Toggle("", isOn: $isOn)
            .labelsHidden()
            .tint(Color.oliveWood)
    }
}

/// Label + value row inside a Stone panel.
struct StoneKeyValueRow: View {
    let label: String
    let value: String
    var valueColor: Color = .ink
    var onTap: (() -> Void)?

    var body: some View {
        let row = HStack {
            Text(label).font(Typo.bodyLarge).foregroundStyle(Color.textMuted)
            Spacer()
            Text(value)
                .font(Typo.titleSmall)
                .foregroundStyle(valueColor)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        if let onTap {
            Button(action: onTap) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// Bottom action area pinned under the content, separated by a hairline.
struct BottomActionBar<Content: View>: View {
    var container: Color = .bone
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.stoneBorder).frame(height: 1)
            VStack(spacing: 0) { content }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
        }
        .background(container)
    }
}

/// Segmented strength/progress meter (password strength, occupancy).
struct SegmentedMeter: View {
    let filled: Int
    let total: Int
    var color: Color = .oliveWood
    var trackColor: Color = .stone

    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<total, id: \.self) { index in
                RoundedRectangle(cornerRadius: 2)
                    .fill(index < filled ? color : trackColor)
                    .frame(height: 3)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

/// Stone row with a leading icon and a chevron — "other ways in", settings entries.
struct NavigationRow: View {
    let text: String
    let action: () -> Void
    var leadingSystemImage: String?

    var body: some View {
        Button(action: action) {
            HStack(spacing: 11) {
                if let leadingSystemImage {
                    Image(systemName: leadingSystemImage)
                        .font(.system(size: 15))
                        .foregroundStyle(Color.ink)
                }
                Text(text)
                    .font(Typo.bodyMedium)
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textMuted)
            }
            .padding(.horizontal, 15)
            .frame(height: 50)
            .background(RoundedRectangle(cornerRadius: 15).fill(Color.stone))
        }
        .buttonStyle(.plain)
    }
}

/// Olive Wood checkbox with the mockup's rounded-square shape.
struct BrandCheckbox: View {
    @Binding var checked: Bool

    var body: some View {
        Button {
            checked.toggle()
        } label: {
            RoundedRectangle(cornerRadius: 7)
                .fill(checked ? Color.oliveWood : Color.stone)
                .frame(width: 22, height: 22)
                .overlay(
                    checked
                        ? Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Color.bone)
                        : nil
                )
        }
        .buttonStyle(.plain)
    }
}

/// Barra in fondo che esiste solo quando si può andare avanti: sale dal bordo
/// inferiore quando `visible` diventa vero e riscende se la selezione si
/// svuota. Avvolge `DarkContinueBar` e `DarkTotalBar` nel wizard di prenotazione.
struct BottomBarReveal<Content: View>: View {
    let visible: Bool
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            if visible {
                content.transition(.move(edge: .bottom))
            }
        }
        .animation(.snappy, value: visible)
    }
}

/// Light footer holding a dark "continue" block with an olive arrow button.
struct DarkContinueBar: View {
    let label: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.stoneBorder).frame(height: 1)
            Button(action: action) {
                HStack {
                    Text(label)
                        .font(Typo.jost(15, weight: .medium))
                        .foregroundStyle(Color.bone)
                        .lineLimit(1)
                    Spacer()
                    RoundedRectangle(cornerRadius: 11)
                        .fill(Color.oliveWood)
                        .frame(width: 38, height: 38)
                        .overlay(
                            Image(systemName: "arrow.right")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(Color.bone)
                        )
                }
                .padding(.leading, 22)
                .padding(.trailing, 8)
                .frame(height: 54)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .readableWidth()
        }
        .background(Color.bone)
    }
}

/// Dark running-total footer with an olive call to action (booking steps 2 and 3).
struct DarkTotalBar: View {
    let caption: String
    let value: String
    let ctaLabel: String
    let action: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(caption.uppercased())
                    .font(Typo.jost(10, weight: .medium))
                    .kerning(1.3)
                    .foregroundStyle(Color.oliveWood)
                    .lineLimit(1)
                Text(value)
                    .font(Typo.cormorant(26))
                    .foregroundStyle(Color.bone)
                    .lineLimit(1)
            }
            Spacer()
            Button(action: action) {
                HStack(spacing: 9) {
                    Text(ctaLabel).font(Typo.jost(15, weight: .medium))
                    Image(systemName: "arrow.right").font(.system(size: 14, weight: .medium))
                }
                .foregroundStyle(Color.bone)
                .padding(.horizontal, 22)
                .frame(height: 50)
                .background(RoundedRectangle(cornerRadius: 15).fill(Color.oliveWood))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .readableWidth()
        .background(
            Color.ink
                .clipShape(.rect(topLeadingRadius: 26, topTrailingRadius: 26))
                .ignoresSafeArea(edges: .bottom)
        )
    }
}
