import SwiftUI

/// Il cliente di un appuntamento in modifica: si mostra e basta, niente ricerca
/// né "Crea nuovo cliente". Lo usano il foglio del titolare e quello dell'operatore.
struct FixedClientRow: View {
    let client: ClientRecord?

    var body: some View {
        HStack(spacing: 11) {
            Circle()
                .fill(Color.bone)
                .frame(width: 34, height: 34)
                .overlay(
                    Text(client?.initials ?? "")
                        .font(Typo.cormorant(12, weight: .regular))
                        .foregroundStyle(Color.ink)
                )
            VStack(alignment: .leading, spacing: 0) {
                Text(client?.fullName ?? "")
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                if let phone = client?.phone {
                    Text(phone)
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.textMuted)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md)
                .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
        )
    }
}
