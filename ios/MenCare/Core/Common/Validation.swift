import Foundation

/// Regole di validazione dell'intero prodotto: qui e basta.
///
/// Copia parola per parola di `backend/src/lib/validation.ts` (e gemella di
/// `android/core/common/.../Validation.kt`): stessi limiti, stessa
/// normalizzazione, stesse frasi d'errore. Il form dà la risposta subito,
/// il server resta l'ultima parola e non si fida mai del client.
/// Cambiare una regola nel backend vuol dire cambiarla anche qui.

// MARK: - Esito

/// Esito di una regola: `nil` come messaggio significa "valore accettato".
/// Volutamente minimale — la UI vuole solo sapere se mostrare una riga rossa
/// e con che testo.
enum ValidationResult: Equatable {
    case valid
    case invalid(String)

    var isValid: Bool {
        if case .valid = self { return true }
        return false
    }

    /// Il messaggio già localizzato da mostrare sotto il campo, o `nil`.
    var message: String? {
        if case .invalid(let message) = self { return message }
        return nil
    }
}

// MARK: - Limiti

enum ValidationLimits {
    static let nameMin = 2
    static let nameMax = 50
    static let passwordMin = 8
    static let passwordMax = 72
    static let emailMax = 254
    /// Testo libero obbligatorio (nome servizio, nome campagna, titolo push).
    static let textMax = 60
    static let noteMax = 200
    static let campaignBodyMax = 140
    static let durationMinMinutes = 5
    static let durationMaxMinutes = 480
    /// La griglia degli slot è di 30 minuti, ma le durate si muovono a passi di 5.
    static let durationStepMinutes = 5
    static let priceMinCents: Int64 = 0
    static let priceMaxCents: Int64 = 100_000
}

/// Alias storici usati dai contatori di caratteri già in giro per l'app:
/// il numero adesso arriva da `ValidationLimits`, non più da due costanti sparse.
let maxNoteLength = ValidationLimits.noteMax
let campaignBodyLimit = ValidationLimits.campaignBodyMax

/// Prefisso usato quando il numero arriva senza indicativo internazionale.
let defaultPhoneCountryCode = "+39"

// MARK: - Telefono

/// Porta un numero scritto a mano nel formato E.164 (`+393478124490`).
/// Accetta spazi, punti, trattini e parentesi, `00` al posto di `+`, e i numeri
/// italiani senza prefisso. Torna `nil` se non ne esce un numero plausibile.
func normalizePhone(_ input: String) -> String? {
    let cleaned = input.filter { !" .-()\u{00A0}".contains($0) }
    if cleaned.isEmpty { return nil }
    let e164: String
    if cleaned.hasPrefix("+") {
        e164 = cleaned
    } else if cleaned.hasPrefix("00") {
        e164 = "+" + cleaned.dropFirst(2)
    } else {
        // Niente zero da togliere: in Italia il prefisso dei fissi lo tiene (+39 081…).
        e164 = defaultPhoneCountryCode + cleaned
    }
    let digits = e164.dropFirst()
    guard digits.count >= 8, digits.count <= 15 else { return nil }
    guard let first = digits.first, ("1"..."9").contains(first) else { return nil }
    guard digits.allSatisfy({ $0.isASCIIDigit }) else { return nil }
    // I numeri italiani hanno da 8 a 11 cifre dopo il +39: fuori da lì è un refuso.
    if e164.hasPrefix("+39") {
        let national = e164.dropFirst(3)
        if national.count < 8 || national.count > 11 { return nil }
    }
    return e164
}

/// "+393478124490" → "+39 347 812 4490": la forma leggibile nei campi e nelle schede.
func formatPhone(_ e164: String) -> String {
    guard e164.hasPrefix(defaultPhoneCountryCode) else { return e164 }
    let digits = Array(e164.dropFirst(defaultPhoneCountryCode.count))
    guard digits.count >= 6 else { return e164 }
    let groups: [String] = digits.count >= 9
        ? [String(digits[0..<3]), String(digits[3..<6]), String(digits[6...])]
        : [String(digits[0..<3]), String(digits[3...])]
    return defaultPhoneCountryCode + " " + groups.joined(separator: " ")
}

// MARK: - Forza della password

/// Forza della password mostrata dal misuratore: gli stessi tre gradini del
/// backend (`DEBOLE` / `MEDIA` / `FORTE`).
enum PasswordStrength {
    case debole, media, forte

    /// Segmenti accesi nel misuratore, su quattro.
    var meterFill: Int {
        switch self {
        case .debole: 1
        case .media: 2
        case .forte: 3
        }
    }

    var label: String {
        switch self {
        case .debole: L("validation_strength_weak")
        case .media: L("validation_strength_medium")
        case .forte: L("validation_strength_strong")
        }
    }
}

/// Punteggio identico a quello del backend: lunghezza, maiuscole+minuscole,
/// cifre e simboli. Le classi sono ASCII come in JavaScript, altrimenti una
/// "è" conterebbe come simbolo su una piattaforma e come lettera sull'altra.
func passwordStrength(_ password: String) -> PasswordStrength {
    var score = 0
    if password.count >= ValidationLimits.passwordMin { score += 1 }
    if password.count >= 12 { score += 1 }
    if password.contains(where: \.isASCIILower) && password.contains(where: \.isASCIIUpper) { score += 1 }
    if password.contains(where: \.isASCIIDigit) { score += 1 }
    if password.contains(where: \.isPasswordSymbol) { score += 1 }
    if score <= 2 { return .debole }
    if score == 3 { return .media }
    return .forte
}

// MARK: - Regole di campo

/// Nome e cognome: lettere (anche accentate), spazi, apostrofi, trattini e
/// punti — "D'Amico", "De Vito", "Jr.".
func validateName(_ value: String) -> ValidationResult {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return .invalid(L("validation_required")) }
    if trimmed.count < ValidationLimits.nameMin || trimmed.count > ValidationLimits.nameMax {
        return .invalid(L("validation_name_length"))
    }
    // Equivalente di `^[\p{L}][\p{L}'\-. ]*[\p{L}.]$`, scritto a mano per non
    // dipendere da come i due dialetti di regex trattano `\p{L}`.
    guard let first = trimmed.first, let last = trimmed.last else {
        return .invalid(L("validation_name_letters"))
    }
    let bodyOk = trimmed.allSatisfy { $0.isLetter || $0 == "'" || $0 == "-" || $0 == "." || $0 == " " }
    guard first.isLetter, last.isLetter || last == ".", bodyOk else {
        return .invalid(L("validation_name_letters"))
    }
    return .valid
}

/// Testo libero obbligatorio: trim più un tetto. Il nome di un servizio non è
/// un nome proprio — "Shampoo + taglio" deve passare — quindi non usa
/// `validateName`.
func validateRequiredText(_ value: String, max: Int = ValidationLimits.textMax) -> ValidationResult {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return .invalid(L("validation_required")) }
    if trimmed.count > max { return .invalid(L("validation_text_max", max)) }
    return .valid
}

private let emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

func validateEmail(_ value: String) -> ValidationResult {
    let normalized = normalizeEmail(value)
    if normalized.isEmpty { return .invalid(L("validation_required")) }
    guard normalized.count <= ValidationLimits.emailMax,
          normalized.wholeMatch(of: emailPattern) != nil else {
        return .invalid(L("validation_email"))
    }
    return .valid
}

/// L'email si salva sempre minuscola e senza spazi: due account che
/// differiscono per una maiuscola sarebbero lo stesso account.
func normalizeEmail(_ value: String) -> String {
    value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
}

/// `required: false` accetta il campo vuoto (telefono dell'operatore, dove il
/// numero è un di più) ma non un numero scritto male.
func validatePhone(_ value: String, required: Bool = true) -> ValidationResult {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty {
        return required ? .invalid(L("validation_required")) : .valid
    }
    return normalizePhone(trimmed) == nil ? .invalid(L("validation_phone")) : .valid
}

/// Almeno una lettera e una cifra: la stessa regola del misuratore nel form.
func validatePassword(_ value: String) -> ValidationResult {
    if value.isEmpty { return .invalid(L("validation_required")) }
    if value.count < ValidationLimits.passwordMin { return .invalid(L("validation_password_min")) }
    if value.count > ValidationLimits.passwordMax { return .invalid(L("validation_password_max")) }
    if !value.contains(where: \.isLetter) { return .invalid(L("validation_password_letter")) }
    if !value.contains(where: \.isASCIIDigit) { return .invalid(L("validation_password_digit")) }
    return .valid
}

func validatePasswordConfirm(_ password: String, _ confirm: String) -> ValidationResult {
    if confirm.isEmpty { return .invalid(L("validation_required")) }
    return confirm == password ? .valid : .invalid(L("validation_password_match"))
}

/// La nota è facoltativa: vuota va bene, troppo lunga no.
func validateNote(_ value: String) -> ValidationResult {
    value.count > ValidationLimits.noteMax
        ? .invalid(L("validation_note_max", ValidationLimits.noteMax))
        : .valid
}

func normalizeNote(_ value: String) -> String? {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? nil : trimmed
}

/// Durate a passi di 5 minuti, da 5 minuti a 8 ore.
func validateDuration(_ minutes: Int) -> ValidationResult {
    if minutes < ValidationLimits.durationMinMinutes { return .invalid(L("validation_duration_min")) }
    if minutes > ValidationLimits.durationMaxMinutes { return .invalid(L("validation_duration_max")) }
    if minutes % ValidationLimits.durationStepMinutes != 0 { return .invalid(L("validation_duration_step")) }
    return .valid
}

func clampDuration(_ minutes: Int) -> Int {
    min(max(minutes, ValidationLimits.durationMinMinutes), ValidationLimits.durationMaxMinutes)
}

// MARK: - Prezzo

/// Tiene nel campo prezzo solo ciò che può diventare un prezzo: cifre, un solo
/// separatore decimale (normalizzato a virgola) e al massimo due decimali.
/// È il filtro che gira a ogni tasto, così "1.2.3" non arriva mai al parser
/// (prima finiva salvato come 0,00 €).
func sanitizePriceInput(_ value: String) -> String {
    var out = ""
    var separatorSeen = false
    var decimals = 0
    for character in value {
        if character.isASCIIDigit {
            if separatorSeen {
                if decimals == 2 { continue }
                decimals += 1
            }
            out.append(character)
        } else if character == "." || character == "," {
            if separatorSeen || out.isEmpty { continue }
            separatorSeen = true
            out.append(",")
        }
    }
    return out
}

/// Euro scritti a mano → centesimi. Si arrotonda, non si tronca: "19,99" deve
/// fare 1999 e non 1998 come faceva la conversione via Double su Android.
/// `Decimal` evita del tutto l'errore di rappresentazione binaria.
func parsePriceToCents(_ euros: String) -> Int64? {
    let normalized = euros
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: ",", with: ".")
    if normalized.isEmpty { return nil }
    guard normalized.allSatisfy({ $0.isASCIIDigit || $0 == "." }),
          normalized.filter({ $0 == "." }).count <= 1,
          normalized.contains(where: \.isASCIIDigit),
          var value = Decimal(string: normalized, locale: Locale(identifier: "en_US_POSIX")) else {
        return nil
    }
    value *= 100
    var rounded = Decimal()
    NSDecimalRound(&rounded, &value, 0, .plain)
    return NSDecimalNumber(decimal: rounded).int64Value
}

/// 1999 → "19,99", 2200 → "22": il testo con cui si apre il campo prezzo.
func formatCentsAsInput(_ cents: Int64) -> String {
    cents % 100 == 0 ? "\(cents / 100)" : String(format: "%d,%02d", cents / 100, abs(cents % 100))
}

/// Prezzi in centesimi, da 0 a 1.000 €.
func validatePrice(_ cents: Int64) -> ValidationResult {
    if cents < ValidationLimits.priceMinCents { return .invalid(L("validation_price_min")) }
    if cents > ValidationLimits.priceMaxCents { return .invalid(L("validation_price_max")) }
    return .valid
}

/// Il campo prezzo parla in euro: qui si controlla il testo e si ricade sulla
/// regola in centesimi, la stessa del backend.
func validatePriceInput(_ euros: String) -> ValidationResult {
    if euros.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return .invalid(L("validation_required"))
    }
    guard let cents = parsePriceToCents(euros) else { return .invalid(L("validation_price_invalid")) }
    return validatePrice(cents)
}

// MARK: - Classi di caratteri

/// Le classi del backend sono quelle di JavaScript (`\d`, `[a-z]`, `[A-Z]`,
/// `[^\w\s]`), cioè ASCII: le rifacciamo uguali invece di usare le versioni
/// Unicode di Swift, altrimenti lo stesso punteggio darebbe risultati diversi.
private extension Character {
    var isASCIIDigit: Bool { isASCII && ("0"..."9").contains(self) }
    var isASCIILower: Bool { isASCII && ("a"..."z").contains(self) }
    var isASCIIUpper: Bool { isASCII && ("A"..."Z").contains(self) }
    var isPasswordSymbol: Bool {
        !isWhitespace && !isASCIIDigit && !isASCIILower && !isASCIIUpper && self != "_"
    }
}
