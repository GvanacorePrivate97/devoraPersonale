import type { FastifyBaseLogger } from 'fastify'
import { pool, query } from '../db/pool.js'

/**
 * Stati degli appuntamenti che vanno avanti da soli, così il barbiere non deve
 * toccarli a ogni cliente:
 * - all'orario d'inizio un appuntamento confermato diventa "in corso";
 * - a orario d'inizio + durata, se nessuno l'ha gestito, diventa "completato"
 *   (e conta per visite, spesa e KPI).
 * A mano resta solo il no-show, che si può segnare anche dopo la chiusura
 * automatica e si corregge riportandolo a completato (booking/service.ts).
 */

export const APPOINTMENT_STATUS_JOB = 'appointments.status'
/** Ogni minuto: la card cambia stato al massimo un minuto dopo l'orario. */
export const APPOINTMENT_STATUS_CRON = '* * * * *'

export async function advanceAppointmentStatuses(
  log?: FastifyBaseLogger,
  now: Date = new Date(),
): Promise<{ started: number; completed: number }> {
  // Prima si chiude ciò che è finito, poi si apre ciò che è iniziato: così un
  // appuntamento già passato per intero non transita da "in corso".
  // Idempotente: le UPDATE toccano solo le righe ancora da far avanzare.
  const completed = await query<{ id: string }>(
    pool,
    `update appointments
        set status = 'COMPLETED', completed_at = now()
      where status in ('CONFIRMED', 'IN_PROGRESS')
        and ends_at <= $1::timestamptz
      returning id`,
    [now.toISOString()],
  )
  const started = await query<{ id: string }>(
    pool,
    `update appointments
        set status = 'IN_PROGRESS'
      where status = 'CONFIRMED'
        and starts_at <= $1::timestamptz
        and ends_at > $1::timestamptz
      returning id`,
    [now.toISOString()],
  )
  if (started.length > 0 || completed.length > 0) {
    log?.info({ started: started.length, completed: completed.length }, 'stati appuntamenti aggiornati')
  }
  return { started: started.length, completed: completed.length }
}
