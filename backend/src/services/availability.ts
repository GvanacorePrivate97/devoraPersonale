import type { Db } from '../db/pool.js'
import { query } from '../db/pool.js'
import {
  addDays, daysBetween, eachDate, instantToZoned, isoDayOfWeek, minutesOfTime,
  timeOfMinutes, todayInSalon, type LocalDate, type LocalTime,
} from '../lib/time.js'
import { canPlace, slotsFor, unionSlots, type Range } from '../lib/slots.js'
import { minutesRange } from '../db/mappers.js'

/**
 * Disponibilità: una sola lettura del giorno (o dell'intervallo) e poi i conti
 * in memoria con il motore puro. Le app non calcolano più niente: chiedono e
 * ricevono orari già validi.
 */

export type OperatorContext = {
  id: string
  /** Turni settimanali: chiave = giorno ISO 1..7. */
  hours: Map<number, Range[]>
  serviceIds: Set<string>
}

export type AvailabilityContext = {
  salonHours: Map<number, Range[]>
  operators: OperatorContext[]
  /** Occupato per operatore e giorno: appuntamenti attivi + blocchi. */
  busy: Map<string, Range[]>
  holidays: Map<string, { from: LocalDate; to: LocalDate }[]>
  today: LocalDate
  nowMinutes: number
}

const busyKey = (operatorId: string, date: LocalDate) => `${operatorId}|${date}`

/** Carica tutto ciò che serve per calcolare gli slot fra due date, estremi inclusi. */
export async function loadContext(
  db: Db,
  from: LocalDate,
  to: LocalDate,
  options: { ignoreAppointmentId?: string } = {},
): Promise<AvailabilityContext> {
  // Una query dopo l'altra, non in parallelo: dentro una transazione `db` è
  // una sola connessione, che le accoderebbe comunque (e da pg 9 sarebbe un
  // errore). Sono sette letture piccole su indici.
  const salonRows = await query<{ day_of_week: number; starts_at: string; ends_at: string }>(
    db, 'select day_of_week, starts_at, ends_at from salon_hours',
  )
  const operatorRows = await query<{ id: string }>(db, 'select id from operators where active')
  const hourRows = await query<{ operator_id: string; day_of_week: number; starts_at: string; ends_at: string }>(
    db, 'select operator_id, day_of_week, starts_at, ends_at from operator_hours',
  )
  const serviceRows = await query<{ operator_id: string; service_id: string }>(
    db, 'select operator_id, service_id from operator_services',
  )
  const appointmentRows = await query<{ operator_id: string; starts_at: Date; ends_at: Date }>(
    db,
    `select operator_id, starts_at, ends_at
       from appointments
      where status in ('CONFIRMED', 'IN_PROGRESS')
        and starts_at < ($2::date + 1)
        and ends_at   > $1::date
        and ($3::uuid is null or id <> $3::uuid)`,
    [from, to, options.ignoreAppointmentId ?? null],
  )
  const blockRows = await query<{ operator_id: string; on_date: string; starts_at: string; ends_at: string }>(
    db,
    'select operator_id, on_date, starts_at, ends_at from time_blocks where on_date between $1 and $2',
    [from, to],
  )
  const holidayRows = await query<{ operator_id: string; from_date: string; to_date: string }>(
    db,
    'select operator_id, from_date, to_date from holidays where from_date <= $2 and to_date >= $1',
    [from, to],
  )

  const salonHours = new Map<number, Range[]>()
  for (const row of salonRows) {
    const list = salonHours.get(row.day_of_week) ?? []
    list.push(minutesRange(row.starts_at, row.ends_at))
    salonHours.set(row.day_of_week, list)
  }

  const hours = new Map<string, Map<number, Range[]>>()
  for (const row of hourRows) {
    const perOperator = hours.get(row.operator_id) ?? new Map<number, Range[]>()
    const list = perOperator.get(row.day_of_week) ?? []
    list.push(minutesRange(row.starts_at, row.ends_at))
    perOperator.set(row.day_of_week, list)
    hours.set(row.operator_id, perOperator)
  }

  const services = new Map<string, Set<string>>()
  for (const row of serviceRows) {
    const set = services.get(row.operator_id) ?? new Set<string>()
    set.add(row.service_id)
    services.set(row.operator_id, set)
  }

  const busy = new Map<string, Range[]>()
  const pushBusy = (operatorId: string, date: LocalDate, range: Range) => {
    const key = busyKey(operatorId, date)
    const list = busy.get(key) ?? []
    list.push(range)
    busy.set(key, list)
  }
  for (const row of appointmentRows) {
    const start = instantToZoned(row.starts_at)
    const end = instantToZoned(row.ends_at)
    const startMinutes = minutesOfTime(start.time)
    // Un appuntamento che scavalca la mezzanotte non esiste in salone, ma se
    // capitasse si occupa comunque fino a fine giornata.
    const endMinutes = end.date === start.date ? minutesOfTime(end.time) : 24 * 60
    pushBusy(row.operator_id, start.date, { start: startMinutes, end: endMinutes })
  }
  for (const row of blockRows) {
    pushBusy(row.operator_id, row.on_date, minutesRange(row.starts_at, row.ends_at))
  }

  const holidays = new Map<string, { from: LocalDate; to: LocalDate }[]>()
  for (const row of holidayRows) {
    const list = holidays.get(row.operator_id) ?? []
    list.push({ from: row.from_date, to: row.to_date })
    holidays.set(row.operator_id, list)
  }

  const now = new Date()
  const zonedNow = instantToZoned(now)

  return {
    salonHours,
    operators: operatorRows.map((row) => ({
      id: row.id,
      hours: hours.get(row.id) ?? new Map(),
      serviceIds: services.get(row.id) ?? new Set(),
    })),
    busy,
    holidays,
    today: zonedNow.date,
    nowMinutes: minutesOfTime(zonedNow.time),
  }
}

export function isOnHoliday(ctx: AvailabilityContext, operatorId: string, date: LocalDate): boolean {
  return (ctx.holidays.get(operatorId) ?? []).some((h) => h.from <= date && date <= h.to)
}

/** Operatori che possono eseguire **tutti** i servizi scelti. */
export function eligibleOperators(
  ctx: AvailabilityContext,
  operatorId: string | null,
  serviceIds: string[],
): OperatorContext[] {
  return ctx.operators.filter(
    (op) => (operatorId === null || op.id === operatorId) && serviceIds.every((id) => op.serviceIds.has(id)),
  )
}

export function slotsForOperator(
  ctx: AvailabilityContext,
  operator: OperatorContext,
  date: LocalDate,
  durationMinutes: number,
): number[] {
  const dow = isoDayOfWeek(date)
  return slotsFor({
    operatorHours: operator.hours.get(dow) ?? [],
    salonHours: ctx.salonHours.get(dow) ?? [],
    busy: ctx.busy.get(busyKey(operator.id, date)) ?? [],
    durationMinutes,
    onHoliday: isOnHoliday(ctx, operator.id, date),
    isPast: date < ctx.today,
    nowMinutes: date === ctx.today ? ctx.nowMinutes : null,
  })
}

/** Orari proponibili quel giorno: unione degli operatori abilitati. */
export function daySlots(
  ctx: AvailabilityContext,
  operatorId: string | null,
  serviceIds: string[],
  date: LocalDate,
  durationMinutes: number,
): LocalTime[] {
  const operators = eligibleOperators(ctx, operatorId, serviceIds)
  const slots = unionSlots(operators.map((op) => slotsForOperator(ctx, op, date, durationMinutes)))
  return slots.map(timeOfMinutes)
}

/** Giorni con almeno un orario libero, e giorni pieni (capienza esaurita). */
export function daysOverview(
  ctx: AvailabilityContext,
  operatorId: string | null,
  serviceIds: string[],
  from: LocalDate,
  to: LocalDate,
  durationMinutes: number,
): { available: LocalDate[]; fullyBooked: LocalDate[] } {
  const operators = eligibleOperators(ctx, operatorId, serviceIds)
  const available: LocalDate[] = []
  const fullyBooked: LocalDate[] = []

  for (const date of eachDate(from, to)) {
    const withBookings = unionSlots(operators.map((op) => slotsForOperator(ctx, op, date, durationMinutes)))
    if (withBookings.length > 0) {
      available.push(date)
      continue
    }
    // "Pieno" vuol dire che il giorno avrebbe capienza ma è tutta occupata:
    // un giorno di chiusura o di ferie non è pieno, è chiuso.
    const dow = isoDayOfWeek(date)
    const wouldHaveSlots = operators.some(
      (op) =>
        slotsFor({
          operatorHours: op.hours.get(dow) ?? [],
          salonHours: ctx.salonHours.get(dow) ?? [],
          busy: [],
          durationMinutes,
          onHoliday: isOnHoliday(ctx, op.id, date),
          isPast: date < ctx.today,
          nowMinutes: date === ctx.today ? ctx.nowMinutes : null,
        }).length > 0,
    )
    if (wouldHaveSlots) fullyBooked.push(date)
  }
  return { available, fullyBooked }
}

/**
 * Chi può prendere quell'orario: il primo operatore abilitato che risulta
 * libero. Con "qualsiasi operatore" decide il server, non il cliente.
 */
export function resolveOperatorFor(
  ctx: AvailabilityContext,
  operatorId: string | null,
  serviceIds: string[],
  date: LocalDate,
  time: LocalTime,
  durationMinutes: number,
): string | null {
  const wanted = minutesOfTime(time)
  for (const op of eligibleOperators(ctx, operatorId, serviceIds)) {
    if (slotsForOperator(ctx, op, date, durationMinutes).includes(wanted)) return op.id
  }
  return null
}

/** Spostamento a mano (titolare e operatore): niente griglia, niente preavviso. */
export function canPlaceAt(
  ctx: AvailabilityContext,
  operatorId: string,
  date: LocalDate,
  time: LocalTime,
  durationMinutes: number,
): boolean {
  const operator = ctx.operators.find((op) => op.id === operatorId)
  if (!operator) return false
  const dow = isoDayOfWeek(date)
  return canPlace({
    operatorHours: operator.hours.get(dow) ?? [],
    salonHours: ctx.salonHours.get(dow) ?? [],
    busy: ctx.busy.get(busyKey(operatorId, date)) ?? [],
    durationMinutes,
    onHoliday: isOnHoliday(ctx, operatorId, date),
    start: minutesOfTime(time),
  })
}

export { addDays, daysBetween, todayInSalon }
