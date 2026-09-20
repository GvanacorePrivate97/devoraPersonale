import type { FastifyBaseLogger } from 'fastify'
import type { Db } from '../../db/pool.js'
import { pool, query, queryOne, transaction, pgCode, PG } from '../../db/pool.js'
import type { AppointmentDto, WaitlistEntryDto } from '../../db/mappers.js'
import { addDays, instantToZoned, zonedToInstant, minutesOfTime, todayInSalon, type LocalDate, type LocalTime } from '../../lib/time.js'
import { ApiError, forbidden, notFound, slotTaken, validation } from '../../lib/errors.js'
import { formatDateLong, firstName } from '../../lib/format.js'
import {
  canPlaceAt, daySlots, daysOverview, eligibleOperators, loadContext, resolveOperatorFor, slotsForOperator,
} from '../../services/availability.js'
import { clientPrefs, notify, salonNotificationSettings, userIdForClient, userIdForOperator } from '../../services/notifications.js'

/**
 * Prenotazioni. Tutto ciò che tocca l'agenda passa da qui, dentro una
 * transazione: il vincolo di non sovrapposizione del database è l'ultima
 * difesa contro due clienti che confermano lo stesso orario nello stesso
 * istante.
 */

/** Ore entro cui il cliente non può più disdire da solo (decisione di prodotto). */
export const CLIENT_CANCELLATION_WINDOW_HOURS = 2

const APPOINTMENT_SELECT = `
  select a.id, a.client_id, a.operator_id, a.starts_at, a.duration_minutes, a.total_price_cents,
         a.status, a.channel, a.note_for_operator, a.cancelled_by,
         coalesce(
           (select array_agg(s.service_id order by s.position) from appointment_services s where s.appointment_id = a.id),
           '{}'
         ) as service_ids
    from appointments a`

type AppointmentRow = {
  id: string; client_id: string; operator_id: string; starts_at: Date
  duration_minutes: number; total_price_cents: number; status: string; channel: string
  note_for_operator: string | null; cancelled_by: string | null; service_ids: string[]
}

function toDto(row: AppointmentRow): AppointmentDto {
  const zoned = instantToZoned(row.starts_at)
  return {
    id: row.id,
    clientId: row.client_id,
    operatorId: row.operator_id,
    serviceIds: row.service_ids,
    date: zoned.date,
    time: zoned.time,
    startsAt: row.starts_at.toISOString(),
    durationMinutes: row.duration_minutes,
    totalPriceCents: row.total_price_cents,
    status: row.status,
    channel: row.channel,
    noteForOperator: row.note_for_operator,
    cancelledBy: row.cancelled_by,
  }
}

export async function appointmentById(db: Db, id: string): Promise<AppointmentDto | null> {
  const row = await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1`, [id])
  return row ? toDto(row) : null
}

export async function appointmentsForClient(db: Db, clientId: string): Promise<AppointmentDto[]> {
  const rows = await query<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.client_id = $1 order by a.starts_at`, [clientId])
  return rows.map(toDto)
}

export async function appointmentsForOperatorDay(db: Db, operatorId: string, date: LocalDate): Promise<AppointmentDto[]> {
  // I confini del giorno si calcolano nel fuso del salone, non in UTC.
  const rows = await query<AppointmentRow>(
    db,
    `${APPOINTMENT_SELECT}
      where a.operator_id = $1 and a.starts_at >= $2::timestamptz and a.starts_at < $3::timestamptz
      order by a.starts_at`,
    [operatorId, zonedToInstant(date, '00:00'), zonedToInstant(addDays(date, 1), '00:00')],
  )
  return rows.map(toDto)
}

export async function appointmentsBetween(db: Db, from: LocalDate, to: LocalDate, operatorId?: string): Promise<AppointmentDto[]> {
  const rows = await query<AppointmentRow>(
    db,
    `${APPOINTMENT_SELECT}
      where a.starts_at >= $1::timestamptz and a.starts_at < $2::timestamptz
        and ($3::uuid is null or a.operator_id = $3::uuid)
      order by a.starts_at`,
    [zonedToInstant(from, '00:00'), zonedToInstant(addDays(to, 1), '00:00'), operatorId ?? null],
  )
  return rows.map(toDto)
}

/** Durata e prezzo del carrello, fotografati sul listino di adesso. */
async function priceCart(db: Db, serviceIds: string[]) {
  if (serviceIds.length === 0) throw validation('services', 'Scegli almeno un servizio')
  const rows = await query<{ id: string; name: string; duration_minutes: number; price_cents: number }>(
    db, 'select id, name, duration_minutes, price_cents from services where id = any($1::uuid[]) and active', [serviceIds],
  )
  const byId = new Map(rows.map((r) => [r.id, r]))
  const items = serviceIds.map((id) => {
    const row = byId.get(id)
    if (!row) throw validation('services', 'Uno dei servizi scelti non è più disponibile')
    return row
  })
  return {
    items,
    durationMinutes: items.reduce((sum, i) => sum + i.duration_minutes, 0),
    totalPriceCents: items.reduce((sum, i) => sum + i.price_cents, 0),
  }
}

export type BookInput = {
  clientId: string
  operatorId: string | null
  serviceIds: string[]
  date: LocalDate
  time: LocalTime
  noteForOperator?: string | null
  channel?: 'APP' | 'PHONE' | 'WALK_IN'
  createdByUserId?: string | null
  /**
   * "Modifica": l'appuntamento che questo sostituisce. Nella stessa transazione
   * il vecchio si annulla e il nuovo si inserisce, così il posto del vecchio
   * torna libero per il nuovo (lo stesso orario si può tenere) e, se il nuovo
   * non entra, il vecchio resta com'era.
   */
  replaces?: { appointmentId: string; by: 'CLIENT' | 'SALON' } | null
}

/**
 * Quante volte ritentare quando il posto sfuma sotto le mani. Serve a
 * "qualsiasi operatore": se il collega scelto viene occupato mentre scriviamo,
 * al giro dopo se ne cerca un altro invece di dare buca al cliente.
 */
const BOOK_ATTEMPTS = 3

export async function book(input: BookInput, log?: FastifyBaseLogger): Promise<AppointmentDto> {
  let attempt = 0
  let appointment: AppointmentDto
  let replacedDate: LocalDate | null = null
  for (;;) {
    attempt++
    try {
      const result = await bookOnce(input)
      appointment = result.appointment
      replacedDate = result.replacedDate
      break
    } catch (error) {
      const retryable = error instanceof ApiError && error.code === 'SLOT_NO_LONGER_AVAILABLE'
      // Con un operatore scelto non c'è alternativa da cercare: l'orario è preso.
      if (!retryable || input.operatorId !== null || attempt >= BOOK_ATTEMPTS) throw error
    }
  }

  await announceBooking(appointment, log)
  // Se la modifica ha spostato l'appuntamento, il posto lasciato può servire a
  // chi è in lista d'attesa quel giorno.
  if (input.replaces && replacedDate) await notifyWaitlist(replacedDate, log)
  return appointment
}

async function bookOnce(input: BookInput): Promise<{ appointment: AppointmentDto; replacedDate: LocalDate | null }> {
  return transaction(async (db) => {
    let replacedDate: LocalDate | null = null
    if (input.replaces) {
      const old = await queryOne<AppointmentRow>(
        db, `${APPOINTMENT_SELECT} where a.id = $1 for update`, [input.replaces.appointmentId],
      )
      if (!old) throw notFound('Appuntamento')
      const previous = toDto(old)
      if (previous.clientId !== input.clientId) throw validation('clientId', 'In modifica il cliente non cambia')
      if (previous.status !== 'CONFIRMED' && previous.status !== 'IN_PROGRESS') {
        throw validation('status', 'Si modificano solo gli appuntamenti in programma')
      }
      // Modificare vuol dire anche disdire il vecchio: per il cliente vale la
      // stessa finestra dell'annullamento.
      if (input.replaces.by === 'CLIENT') {
        const hoursToStart = (old.starts_at.getTime() - Date.now()) / 3_600_000
        if (hoursToStart < CLIENT_CANCELLATION_WINDOW_HOURS) {
          throw forbidden(`Mancano meno di ${CLIENT_CANCELLATION_WINDOW_HOURS} ore: chiama il salone per modificare`)
        }
      }
      await query(
        db,
        `update appointments set status = 'CANCELLED', cancelled_by = $2, cancelled_at = now() where id = $1`,
        [previous.id, input.replaces.by],
      )
      replacedDate = previous.date
    }

    const cart = await priceCart(db, input.serviceIds)
    const ctx = await loadContext(db, input.date, input.date)

    // Nessuno esegue quella combinazione di servizi: non è un posto sfumato,
    // è una scelta impossibile — e il messaggio deve dirlo.
    if (eligibleOperators(ctx, input.operatorId, input.serviceIds).length === 0) {
      throw validation('services', 'Questo operatore non esegue i servizi scelti')
    }
    const operatorId = resolveOperatorFor(ctx, input.operatorId, input.serviceIds, input.date, input.time, cart.durationMinutes)
    if (!operatorId) throw slotTaken()

    const startsAt = zonedToInstant(input.date, input.time)
    let row: AppointmentRow
    try {
      const inserted = await queryOne<{ id: string }>(
        db,
        `insert into appointments
           (client_id, operator_id, starts_at, duration_minutes, total_price_cents, channel, note_for_operator, created_by_user_id)
         values ($1, $2, $3, $4, $5, $6, $7, $8)
         returning id`,
        [
          input.clientId, operatorId, startsAt, cart.durationMinutes, cart.totalPriceCents,
          input.channel ?? 'APP', input.noteForOperator ?? null, input.createdByUserId ?? null,
        ],
      )
      const id = inserted!.id
      for (const [position, item] of cart.items.entries()) {
        await query(
          db,
          `insert into appointment_services (appointment_id, position, service_id, name, duration_minutes, price_cents)
           values ($1, $2, $3, $4, $5, $6)`,
          [id, position, item.id, item.name, item.duration_minutes, item.price_cents],
        )
      }
      row = (await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1`, [id]))!
    } catch (error) {
      // Qualcuno ha confermato lo stesso orario mentre calcolavamo.
      if (pgCode(error) === PG.exclusionViolation) throw slotTaken()
      throw error
    }
    return { appointment: toDto(row), replacedDate }
  })
}

/** Conferma al cliente e avviso all'operatore, se il salone li tiene accesi. */
async function announceBooking(appointment: AppointmentDto, log?: FastifyBaseLogger) {
  const settings = await salonNotificationSettings(pool)
  const operatorName = await queryOne<{ name: string }>(pool, 'select name from operators where id = $1', [appointment.operatorId])
  const clientUserId = await userIdForClient(pool, appointment.clientId)

  if (settings.bookingConfirmation && clientUserId) {
    await notify(pool, {
      userId: clientUserId,
      kind: 'BOOKING_CONFIRMED',
      title: 'Prenotazione confermata',
      body: `Ci vediamo ${formatDateLong(appointment.date)} alle ${appointment.time}${operatorName ? ` con ${firstName(operatorName.name)}` : ''}.`,
      payload: { appointmentId: appointment.id },
    }, log)
  }

  const operatorUserId = await userIdForOperator(pool, appointment.operatorId)
  if (operatorUserId) {
    const client = await queryOne<{ first_name: string; last_name: string }>(
      pool, 'select first_name, last_name from clients where id = $1', [appointment.clientId],
    )
    await notify(pool, {
      userId: operatorUserId,
      kind: 'BOOKING_CONFIRMED',
      title: 'Nuova prenotazione',
      body: `${client?.first_name ?? 'Un cliente'} ${client?.last_name ?? ''} ha prenotato ${formatDateLong(appointment.date)} alle ${appointment.time}.`.replace(/\s+/g, ' '),
      payload: { appointmentId: appointment.id },
    }, log)
  }
}

export type RescheduleInput = {
  appointmentId: string
  date: LocalDate
  time: LocalTime
  operatorId?: string | null
  /** Il cliente resta sulla griglia; operatore e titolare spostano libero. */
  freeForm: boolean
}

export async function reschedule(input: RescheduleInput, log?: FastifyBaseLogger): Promise<AppointmentDto> {
  const { updated, previous } = await transaction(async (db) => {
    const current = await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1 for update`, [input.appointmentId])
    if (!current) throw notFound('Appuntamento')
    const before = toDto(current)
    if (!['CONFIRMED', 'IN_PROGRESS'].includes(before.status)) {
      throw validation('status', 'Questo appuntamento non è più spostabile')
    }

    const operatorId = input.operatorId ?? before.operatorId
    const ctx = await loadContext(db, input.date, input.date, { ignoreAppointmentId: before.id })

    const fits = input.freeForm
      ? canPlaceAt(ctx, operatorId, input.date, input.time, before.durationMinutes)
      : resolveOperatorFor(ctx, operatorId, before.serviceIds, input.date, input.time, before.durationMinutes) !== null
    if (!fits) throw slotTaken()

    try {
      await query(db, 'update appointments set starts_at = $2, operator_id = $3 where id = $1', [
        before.id, zonedToInstant(input.date, input.time), operatorId,
      ])
    } catch (error) {
      if (pgCode(error) === PG.exclusionViolation) throw slotTaken()
      throw error
    }
    const row = (await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1`, [before.id]))!
    return { updated: toDto(row), previous: before }
  })

  const clientUserId = await userIdForClient(pool, updated.clientId)
  if (clientUserId) {
    await notify(pool, {
      userId: clientUserId,
      kind: 'BOOKING_RESCHEDULED',
      title: 'Appuntamento spostato',
      body: `Il tuo appuntamento è ora ${formatDateLong(updated.date)} alle ${updated.time}.`,
      payload: { appointmentId: updated.id },
    }, log)
  }
  // Il vecchio orario si è liberato: tocca alla lista d'attesa di quel giorno.
  await notifyWaitlist(previous.date, log)
  return updated
}

export type CancelInput = {
  appointmentId: string
  by: 'CLIENT' | 'SALON'
}

export async function cancel(input: CancelInput, log?: FastifyBaseLogger): Promise<void> {
  const cancelled = await transaction(async (db) => {
    const current = await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1 for update`, [input.appointmentId])
    if (!current) throw notFound('Appuntamento')
    const appointment = toDto(current)
    if (appointment.status === 'CANCELLED') return appointment
    if (['COMPLETED', 'NO_SHOW'].includes(appointment.status)) {
      throw validation('status', 'Un appuntamento già concluso non si annulla')
    }

    // Finestra di disdetta: vale solo per il cliente dall'app.
    if (input.by === 'CLIENT') {
      const hoursToStart = (current.starts_at.getTime() - Date.now()) / 3_600_000
      if (hoursToStart < CLIENT_CANCELLATION_WINDOW_HOURS) {
        throw forbidden(
          `Mancano meno di ${CLIENT_CANCELLATION_WINDOW_HOURS} ore: chiama il salone per disdire`,
        )
      }
    }

    await query(
      db,
      `update appointments set status = 'CANCELLED', cancelled_by = $2, cancelled_at = now() where id = $1`,
      [appointment.id, input.by],
    )
    return appointment
  })

  const settings = await salonNotificationSettings(pool)
  if (settings.cancellationAlert && input.by === 'SALON') {
    const clientUserId = await userIdForClient(pool, cancelled.clientId)
    if (clientUserId) {
      await notify(pool, {
        userId: clientUserId,
        kind: 'BOOKING_CANCELLED',
        title: 'Appuntamento annullato',
        body: `L'appuntamento di ${formatDateLong(cancelled.date)} alle ${cancelled.time} è stato annullato dal salone.`,
        payload: { appointmentId: cancelled.id },
      }, log)
    }
  }
  if (input.by === 'CLIENT') {
    const operatorUserId = await userIdForOperator(pool, cancelled.operatorId)
    if (operatorUserId) {
      await notify(pool, {
        userId: operatorUserId,
        kind: 'BOOKING_CANCELLED',
        title: 'Appuntamento annullato',
        body: `Annullato l'appuntamento di ${formatDateLong(cancelled.date)} alle ${cancelled.time}.`,
        payload: { appointmentId: cancelled.id },
      }, log)
    }
  }

  await notifyWaitlist(cancelled.date, log)
}

/** Passaggi di stato dell'appuntamento, con i controlli che mancavano. */
/**
 * Passaggi di stato a mano. Il giro normale non ne ha bisogno: il lavoro
 * `appointments.status` (src/jobs/appointmentStatus.ts) porta da solo un
 * appuntamento in corso all'orario d'inizio e a completato alla fine. A mano
 * restano il no-show e il suo rimedio:
 * - NO_SHOW solo da orario d'inizio passato, anche su un appuntamento che il
 *   lavoro ha già chiuso come completato (il barbiere se ne accorge dopo);
 * - COMPLETED da NO_SHOW, per correggere un no-show segnato per sbaglio.
 * Il no-show avvisa il cliente; la correzione no.
 */
export const STATUS_TRANSITIONS: Record<'IN_PROGRESS' | 'COMPLETED' | 'NO_SHOW', string[]> = {
  IN_PROGRESS: ['CONFIRMED'],
  COMPLETED: ['CONFIRMED', 'IN_PROGRESS', 'NO_SHOW'],
  NO_SHOW: ['CONFIRMED', 'IN_PROGRESS', 'COMPLETED'],
}

export async function setStatus(
  appointmentId: string,
  status: 'IN_PROGRESS' | 'COMPLETED' | 'NO_SHOW',
  log?: FastifyBaseLogger,
): Promise<AppointmentDto> {
  const { appointment, changed } = await transaction(async (db) => {
    const current = await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1 for update`, [appointmentId])
    if (!current) throw notFound('Appuntamento')
    const before = toDto(current)
    if (before.status === status) return { appointment: before, changed: false } // idempotente
    if (before.status === 'CANCELLED') throw validation('status', 'Appuntamento annullato')
    if (!STATUS_TRANSITIONS[status].includes(before.status)) {
      throw validation('status', 'Passaggio di stato non consentito')
    }
    if (status === 'NO_SHOW' && new Date(current.starts_at).getTime() > Date.now()) {
      throw validation('status', "L'appuntamento non è ancora iniziato")
    }

    await query(
      db,
      `update appointments
          set status = $2::appointment_status,
              -- Il vincolo vuole completed_at se e solo se COMPLETED: si
              -- azzera uscendo da completato (no-show), si scrive entrandoci.
              -- Il cast è necessario: senza, Postgres deve dedurre due tipi
              -- diversi per lo stesso parametro e rifiuta la query.
              completed_at = case when $2::appointment_status = 'COMPLETED' then coalesce(completed_at, now()) else null end
        where id = $1`,
      [appointmentId, status],
    )
    const row = (await queryOne<AppointmentRow>(db, `${APPOINTMENT_SELECT} where a.id = $1`, [appointmentId]))!
    return { appointment: toDto(row), changed: true }
  })

  if (changed && status === 'NO_SHOW') {
    const clientUserId = await userIdForClient(pool, appointment.clientId)
    if (clientUserId) {
      await notify(pool, {
        userId: clientUserId,
        kind: 'BOOKING_NO_SHOW',
        title: 'Appuntamento non effettuato',
        body: `Non ti abbiamo visto all'appuntamento di ${formatDateLong(appointment.date)} alle ${appointment.time}. ` +
          'Quando vuoi, prenota un nuovo appuntamento dall\'app.',
        payload: { appointmentId: appointment.id },
      }, log)
    }
  }
  return appointment
}

// ------------------------------------------------------------- disponibilità

/**
 * `ignoreAppointmentId`: in modifica l'appuntamento stesso non occupa il suo
 * posto, altrimenti non si potrebbe tenere lo stesso orario.
 */
export async function availability(
  operatorId: string | null, serviceIds: string[], date: LocalDate, ignoreAppointmentId?: string,
) {
  const cart = await priceCart(pool, serviceIds)
  const ctx = await loadContext(pool, date, date, { ignoreAppointmentId })
  return { date, slots: daySlots(ctx, operatorId, serviceIds, date, cart.durationMinutes) }
}

export async function availabilityRange(
  operatorId: string | null, serviceIds: string[], from: LocalDate, to: LocalDate, ignoreAppointmentId?: string,
) {
  const cart = await priceCart(pool, serviceIds)
  const ctx = await loadContext(pool, from, to, { ignoreAppointmentId })
  return daysOverview(ctx, operatorId, serviceIds, from, to, cart.durationMinutes)
}

/** Prima disponibilità per ogni operatore: la usa il primo passo del wizard. */
export async function nextAvailability(serviceIds: string[], horizonDays = 30) {
  const cart = await priceCart(pool, serviceIds)
  const from = todayInSalon()
  const toDate = addDays(from, horizonDays)
  const ctx = await loadContext(pool, from, toDate)

  return eligibleOperators(ctx, null, serviceIds).map((op) => {
    for (let date = from; date <= toDate; date = addDays(date, 1)) {
      const slots = slotsForOperator(ctx, op, date, cart.durationMinutes)
      if (slots.length > 0) {
        const minutes = slots[0]!
        return {
          operatorId: op.id,
          date,
          time: `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`,
        }
      }
    }
    return { operatorId: op.id, date: null, time: null }
  })
}

// --------------------------------------------------------- lista d'attesa ---

type WaitlistRow = {
  id: string; client_id: string; on_date: string; at_time: string | null; operator_id: string | null
  duration_minutes: number; total_price_cents: number; status: string; position: number; service_ids: string[]
}

const WAITLIST_SELECT = `
  select w.id, w.client_id, w.on_date, w.at_time, w.operator_id, w.duration_minutes,
         w.total_price_cents, w.status,
         (
           select count(*) + 1
             from waitlist_entries q
            where q.status = 'WAITING' and q.on_date = w.on_date
              and q.operator_id is not distinct from w.operator_id
              and q.created_at < w.created_at
         )::int as position,
         coalesce(
           (select array_agg(s.service_id order by s.position) from waitlist_entry_services s where s.entry_id = w.id),
           '{}'
         ) as service_ids
    from waitlist_entries w`

function waitlistDto(row: WaitlistRow): WaitlistEntryDto {
  return {
    id: row.id,
    clientId: row.client_id,
    date: row.on_date,
    time: row.at_time ? row.at_time.slice(0, 5) : null,
    operatorId: row.operator_id,
    serviceIds: row.service_ids,
    durationMinutes: row.duration_minutes,
    totalPriceCents: row.total_price_cents,
    position: row.position,
    status: row.status,
  }
}

export async function waitlistForClient(db: Db, clientId: string): Promise<WaitlistEntryDto[]> {
  const rows = await query<WaitlistRow>(
    db,
    `${WAITLIST_SELECT}
      where w.client_id = $1 and w.status = 'WAITING' and w.on_date >= current_date
      order by w.on_date, w.at_time nulls first`,
    [clientId],
  )
  return rows.map(waitlistDto)
}

export type JoinWaitlistInput = {
  clientId: string
  date: LocalDate
  time: LocalTime | null
  operatorId: string | null
  serviceIds: string[]
}

export async function joinWaitlist(input: JoinWaitlistInput): Promise<WaitlistEntryDto> {
  return transaction(async (db) => {
    const cart = await priceCart(db, input.serviceIds)
    // Una sola posizione per cliente, giorno e operatore: se c'è già, si
    // aggiorna l'ora richiesta invece di creare una seconda attesa.
    const existing = await queryOne<{ id: string }>(
      db,
      `select id from waitlist_entries
        where client_id = $1 and on_date = $2
          and operator_id is not distinct from $3 and status = 'WAITING'`,
      [input.clientId, input.date, input.operatorId],
    )

    let id: string
    if (existing) {
      id = existing.id
      await query(db, 'update waitlist_entries set at_time = $2, duration_minutes = $3, total_price_cents = $4 where id = $1', [
        id, input.time, cart.durationMinutes, cart.totalPriceCents,
      ])
      await query(db, 'delete from waitlist_entry_services where entry_id = $1', [id])
    } else {
      const inserted = await queryOne<{ id: string }>(
        db,
        `insert into waitlist_entries (client_id, on_date, at_time, operator_id, duration_minutes, total_price_cents)
         values ($1, $2, $3, $4, $5, $6) returning id`,
        [input.clientId, input.date, input.time, input.operatorId, cart.durationMinutes, cart.totalPriceCents],
      )
      id = inserted!.id
    }
    for (const [position, item] of cart.items.entries()) {
      await query(db, 'insert into waitlist_entry_services (entry_id, position, service_id) values ($1, $2, $3)', [
        id, position, item.id,
      ])
    }
    const row = (await queryOne<WaitlistRow>(db, `${WAITLIST_SELECT} where w.id = $1`, [id]))!
    return waitlistDto(row)
  })
}

export async function leaveWaitlist(entryId: string, clientId?: string): Promise<void> {
  const row = await queryOne<{ id: string; client_id: string }>(
    pool, 'select id, client_id from waitlist_entries where id = $1', [entryId],
  )
  if (!row) return
  if (clientId && row.client_id !== clientId) throw forbidden('Questa attesa non è tua')
  await query(pool, 'delete from waitlist_entries where id = $1', [entryId])
}

/**
 * Si è liberato spazio in quel giorno: avvisa **una** persona in coda, la prima
 * il cui orario torna prenotabile. Il posto non viene bloccato — chi prenota
 * per primo se lo prende — ed è la stessa regola di prima, ora con le
 * preferenze del cliente rispettate davvero.
 */
export async function notifyWaitlist(date: LocalDate, log?: FastifyBaseLogger): Promise<void> {
  const entries = await query<WaitlistRow>(
    pool,
    `${WAITLIST_SELECT} where w.on_date = $1 and w.status = 'WAITING' order by w.created_at`,
    [date],
  )
  if (entries.length === 0) return

  const ctx = await loadContext(pool, date, date)
  for (const row of entries) {
    const entry = waitlistDto(row)
    const slots = daySlots(ctx, entry.operatorId, entry.serviceIds, date, entry.durationMinutes)
    if (slots.length === 0) continue
    const slot = entry.time && slots.includes(entry.time) ? entry.time : entry.time ? null : slots[0]!
    if (!slot) continue

    const userId = await userIdForClient(pool, entry.clientId)
    // Scheda senza account (cliente creato al telefono): non c'è modo di
    // avvisarlo, quindi si passa al prossimo invece di bruciare l'annuncio.
    if (!userId) continue
    {
      const prefs = await clientPrefs(pool, userId)
      if (!prefs.waitlistAlerts) continue // rispetta l'interruttore nel profilo
      const operatorName = entry.operatorId
        ? await queryOne<{ name: string }>(pool, 'select name from operators where id = $1', [entry.operatorId])
        : null
      await notify(pool, {
        userId,
        kind: 'WAITLIST_SLOT',
        title: "Lista d'attesa",
        body: `Si è liberato un posto ${formatDateLong(date)} alle ${slot}${operatorName ? ` con ${firstName(operatorName.name)}` : ''}: prenota prima che lo prenda qualcun altro.`,
        payload: { waitlistEntryId: entry.id, date, time: slot },
      }, log)
    }
    await query(pool, `update waitlist_entries set status = 'NOTIFIED', notified_at = now() where id = $1`, [entry.id])
    return // una notifica per ogni posto liberato
  }
}

export { minutesOfTime }
