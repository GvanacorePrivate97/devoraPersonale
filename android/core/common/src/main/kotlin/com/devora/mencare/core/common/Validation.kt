package com.devora.mencare.core.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Regole di validazione dell'intero prodotto: qui e basta, per Android.
 *
 * È la copia parola per parola di `backend/src/lib/validation.ts` (limiti,
 * normalizzazioni e messaggi) e gemella di `ios/MenCare/Core/Common/Validation.swift`.
 * Il form dà l'errore subito; il server resta l'ultima parola e non si fida mai
 * di ciò che arriva. Cambiare una regola sul backend vuol dire cambiarla qui.
 *
 * Il modulo è Kotlin puro: i messaggi vivono in `:core:designsystem`
 * (`validationMessage`), così la copy resta nelle risorse stringa.
 */

const val NAME_MIN = 2
const val NAME_MAX = 50
const val PASSWORD_MIN = 8
const val PASSWORD_MAX = 72
const val EMAIL_MAX = 254
const val NOTE_MAX = 200
const val CAMPAIGN_BODY_MAX = 140

/** Limite generico dei campi di testo liberi (nome servizio, nome campagna, titolo push). */
const val TEXT_MAX = 60

const val DURATION_MIN_MINUTES = 5
const val DURATION_MAX_MINUTES = 480
const val DURATION_STEP_MINUTES = 5

const val PRICE_MAX_CENTS = 100_000L

/** Prefisso usato quando il numero arriva senza indicativo internazionale. */
const val DEFAULT_PHONE_COUNTRY_CODE = "+39"

/** Lettere (anche accentate), spazi, apostrofi e trattini: "D'Amico", "De Vito". */
private val NAME_REGEX = Regex("^\\p{L}[\\p{L}'\\-. ]*[\\p{L}.]$")
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
private val E164_REGEX = Regex("^\\+[1-9]\\d{7,14}$")
private val PHONE_SEPARATORS = Regex("[\\s.\\-()]")

private val LOWERCASE_REGEX = Regex("[a-z]")
private val UPPERCASE_REGEX = Regex("[A-Z]")
private val DIGIT_REGEX = Regex("\\d")
private val LETTER_REGEX = Regex("\\p{L}")
private val SPECIAL_REGEX = Regex("[^\\w\\s]")

/** Oltre questa soglia il testo non è un prezzo ma un refuso: meglio `null` di un Long a caso. */
private val MAX_PARSABLE_CENTS = BigDecimal.valueOf(1_000_000_000L)

/** Esito di una regola: il valore già normalizzato, oppure l'errore da mostrare sotto il campo. */
sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val error: ValidationError) : ValidationResult<Nothing>
}

/** Errori mostrabili; la copy italiana sta in `:core:designsystem` (`validationMessage`). */
enum class ValidationError {
    REQUIRED,
    NAME_LENGTH,
    NAME_CHARS,
    EMAIL_INVALID,
    PHONE_INVALID,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_NO_LETTER,
    PASSWORD_NO_DIGIT,
    PASSWORD_MISMATCH,
    TEXT_TOO_LONG,
    NOTE_TOO_LONG,
    DURATION_TOO_SHORT,
    DURATION_TOO_LONG,
    DURATION_STEP,
    PRICE_INVALID,
    PRICE_NEGATIVE,
    PRICE_TOO_HIGH,
}

/** `null` quando il valore è accettato: è quello che il form aggancia sotto il campo. */
fun <T> ValidationResult<T>.errorOrNull(): ValidationError? = when (this) {
    is ValidationResult.Valid -> null
    is ValidationResult.Invalid -> error
}

/** Il valore normalizzato, o `null` se la regola l'ha rifiutato. */
fun <T> ValidationResult<T>.valueOrNull(): T? = when (this) {
    is ValidationResult.Valid -> value
    is ValidationResult.Invalid -> null
}

val <T> ValidationResult<T>.isValid: Boolean get() = this is ValidationResult.Valid

/**
 * Porta un numero scritto a mano nel formato E.164 (`+393478124490`).
 * Accetta spazi, punti, trattini e parentesi, `00` al posto di `+`, e i numeri
 * italiani senza prefisso. Torna `null` se non ne esce un numero plausibile.
 */
fun normalizePhone(input: String): String? {
    val cleaned = input.replace(PHONE_SEPARATORS, "")
    if (cleaned.isEmpty()) return null
    val e164 = when {
        cleaned.startsWith("+") -> cleaned
        cleaned.startsWith("00") -> "+" + cleaned.substring(2)
        // Niente zero da togliere: in Italia il prefisso dei fissi lo tiene (+39 081…).
        else -> DEFAULT_PHONE_COUNTRY_CODE + cleaned
    }
    if (!E164_REGEX.matches(e164)) return null
    // I numeri italiani hanno da 8 a 11 cifre dopo il +39: fuori da lì è un refuso.
    if (e164.startsWith(DEFAULT_PHONE_COUNTRY_CODE)) {
        val digits = e164.substring(DEFAULT_PHONE_COUNTRY_CODE.length)
        if (digits.length < 8 || digits.length > 11) return null
    }
    return e164
}

/** "+393478124490" → "+39 347 812 4490": la forma leggibile mostrata nei campi e nelle schede. */
fun formatPhone(e164: String): String {
    if (!e164.startsWith(DEFAULT_PHONE_COUNTRY_CODE)) return e164
    val digits = e164.substring(DEFAULT_PHONE_COUNTRY_CODE.length)
    if (digits.length < 6) return e164
    val groups = if (digits.length >= 9) {
        listOf(digits.substring(0, 3), digits.substring(3, 6), digits.substring(6))
    } else {
        listOf(digits.substring(0, 3), digits.substring(3))
    }
    return "$DEFAULT_PHONE_COUNTRY_CODE ${groups.joinToString(" ")}"
}

/** Forza della password mostrata dal misuratore in registrazione e in cambio password. */
enum class PasswordStrength { DEBOLE, MEDIA, FORTE }

fun passwordStrength(password: String): PasswordStrength {
    var score = 0
    if (password.length >= PASSWORD_MIN) score++
    if (password.length >= 12) score++
    if (LOWERCASE_REGEX.containsMatchIn(password) && UPPERCASE_REGEX.containsMatchIn(password)) score++
    if (DIGIT_REGEX.containsMatchIn(password)) score++
    if (SPECIAL_REGEX.containsMatchIn(password)) score++
    return when {
        score <= 2 -> PasswordStrength.DEBOLE
        score == 3 -> PasswordStrength.MEDIA
        else -> PasswordStrength.FORTE
    }
}

/** Nome o cognome di una persona: trim, da 2 a 50 caratteri, solo lettere. */
fun validateName(input: String): ValidationResult<String> {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
        trimmed.length < NAME_MIN || trimmed.length > NAME_MAX ->
            ValidationResult.Invalid(ValidationError.NAME_LENGTH)
        !NAME_REGEX.matches(trimmed) -> ValidationResult.Invalid(ValidationError.NAME_CHARS)
        else -> ValidationResult.Valid(trimmed)
    }
}

/** Testo libero obbligatorio (nome servizio, nome campagna, titolo push): trim + limite. */
fun validateRequiredText(input: String, max: Int = TEXT_MAX): ValidationResult<String> {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
        trimmed.length > max -> ValidationResult.Invalid(ValidationError.TEXT_TOO_LONG)
        else -> ValidationResult.Valid(trimmed)
    }
}

/** Email: trim + minuscole, come la salva il backend. */
fun validateEmail(input: String): ValidationResult<String> {
    val normalized = input.trim().lowercase()
    return when {
        normalized.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
        normalized.length > EMAIL_MAX || !EMAIL_REGEX.matches(normalized) ->
            ValidationResult.Invalid(ValidationError.EMAIL_INVALID)
        else -> ValidationResult.Valid(normalized)
    }
}

/** Telefono: il valore accettato è sempre in E.164. */
fun validatePhone(input: String): ValidationResult<String> {
    if (input.isBlank()) return ValidationResult.Invalid(ValidationError.REQUIRED)
    val e164 = normalizePhone(input) ?: return ValidationResult.Invalid(ValidationError.PHONE_INVALID)
    return ValidationResult.Valid(e164)
}

/** Almeno una lettera e una cifra: la stessa regola del misuratore nel form. */
fun validatePassword(input: String): ValidationResult<String> = when {
    input.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
    input.length < PASSWORD_MIN -> ValidationResult.Invalid(ValidationError.PASSWORD_TOO_SHORT)
    input.length > PASSWORD_MAX -> ValidationResult.Invalid(ValidationError.PASSWORD_TOO_LONG)
    !LETTER_REGEX.containsMatchIn(input) -> ValidationResult.Invalid(ValidationError.PASSWORD_NO_LETTER)
    !DIGIT_REGEX.containsMatchIn(input) -> ValidationResult.Invalid(ValidationError.PASSWORD_NO_DIGIT)
    else -> ValidationResult.Valid(input)
}

fun validatePasswordConfirm(password: String, confirm: String): ValidationResult<String> = when {
    confirm.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
    confirm != password -> ValidationResult.Invalid(ValidationError.PASSWORD_MISMATCH)
    else -> ValidationResult.Valid(confirm)
}

/** Nota dell'appuntamento: facoltativa, al massimo 200 caratteri, vuota diventa `null`. */
fun validateNote(input: String): ValidationResult<String?> = when {
    input.length > NOTE_MAX -> ValidationResult.Invalid(ValidationError.NOTE_TOO_LONG)
    else -> ValidationResult.Valid(input.trim().ifEmpty { null })
}

/** Tiene una durata dentro i limiti, per gli stepper +/- dell'editor servizi. */
fun clampDuration(minutes: Int): Int = minutes.coerceIn(DURATION_MIN_MINUTES, DURATION_MAX_MINUTES)

/** Durate a passi di 5 minuti: la griglia degli slot è di 30. */
fun validateDuration(minutes: Int): ValidationResult<Int> = when {
    minutes < DURATION_MIN_MINUTES -> ValidationResult.Invalid(ValidationError.DURATION_TOO_SHORT)
    minutes > DURATION_MAX_MINUTES -> ValidationResult.Invalid(ValidationError.DURATION_TOO_LONG)
    minutes % DURATION_STEP_MINUTES != 0 -> ValidationResult.Invalid(ValidationError.DURATION_STEP)
    else -> ValidationResult.Valid(minutes)
}

/** Prezzi in centesimi, da 0 a 1.000 €. */
fun validatePrice(cents: Long): ValidationResult<Long> = when {
    cents < 0 -> ValidationResult.Invalid(ValidationError.PRICE_NEGATIVE)
    cents > PRICE_MAX_CENTS -> ValidationResult.Invalid(ValidationError.PRICE_TOO_HIGH)
    else -> ValidationResult.Valid(cents)
}

/**
 * Euro scritti a mano → centesimi. Si arrotonda, non si tronca: "19,99" deve
 * fare 1999 e non 1998 come faceva la vecchia conversione via `Double`.
 * [BigDecimal] toglie di mezzo l'errore di rappresentazione binaria, e un testo
 * malformato come "1.2.3" torna `null` invece di diventare 0,00 € in silenzio.
 */
fun parsePriceToCents(euros: String): Long? {
    val decimal = euros.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    val cents = decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
    if (cents.abs() > MAX_PARSABLE_CENTS) return null
    return cents.toLong()
}

/**
 * Il campo prezzo parla in euro: qui si controlla il testo e si ricade sulla
 * regola in centesimi, la stessa del backend.
 */
fun validatePriceInput(euros: String): ValidationResult<Long> {
    if (euros.isBlank()) return ValidationResult.Invalid(ValidationError.REQUIRED)
    val cents = parsePriceToCents(euros) ?: return ValidationResult.Invalid(ValidationError.PRICE_INVALID)
    return validatePrice(cents)
}

/** 1999 → "19,99", 2200 → "22": il testo con cui si apre il campo prezzo. */
fun formatCentsAsInput(cents: Long): String {
    val euros = cents / 100
    val rest = (cents % 100).toInt()
    return if (rest == 0) euros.toString() else "$euros,${rest.toString().padStart(2, '0')}"
}

/**
 * Tiene nel campo prezzo solo ciò che può diventare un prezzo: cifre, un solo
 * separatore decimale (normalizzato a virgola) e al massimo due decimali.
 */
fun sanitizePriceInput(raw: String): String {
    val builder = StringBuilder()
    var decimals = -1
    for (char in raw) {
        when {
            char.isDigit() && decimals < 0 -> if (builder.length < 4) builder.append(char)
            char.isDigit() && decimals < 2 -> {
                builder.append(char)
                decimals++
            }
            (char == ',' || char == '.') && decimals < 0 -> {
                builder.append(if (builder.isEmpty()) "0," else ",")
                decimals = 0
            }
            else -> Unit
        }
    }
    return builder.toString()
}
