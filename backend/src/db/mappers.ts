import { instantToZoned, timeOfMinutes, minutesOfTime, type LocalDate, type LocalTime } from '../lib/time.js'

/**
 * Forme JSON che viaggiano verso le app. Sono deliberatamente piatte e in
 * ISO-8601: `2026-09-20` per le date, `10:30` per gli orari, istanti completi
 * per i timestamp. Android e iOS hanno lo stesso identico contratto.
 */

export type ServiceDto = {
  id: string
  name: string
  durationMinutes: number
  priceCents: number
  description: string | null
  featured: boolean
  active: boolean
}

export type TimeRangeDto = { start: LocalTime; end: LocalTime }

export type OperatorDto = {
  id: string
  name: string
  title: string
  bio: string
  specialties: string[]
  isOwner: boolean
  active: boolean
  /** Chiavi "1".."7", ISO: 1 lunedì … 7 domenica. */
  weeklyHours: Record<string, TimeRangeDto[]>
  serviceIds: string[]
}

export type SalonDto = {
  name: string
  address: string
  city: string
  phone: string | null
  timezone: string
  weeklyHours: Record<string, TimeRangeDto[]>
}

export type AppointmentDto = {
  id: string
  clientId: string
  operatorId: string
  serviceIds: string[]
  date: LocalDate
  time: LocalTime
  startsAt: string
  durationMinutes: number
  totalPriceCents: number
  status: string
  channel: string
  noteForOperator: string | null
  cancelledBy: string | null
}

export type WaitlistEntryDto = {
  id: string
  clientId: string
  date: LocalDate
  time: LocalTime | null
  operatorId: string | null
  serviceIds: string[]
  durationMinutes: number
  totalPriceCents: number
  position: number
  status: string
}

export type ClientDto = {
  id: string
  firstName: string
  lastName: string
  phone: string
  email: string | null
  customerSince: LocalDate
  visitCount: number
  lifetimeSpendCents: number
  noShowCount: number
  lastVisit: LocalDate | null
  preferredServiceIds: string[]
  preferredOperatorId: string | null
  marketingOptIn: boolean
}

export type UserDto = {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string
  role: 'CLIENT' | 'STAFF' | 'OWNER'
  memberSince: LocalDate
  visitCount: number
  avatarUrl: string | null
  clientId: string | null
  operatorId: string | null
}

export type NotificationDto = {
  id: string
  title: string
  body: string
  kind: string
  payload: Record<string, unknown>
  at: string
  read: boolean
}

/** `time` di Postgres arriva come `HH:MM:SS`: alle app serve `HH:MM`. */
export function toLocalTime(value: string): LocalTime {
  return value.slice(0, 5)
}

export function rangeDto(start: string, end: string): TimeRangeDto {
  return { start: toLocalTime(start), end: toLocalTime(end) }
}

export function minutesRange(start: string, end: string): { start: number; end: number } {
  return { start: minutesOfTime(toLocalTime(start)), end: minutesOfTime(toLocalTime(end)) }
}

export { instantToZoned, timeOfMinutes }
