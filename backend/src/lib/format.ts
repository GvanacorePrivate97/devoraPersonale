import type { LocalDate, LocalTime } from './time.js'

/**
 * Testi italiani delle notifiche. Stanno sul server perché è il server a
 * generarle: le app mostrano quello che arriva, senza ricostruirlo.
 */

const GIORNI = ['lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica']
const MESI = [
  'gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno',
  'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre',
]

export function formatDateLong(date: LocalDate): string {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number]
  const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
  return `${GIORNI[weekday === 0 ? 6 : weekday - 1]} ${d} ${MESI[m - 1]}`
}

export function formatDateShort(date: LocalDate): string {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number]
  const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
  return `${GIORNI[weekday === 0 ? 6 : weekday - 1]!.slice(0, 3)} ${d} ${MESI[m - 1]!.slice(0, 3)}`
}

export function formatTime(time: LocalTime): string {
  return time
}

export function formatPrice(cents: number): string {
  return `€ ${(cents / 100).toFixed(2).replace('.', ',')}`
}

export function formatDuration(minutes: number): string {
  return `${minutes} min`
}

export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? fullName
}
