import SwiftUI

struct PrimaryButton: View {
    let text: String
    let action: () -> Void
    var enabled: Bool = true
    var loading: Bool = false

    var body: some View {
        Button(action: action) {
            Group {
                if loading {
                    ProgressView().tint(.bone)
                } else {
                    Text(text).font(Typo.titleMedium)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.bone)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.ink))
        .opacity(enabled && !loading ? 1 : 0.5)
        .disabled(!enabled || loading)
    }
}

struct AccentButton: View {
    let text: String
    let action: () -> Void
    var enabled: Bool = true
    var loading: Bool = false
    var height: CGFloat = 52
    var corner: CGFloat = 14
    var leadingSystemImage: String?

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                if loading {
                    ProgressView().tint(.bone)
                } else {
                    if let leadingSystemImage {
                        Image(systemName: leadingSystemImage).font(.system(size: 15))
                    }
                    Text(text).font(Typo.titleMedium).kerning(0.8)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.bone)
        .background(RoundedRectangle(cornerRadius: corner).fill(Color.oliveWood))
        .opacity(enabled && !loading ? 1 : 0.5)
        .disabled(!enabled || loading)
    }
}

/// Social sign-in button: brand glyph on the Stone surface.
struct SocialButton: View {
    let text: String
    let icon: Image
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                icon.resizable().scaledToFit().frame(width: 16, height: 16)
                Text(text).font(Typo.titleSmall)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.ink)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
    }
}

struct SecondaryButton: View {
    let text: String
    let action: () -> Void
    var enabled: Bool = true
    var onDark: Bool = false

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Typo.titleMedium)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
        }
        .buttonStyle(.plain)
        .foregroundStyle(onDark ? Color.bone : Color.ink)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(onDark ? Color.bone.opacity(0.4) : Color.stoneBorder, lineWidth: 1)
        )
        .opacity(enabled ? 1 : 0.5)
        .disabled(!enabled)
    }
}

struct LinkButton: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Typo.labelLarge)
                .foregroundStyle(Color.oliveWood)
        }
        .buttonStyle(.plain)
    }
}
