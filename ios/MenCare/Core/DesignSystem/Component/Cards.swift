import SwiftUI

struct OutlineCard<Content: View>: View {
    var onTap: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        let card = VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
            .overlay(RoundedRectangle(cornerRadius: Radii.md).strokeBorder(Color.stoneBorder, lineWidth: 1.5))
        if let onTap {
            Button(action: onTap) { card }.buttonStyle(.plain)
        } else {
            card
        }
    }
}

/// Avviso: qualcosa richiede una mossa prima di poter procedere (gli appuntamenti
/// in conflitto con un blocco). Ha il suo colore, l'ambra: l'accento lo faceva
/// leggere come una cosa del marchio, il rosso come un guasto — qui invece non è
/// rotto niente, c'è solo qualcosa da spostare.
struct WarningCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.circle")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.warnAmber)
                Text(title)
                    .font(Typo.jost(15, weight: .medium))
                    .foregroundStyle(Color.warnAmber)
            }
            content
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.warnTint))
    }
}
