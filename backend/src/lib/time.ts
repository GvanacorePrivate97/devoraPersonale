import { env } from '../config/env.js'

/**
 * Il salone ragiona in orario da muro (Europe/Rome), il database in istanti
 * UTC. Qui stanno le due conversioni, senza librerie: `Intl` conosce già l'ora
 * legale, quindi l'ultima domenica di marzo non ci fa sbagliare di un'ora.
 */

export const SALON_TZ = env.SALON_TIMEZONE

/** Data locale `YYYY-MM-DD`. */
export type LocalDate = string
/** Orario locale `HH:mm`. */
export type LocalTime = string

const partsFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: SALON_TZ,
  hour12: false,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

function partsOf(instant: Date): Record<string, number> {
  const out: Record<string, number> = {}
  for (const part of partsFormatter.formatToParts(instant)) {
    if (part.type !== 'literal') out[part.type] = Number(part.value)
  }
  return out
}

/** Scarto del fuso (ms) attivo in quell'istante: cambia con l'ora legale. */
function offsetMs(instant: Date): number {
  const p = partsOf(instant)
  const asUtc = Date.UTC(p.year!, p.month! - 1, p.day!, p.hour! % 24, p.minute!, p.second!)
  return asUtc - instant.getTime()
}

/** Da orario da muro del salone all'istante UTC da salvare. */
export function zonedToInstant(date: LocalDate, time: LocalTime): Date {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number]
  const [hh, mm] = time.split(':').map(Number) as [number, number]
  const guess = Date.UTC(y, m - 1, d, hh, mm)
  // Due passaggi: il primo usa lo scarto sbagliato a cavallo del cambio d'ora.
  let instant = guess - offsetMs(new Date(guess))
  instant = guess - offsetMs(new Date(instant))
  return new Date(instant)
}

/** Dall'istante salvato all'orario da muro del salone. */
export function instantToZoned(instant: Date): { date: LocalDate; time: LocalTime; dayOfWeek: number } {
  const p = partsOf(instant)
  const date = `${String(p.year).padStart(4, '0')}-${String(p.month).padStart(2, '0')}-${String(p.day).padStart(2, '0')}`
  const hour = p.hour! % 24
  const time = `${String(hour).padStart(2, '0')}:${String(p.minute).padStart(2, '0')}`
  return { date, time, dayOfWeek: isoDayOfWeek(date) }
}

/** Giorno della settimana ISO: 1 lunedì … 7 domenica. */
export function isoDayOfWeek(date: LocalDate): number {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number]
  const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
  return day === 0 ? 7 : day
}

export function todayInSalon(now: Date = new Date()): LocalDate {
  return instantToZoned(now).date
}

/** Minuti da mezzanotte, la forma con cui il motore degli slot fa i conti. */
export function minutesOfTime(time: LocalTime): number {
  const [hh, mm] = time.split(':').map(Number) as [number, number]
  return hh * 60 + mm
}

export function timeOfMinutes(minutes: number): LocalTime {
  const hh = Math.floor(minutes / 60)
  const mm = minutes % 60
  return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`
}

export function addDays(date: LocalDate, days: number): LocalDate {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number]
  const next = new Date(Date.UTC(y, m - 1, d + days))
  return next.toISOString().slice(0, 10)
}

export function daysBetween(from: LocalDate, to: LocalDate): number {
  const [y1, m1, d1] = from.split('-').map(Number) as [number, number, number]
  const [y2, m2, d2] = to.split('-').map(Number) as [number, number, number]
  return Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86_400_000)
}

export function eachDate(from: LocalDate, to: LocalDate): LocalDate[] {
  const out: LocalDate[] = []
  for (let d = from; daysBetween(d, to) >= 0; d = addDays(d, 1)) out.push(d)
  return out
}

/** Lunedì della settimana che contiene `date`. */
export function startOfWeek(date: LocalDate): LocalDate {
  return addDays(date, -(isoDayOfWeek(date) - 1))
}
