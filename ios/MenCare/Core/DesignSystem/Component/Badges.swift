import SwiftUI

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
