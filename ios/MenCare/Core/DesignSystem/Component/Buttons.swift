import SwiftUI

s

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

s

s
