import SwiftUI

struct OutlineCard<Content: View>: View {
    var onTap: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        let card = VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.bone))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.stoneBorder, lineWidth: 1))
        if let onTap {
            Button(action: onTap) { card }.buttonStyle(.plain)
        } else {
            card
        }
    }
}

struct DarkCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20).fill(Color.ink))
            .foregroundStyle(Color.bone)
    }
}

/// "label — value" row used in summaries.
struct KeyValueRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).font(Typo.bodyMedium).foregroundStyle(Color.textMuted)
            Spacer()
            Text(value).font(Typo.titleSmall).foregroundStyle(Color.ink)
        }
    }
}
