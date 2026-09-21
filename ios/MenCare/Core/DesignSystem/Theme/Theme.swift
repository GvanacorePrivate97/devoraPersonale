import SwiftUI

// Brand palette — Antonio De Vito · Men Care.
// Mirrors the Android `core:designsystem` theme tokens.
//
// Ogni colore regge almeno 4.5:1 (WCAG 2.1 AA, testo normale) sulle superfici
// su cui l'app lo usa davvero. Stone è la più severa delle due superfici
// chiare, quindi è quella su cui sono tarati i toni di testo.
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

    /// Accento su superficie CHIARA: testo, icone, bordi e i riempimenti che
    /// portano testo Bone. 4.70:1 su Stone, 5.51:1 su Bone, e Bone sopra di lui
    /// 5.51:1 — funziona sia come inchiostro sia come fondo.
    static let oliveWood = Color(hex: 0x77654B) // accent

    /// Accento su superficie SCURA: è l'oro del marchio, campionato dalla
    /// scritta "MEN CARE" del logo ufficiale. 8.65:1 su Ink, dove l'oliva scura
    /// si fermava a 3.75:1.
    static let oliveLight = Color(hex: 0xBFA277)

    /// Stato premuto dell'accento chiaro.
    /// Oro chiaro: testo e icone della voce attiva nella barra nera, dove
    /// l'oro pieno su fondo oro-trasparente perderebbe stacco. 13.4:1 su Ink.
    static let goldSoft = Color(hex: 0xE7D9BF)

    static let oliveWoodDark = Color(hex: 0x6A5A43)

    /// Fondo piatto delle pill d'accento: niente opacity, così è uguale su Bone e su Stone.
    static let oliveTint = Color(hex: 0xEFECE9)

    static let ink = Color(hex: 0x000006) // dark bands, primary text
    static let inkSoft = Color(hex: 0x000004)
    static let onDarkMuted = Color(hex: 0xB9B6AE) // secondary text on dark bands — 10.4:1 su Ink
    static let stoneBorder = Color(hex: 0xDBDAD6)
    /// Fasce non prenotabili dell'agenda: pieno, così due fasce sovrapposte non si scuriscono.
    static let stoneSoft = Color(hex: 0xF3F3F2)
    /// Testo secondario su chiaro: 5.67:1 su Stone, 6.65:1 su Bone.
    static let textMuted = Color(hex: 0x5F5B52)

    // Semantic — tarati su Stone.
    /// Andamento sulle bande scure: verde se sale, rosso se scende. I semantici
    /// da fondo chiaro sul nero non arrivano a 3:1, quindi il trend ha la sua
    /// coppia schiarita — 9.9:1 e 8.4:1 su Ink.
    static let trendUp = Color(hex: 0x9FD3B2)
    static let trendDown = Color(hex: 0xE2A2A2)

    static let successGreen = Color(hex: 0x3E6B4C) // 5.16:1 su Stone
    static let warnAmber = Color(hex: 0x8A5F1B) // 4.72:1 su Stone
    static let errorRed = Color(hex: 0x8C2F2F) // 6.88:1 su Stone
}

/// Type scale — same roles and sizes as the Android Material 3 typography.
/// Cormorant Garamond carries display/headlines, Jost carries body/UI.
///
/// Due regole, e valgono per tutta l'app:
///
/// 1. **Pesi.** Jost è un geometric sans dalle aste sottili: a `.regular` su
///    fondo chiaro sparisce. Il corpo parte da `.medium`, titoli ed etichette
///    da `.semibold`, i display serif da `.bold`. Niente è più `.regular`.
/// 2. **Pavimento.** Nessuno stile scende sotto gli 11 pt, e sotto i 12 pt ci
///    vanno solo etichette maiuscole brevi — mai una frase da leggere.
enum Typo {
    static func cormorant(_ size: CGFloat, weight: Font.Weight = .bold) -> Font {
        .custom("Cormorant Garamond", size: size).weight(weight)
    }

    static func jost(_ size: CGFloat, weight: Font.Weight = .medium) -> Font {
        .custom("Jost", size: size).weight(weight)
    }

    static let displayLarge = cormorant(44)
    static let displayMedium = cormorant(36)
    static let displaySmall = cormorant(30)
    static let headlineLarge = cormorant(28)
    static let headlineMedium = cormorant(24)
    static let headlineSmall = cormorant(21)
    static let titleLarge = jost(18, weight: .semibold)
    static let titleMedium = jost(16, weight: .semibold)
    static let titleSmall = jost(14, weight: .semibold)
    static let bodyLarge = jost(16)
    static let bodyMedium = jost(14)
    static let bodySmall = jost(12)
    static let labelLarge = jost(14, weight: .semibold)
    static let labelMedium = jost(12, weight: .semibold)
    static let labelSmall = jost(11, weight: .semibold)

    /// Etichetta maiuscola di sezione — un ruolo solo, invece dei 9/10/11 pt
    /// spaziati a mano che giravano per le schermate. Va con `.sectionTracking()`.
    static let overline = jost(11, weight: .semibold)

    /// Didascalie, orari della griglia, marche temporali: il gradino sotto
    /// `bodySmall` per peso e colore, non per corpo.
    static let meta = jost(12, weight: .medium)
}

extension View {
    /// Small uppercase section label tracking, as the Android `Overline`.
    func sectionTracking() -> some View {
        kerning(1.5)
    }
}

/// Scala dei raggi — sei valori, non venti, gli stessi di `Radii` su Android.
///
/// Le schermate giravano con una ventina di raggi scritti a mano: due card
/// vicine non avevano mai lo stesso angolo. Qui si sceglie il ruolo, non il
/// numero.
enum Radii {
    /// Micro-elementi: barre di avanzamento, tacche, indicatori.
    static let xs: CGFloat = 6
    /// Controlli piccoli: chip compatti, quadratini, riquadri d'icona.
    static let sm: CGFloat = 10
    /// Il raggio di serie: bottoni, campi, card, tessere.
    static let md: CGFloat = 16
    /// Contenitori grandi: card scure in evidenza, fogli, sheet.
    static let lg: CGFloat = 22
    /// Bande e fogli a tutta larghezza: la testa scura, il foglio chiaro.
    static let xl: CGFloat = 28
    /// Pillole: barra di navigazione, tab, badge di stato.
    static let pill: CGFloat = 999
}
