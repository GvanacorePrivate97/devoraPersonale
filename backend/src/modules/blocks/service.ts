import type { FastifyBaseLogger } from 'fastify'
import type { Db } from '../../db/pool.js'
import { pool, query, queryOne, transaction } from '../../db/pool.js'
import type { AppointmentDto } from '../../db/mappers.js'
import { toLocalTime } from '../../db/mappers.js'
import { forbidden, notFound, validation } from '../../lib/errors.js'
import { addDays, minutesOfTime, type LocalDate, type LocalTime } from '../../lib/time.js'
import { appointmentsForOperatorDay, notifyWaitlist } from '../booking/service.js'

/**
 * Blocchi: le assenze che stanno dentro una giornata (permesso, pausa, ferie
 * di poche ore, corso). Per il motore degli slot sono tempo occupato esattamente
 * come un appuntamento, quindi valgono due regole:
 *  * non si crea un blocco sopra appuntamenti attivi — si dice quali sono e si
 *    lascia decidere a chi sta davanti allo schermo;
 *  * cancellarne uno libera orari, e chi è in lista d'attesa va avvisato: era
 *    l'asimmetria del vecchio codice, che avvisava la coda solo per le disdette.
 */

export type BlockReason = 'PERMESSO' | 'PAUSA' | 'FERIE' | 'CORSO'

export type TimeBlockDto = {
  id: string
  operatorId: string
  reason: BlockReason
  date: LocalDate
  start: LocalTime
  end: LocalTime
  label: string | null
}

type BlockRow = {
  id: string; operator_id: string; reason: BlockReason; on_date: string
  starts_at: string; ends_at: string; label: string | null
}

const BLOCK_SELECT = `
  select id, operator_id, reason, on_date, starts_at, ends_at, label
    from time_blocks`

function toDto(row: BlockRow): TimeBlockDto {
  return {
    id: row.id,
    operatorId: row.operator_id,
    reason: row.reason,
    date: row.on_date,
    start: toLocalTime(row.starts_at),
    end: toLocalTime(row.ends_at),
    label: row.label,
  }
}

/** Blocchi di una giornata; senza operatore, quelli di tutta la squadra. */
export async function blocksForDay(db: Db, operatorId: string | null, date: LocalDate): Promise<TimeBlockDto[]> {
  const rows = await query<BlockRow>(
    db,
    `${BLOCK_SELECT}
      where on_date = $1 and ($2::uuid is null or operator_id = $2::uuid)
      order by starts_at`,
    [date, operatorId],
  )
  return rows.map(toDto)
}

/** Una settimana intera, lunedì compreso: la vista del titolare la legge così. */
export async function blocksForWeek(
  db: Db, operatorId: string | null, weekStart: LocalDate,
): Promise<TimeBlockDto[]> {
  const rows = await query<BlockRow>(
    db,
    `${BLOCK_SELECT}
      where on_date between $1 and $2 and ($3::uuid is null or operator_id = $3::uuid)
      order by on_date, starts_at`,
    [weekStart, addDays(weekStart, 6), operatorId],
  )
  return rows.map(toDto)
}

export async function blockById(db: Db, id: string): Promise<TimeBlockDto | null> {
  const row = await queryOne<BlockRow>(db, `${BLOCK_SELECT} where id = $1`, [id])
  return row ? toDto(row) : null
}

/**
 * Appuntamenti attivi che quell'intervallo travolgerebbe. La lettura della
 * giornata è quella dell'agenda (`appointmentsForOperatorDay`): stessa query,
 * stessi confini di giornata nel fuso del salone, nessuna seconda verità.
 */
export async function conflicts(
  db: Db, operatorId: string, date: LocalDate, start: LocalTime, end: LocalTime,
): Promise<AppointmentDto[]> {
  const from = minutesOfTime(start)
  const to = minutesOfTime(end)
  const day = await appointmentsForOperatorDay(db, operatorId, date)
  return day.filter((appointment) => {
    if (!['CONFIRMED', 'IN_PROGRESS'].includes(appointment.status)) return false
    const appointmentStart = minutesOfTime(appointment.time)
    return appointmentStart < to && from < appointmentStart + appointment.durationMinutes
  })
}

export type NewBlockInput = {
  operatorId: string
  reason: BlockReason
  date: LocalDate
  start: LocalTime
  end: LocalTime
  label?: string | null
  createdByUserId?: string | null
}

/**
 * Il blocco nasce solo se la fascia è davvero libera. Controllo e inserimento
 * stanno nella stessa transazione: fra il "nessun conflitto" e la scrittura non
 * deve poter entrare una prenotazione.
 */
export async function createBlock(input: NewBlockInput): Promise<TimeBlockDto> {
  if (minutesOfTime(input.end) <= minutesOfTime(input.start)) {
    throw validation('end', 'La fine deve venire dopo l’inizio')
  }

  return transaction(async (db) => {
    const operator = await queryOne<{ id: string }>(db, 'select id from operators where id = $1', [input.operatorId])
    if (!operator) throw notFound('Operatore')

    const hit = await conflicts(db, input.operatorId, input.date, input.start, input.end)
    if (hit.length > 0) {
      throw validation(
        'conflicts',
        hit.length === 1
          ? 'In quella fascia c’è già un appuntamento: spostalo o annullalo prima di bloccare'
          : `In quella fascia ci sono ${hit.length} appuntamenti: spostali o annullali prima di bloccare`,
      )
    }

    const row = await queryOne<BlockRow>(
      db,
      `insert into time_blocks (operator_id, reason, on_date, starts_at, ends_at, label, created_by_user_id)
       values ($1, $2, $3, $4, $5, $6, $7)
       returning id, operator_id, reason, on_date, starts_at, ends_at, label`,
      [
        input.operatorId, input.reason, input.date, input.start, input.end,
        input.label ?? null, input.createdByUserId ?? null,
      ],
    )
    return toDto(row!)
  })
}

/**
 * `restrictToOperatorId` c'è quando a cancellare è un operatore: solo i suoi.
 * Tolto il blocco, quegli orari tornano prenotabili: la coda di quel giorno va
 * avvisata, come succede già quando si libera un appuntamento.
 */
export async function deleteBlock(
  id: string, restrictToOperatorId?: string, log?: FastifyBaseLogger,
): Promise<void> {
  const block = await blockById(pool, id)
  if (!block) return
  if (restrictToOperatorId && block.operatorId !== restrictToOperatorId) {
    throw forbidden('Questo blocco non è tuo')
  }
  await query(pool, 'delete from time_blocks where id = $1', [id])
  await notifyWaitlist(block.date, log)
}
