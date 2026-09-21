import SwiftUI

/// The brand mark in its rounded-square outline, as it appears on dark bands.
struct LogoBadge: View {
    var size: CGFloat = 44
    var corner: CGFloat = 14
    var borderWidth: CGFloat = 1.5

    var body: some View {
        RoundedRectangle(cornerRadius: corner)
            .strokeBorder(Color.bone, lineWidth: borderWidth)
            .frame(width: size, height: size)
            .overlay(
                Image("logo-mark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: size * 0.52, height: size * 0.52)
            )
    }
}

/// Bell on a dark band, with an olive dot while something is unread. Same
/// button in the client, staff and owner headers.
struct NotificationBell: View {
    let hasUnread: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.bone.opacity(0.1))
                    .frame(width: 38, height: 38)
                    .overlay(
                        Image(systemName: "bell")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.bone)
                    )
                if hasUnread {
                    Circle()
                        .fill(Color.oliveWood)
                        .frame(width: 7, height: 7)
                        .padding(.top, 8)
                        .padding(.trailing, 9)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("ds_notifications"))
    }
}
