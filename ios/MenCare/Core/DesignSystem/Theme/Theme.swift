import SwiftUI

// Brand palette — Antonio De Vito · Men Care.
// Mirrors the Android `core:designsystem` theme tokens.
extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    static let bone = Color(hex: 0xFDFDFD) // background
    static let stone = Color(hex: 0xEBEBEA) // surface / secondary
    static let oliveWood = Color(hex: 0x867357) // accent
    static let oliveWoodDark = Color(hex: 0x6A5A43)
    static let ink = Color(hex: 0x000006) // dark bands, primary text
    static let inkSoft = Color(hex: 0x000004)
    static let onDarkMuted = Color(hex: 0xB9B6AE) // secondary text on dark bands
    static let stoneBorder = Color(hex: 0xDBDAD6)
    /// Fasce non prenotabili dell'agenda: pieno, così due fasce sovrapposte non si scuriscono.
    static let stoneSoft = Color(hex: 0xF3F3F2)
    static let textMuted = Color(hex: 0x6E6A61)

    // Semantic
    static let successGreen = Color(hex: 0x3E6B4C)
    static let warnAmber = Color(hex: 0x9A6B1F)
    static let errorRed = Color(hex: 0x8C2F2F)
}

/// Type scale — same roles and sizes as the Android Material 3 typography.
/// Cormorant Garamond carries display/headlines, Jost carries body/UI.
enum Typo {
    static func cormorant(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        .custom("Cormorant Garamond", size: size).weight(weight)
    }

    static func jost(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom("Jost", size: size).weight(weight)
    }

    static let displayLarge = cormorant(44)
    static let displayMedium = cormorant(36)
    static let displaySmall = cormorant(30)
    static let headlineLarge = cormorant(28)
    static let headlineMedium = cormorant(24)
    static let headlineSmall = cormorant(21)
    static let titleLarge = jost(18, weight: .medium)
    static let titleMedium = jost(16, weight: .medium)
    static let titleSmall = jost(14, weight: .medium)
    static let bodyLarge = jost(16)
    static let bodyMedium = jost(14)
    static let bodySmall = jost(12)
    static let labelLarge = jost(14, weight: .medium)
    static let labelMedium = jost(12, weight: .medium)
    static let labelSmall = jost(10, weight: .medium)
}

extension View {
    /// Small uppercase section label tracking, as the Android labelSmall.
    func sectionTracking() -> some View {
        kerning(0.8)
    }
}
