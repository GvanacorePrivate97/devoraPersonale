import SwiftUI

/// Circle with serif initials — stands in for people photos across the app.
s

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

/// Pill d'accento. Il fondo è un tono pieno, non l'oliva al 14%: con l'opacity
/// il contrasto del testo cambiava a seconda di cosa c'era sotto (4.5:1 su
/// Bone, 3.3:1 su Stone). Così è 4.76:1 ovunque.
s

s
