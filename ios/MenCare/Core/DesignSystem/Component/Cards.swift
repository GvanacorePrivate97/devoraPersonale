import SwiftUI

struct OutlineCard<Content: View>: View {
    var onTap: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        let card = VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.stoneBorder, lineWidth: 1))
        if let onTap {
            Button(action: onTap) { card }.buttonStyle(.plain)
        } else {
            card
        }
    }
}
