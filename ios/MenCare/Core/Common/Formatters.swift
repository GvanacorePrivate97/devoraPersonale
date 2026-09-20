import Foundation

// Italian-locale formatting shared by every screen — mirrors the Android
// core:common formatters so both apps print identical strings.

private let italian = Locale(identifier: "it_IT")

private let thousandsFormatter: NumberFormatter = {
    let f = NumberFormatter()
    f.locale = italian
    f.numberStyle = .decimal
    f.maximumFractionDigits = 0
    return f
}()

private func groupedEuros(_ euros: Int64) -> String {
    thousandsFormatter.string(from: NSNumber(value: euros)) ?? "\(euros)"
}

/// "€ 22,00"
func formatPrice(_ cents: Int64) -> String {
    let euros = cents / 100
    let rest = cents % 100
    return "€ \(groupedEuros(euros)),\(String(format: "%02d", rest))"
}

/// "€ 22" or "€ 22,50" — compact form used in lists.
func formatPriceCompact(_ cents: Int64) -> String {
    cents % 100 == 0 ? "€ \(groupedEuros(cents / 100))" : formatPrice(cents)
}

/// "75 min"
func formatDuration(_ minutes: Int) -> String { "\(minutes) min" }

/// "1h 15m" for agenda summaries.
func formatDurationLong(_ minutes: Int) -> String {
    let h = minutes / 60
    let m = minutes % 60
    if h == 0 { return "\(m)m" }
    if m == 0 { return "\(h)h" }
    return "\(h)h \(m)m"
}

func formatTime(_ time: LocalTime) -> String {
    String(format: "%02d:%02d", time.hour, time.minute)
}

private let monthsFull = [
    "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
    "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
]
private let monthsShort = [
    "gen", "feb", "mar", "apr", "mag", "giu",
    "lug", "ago", "set", "ott", "nov", "dic",
]
private let daysFull = ["lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"]
private let daysShort = ["lun", "mar", "mer", "gio", "ven", "sab", "dom"]

/// "venerdì 11 settembre"
func formatDateLong(_ date: LocalDate) -> String {
    "\(daysFull[date.dayOfWeek.rawValue - 1]) \(date.day) \(monthsFull[date.month - 1])"
}

/// "ven 11 set"
func formatDateShort(_ date: LocalDate) -> String {
    "\(daysShort[date.dayOfWeek.rawValue - 1]) \(date.day) \(monthsShort[date.month - 1])"
}

/// "lun" — il nome breve del giorno, senza data: la striscia dei giorni
/// dell'agenda vuole solo quello, e ritagliarlo da `formatDateShort` significava
/// dipendere dal fatto che il nome venga per primo.
func formatDayNameShort(_ date: LocalDate) -> String {
    daysShort[date.dayOfWeek.rawValue - 1]
}

/// "settembre 2026"
func formatMonthYear(_ date: LocalDate) -> String {
    "\(monthsFull[date.month - 1]) \(date.year)"
}

func formatDateTime(_ dt: LocalDateTime) -> String {
    "\(formatDateShort(dt.date)) · \(formatTime(dt.time))"
}

/// "Sabato" for one day, "Lun — Mer" for a run of them.
func formatDayGroup(_ days: [DayOfWeek]) -> String {
    guard let first = days.first else { return "" }
    func full(_ day: DayOfWeek) -> String {
        daysFull[day.rawValue - 1].prefix(1).uppercased() + daysFull[day.rawValue - 1].dropFirst()
    }
    func short(_ day: DayOfWeek) -> String {
        daysShort[day.rawValue - 1].prefix(1).uppercased() + daysShort[day.rawValue - 1].dropFirst()
    }
    return days.count == 1 ? full(first) : "\(short(first)) — \(short(days.last!))"
}

/// `tel:` link for a stored phone number ("+39 347 812 4490" → "tel:+393478124490"),
/// for a "Chiama" button: opened with `openURL`, it brings up the dialer. On an
/// iPad without telephony nothing happens.
func phoneDialURL(_ phone: String?) -> URL? {
    let number = (phone ?? "").filter { $0.isNumber || $0 == "+" }
    return number.isEmpty ? nil : URL(string: "tel:\(number)")
}

extension String {
    /// "venerdì 11 settembre" → "Venerdì 11 settembre".
    ///
    /// Stava in `StepDatetime.swift`, cioè dentro una feature, mentre a usarlo
    /// sono anche il design system e due agende: da lì in giù nessuno può
    /// importare una feature, quindi vive qui.
    var capitalizedFirst: String {
        prefix(1).uppercased() + dropFirst()
    }
}
