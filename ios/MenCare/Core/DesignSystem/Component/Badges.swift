import SwiftUI

/// Circle with serif initials — stands in for people photos across the app.
struct InitialsAvatar: View {
    let initials: String
    var size: CGFloat = 44
    var dark: Bool = false

    var body: some View {
        Circle()
            .fill(dark ? Color.ink : Color.stone)
            .frame(width: size, height: size)
            .overlay(
                Text(initials)
                    .font(Typo.cormorant(size * 0.42, weight: .regular))
                    .foregroundStyle(dark ? Color.bone : Color.ink)
            )
    }
}

/// Small rounded status/category pill.
struct Pill: View {
    let text: String
    var container: Color = .stone
    var content: Color = .ink

    var body: some View {
        Text(text)
            .font(Typo.labelMedium)
            .foregroundStyle(content)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(container))
    }
}

struct AccentPill: View {
    let text: String

    var body: some View {
        Pill(text: text, container: .oliveWood.opacity(0.14), content: .oliveWood)
    }
}

struct DarkPill: View {
    let text: String

    var body: some View {
        Pill(text: text, container: .ink, content: .bone)
    }
}
