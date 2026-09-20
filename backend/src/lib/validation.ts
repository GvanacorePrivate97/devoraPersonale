import { z } from 'zod'
import { validation } from './errors.js'

/**
 * Regole di validazione dell'intero prodotto: qui e basta.
 * Le due app le ripetono parola per parola (Android
 * `core/common/Validation.kt`, iOS `Core/Common/Validation.swift`) per dare
 * l'errore subito nel form; il server resta l'ultima parola e non si fida mai
 * di ciò che arriva. Cambiare una regola qui vuol dire cambiarla lì.
 */

export const NAME_MIN = 2
export const NAME_MAX = 50
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 72
export const NOTE_MAX = 200
export const CAMPAIGN_BODY_MAX = 140

/** Lettere (anche accentate), spazi, apostrofi e trattini: "D'Amico", "De Vito". */
const NAME_RE = /^[\p{L}][\p{L}'\-. ]*[\p{L}.]$/u
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

/** Prefisso usato quando il numero arriva senza indicativo internazionale. */
export const DEFAULT_PHONE_COUNTRY_CODE = '+39'

/**
 * Porta un numero scritto a mano nel formato E.164 (`+393478124490`).
 * Accetta spazi, punti, trattini e parentesi, `00` al posto di `+`, e i numeri
 * italiani senza prefisso. Torna `null` se non ne esce un numero plausibile.
 */
export function normalizePhone(input: string): string | null {
  const cleaned = input.replace(/[\s.\-() ]/g, '')
  if (cleaned.length === 0) return null
  let e164: string
  if (cleaned.startsWith('+')) e164 = cleaned
  else if (cleaned.startsWith('00')) e164 = `+${cleaned.slice(2)}`
  // Niente zero da togliere: in Italia il prefisso dei fissi lo tiene (+39 081…).
  else e164 = `${DEFAULT_PHONE_COUNTRY_CODE}${cleaned}`
  if (!/^\+[1-9]\d{7,14}$/.test(e164)) return null
  // I numeri italiani hanno da 8 a 11 cifre dopo il +39: fuori da lì è un refuso.
  if (e164.startsWith('+39')) {
    const digits = e164.slice(3)
    if (digits.length < 8 || digits.length > 11) return null
  }
  return e164
}

/** Forza della password mostrata dal misuratore in registrazione. */
export type PasswordStrength = 'DEBOLE' | 'MEDIA' | 'FORTE'

export function passwordStrength(password: string): PasswordStrength {
  let score = 0
  if (password.length >= PASSWORD_MIN) score++
  if (password.length >= 12) score++
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++
  if (/\d/.test(password)) score++
  if (/[^\w\s]/.test(password)) score++
  if (score <= 2) return 'DEBOLE'
  if (score === 3) return 'MEDIA'
  return 'FORTE'
}

export const nameSchema = z
  .string()
  .transform((v) => v.trim())
  .refine((v) => v.length >= NAME_MIN && v.length <= NAME_MAX, `Da ${NAME_MIN} a ${NAME_MAX} caratteri`)
  .refine((v) => NAME_RE.test(v), 'Sono ammesse solo lettere')

export const LABEL_MIN = 2
export const LABEL_MAX = 60

/**
 * Etichette scritte dal salone: nomi di servizi ("Shampoo + taglio"), campagne,
 * titoli. A differenza dei nomi di persona ammettono numeri e simboli: l'unica
 * regola è che dicano qualcosa e non siano un tema.
 */
export const labelSchema = z
  .string()
  .transform((v) => v.trim())
  .refine((v) => v.length >= LABEL_MIN, 'Campo obbligatorio')
  .refine((v) => v.length <= LABEL_MAX, `Massimo ${LABEL_MAX} caratteri`)

export const emailSchema = z
  .string()
  .transform((v) => v.trim().toLowerCase())
  .refine((v) => v.length <= 254 && EMAIL_RE.test(v), 'Indirizzo email non valido')

export const phoneSchema = z
  .string()
  .transform((v) => normalizePhone(v))
  .refine((v): v is string => v !== null, 'Numero di telefono non valido')

/** Almeno una lettera e una cifra: la stessa regola del misuratore nel form. */
export const passwordSchema = z
  .string()
  .min(PASSWORD_MIN, `Almeno ${PASSWORD_MIN} caratteri`)
  .max(PASSWORD_MAX, `Massimo ${PASSWORD_MAX} caratteri`)
  .refine((v) => /[\p{L}]/u.test(v), 'Deve contenere almeno una lettera')
  .refine((v) => /\d/.test(v), 'Deve contenere almeno un numero')

export const noteSchema = z
  .string()
  .max(NOTE_MAX, `Massimo ${NOTE_MAX} caratteri`)
  .transform((v) => (v.trim().length === 0 ? null : v.trim()))
  .nullable()

/** Durate a passi di 5 minuti: la griglia degli slot è di 30. */
export const durationSchema = z
  .number()
  .int('Durata non valida')
  .min(5, 'Almeno 5 minuti')
  .max(480, 'Al massimo 8 ore')
  .refine((v) => v % 5 === 0, 'Usa passi di 5 minuti')

/** Prezzi in centesimi, da 0 a 1.000 €. */
export const priceSchema = z
  .number()
  .int('Prezzo non valido')
  .min(0, 'Il prezzo non può essere negativo')
  .max(100_000, 'Prezzo troppo alto')

export const idSchema = z.string().uuid('Identificativo non valido')
export const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Data non valida')
export const timeSchema = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Orario non valido')

/**
 * Converte l'errore di uno schema zod nell'errore di validazione dell'API,
 * che porta con sé il campo: è quello che le app agganciano sotto il campo.
 */
export function toValidationError(error: z.ZodError): ReturnType<typeof validation> {
  const issue = error.issues[0]
  const field = issue?.path.join('.') || 'form'
  return validation(field, issue?.message ?? 'Valore non valido')
}
