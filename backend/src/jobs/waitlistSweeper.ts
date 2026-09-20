import type { FastifyBaseLogger } from 'fastify'
import { pool, query } from '../db/pool.js'
import { todayInSalon } from '../lib/time.js'

/**
 * Pulizia della lista d'attesa. Le richieste nascono con stato WAITING e
 * passano a NOTIFIED quando si libera un posto, ma nessuno le chiude mai: senza
 * questo lavoro resterebbero lì per sempre, falsando le code (§6.4 di FEATURES,
 * "le richieste per giorni passati non si vedono più") e tenendo occupato
 * l'indice unico che impedisce al cliente di rimettersi in coda per quel giorno.
 */

export const WAITLIST_SWEEPER_JOB = 'waitlist.sweeper'
/** Ogni ora, al minuto 7: sfalsato dagli altri lavori, che partono sul minuto tondo. */
export const WAITLIST_SWEEPER_CRON = '7 * * * *'

/**
 * Quanto vale un avviso "si è liberato un posto" prima di considerarsi scaduto.
 * Il posto non è riservato: passato un giorno, o il cliente ha prenotato o la
 * richiesta non serve più.
 */
export const NOTIFIED_TTL_HOURS = 24

export async function sweepWaitlist(log?: FastifyBaseLogger): Promise<{ pastDay: number; staleNotified: number }> {
  // Il giorno lo decide il fuso del salone, non quello della sessione Postgres:
  // a mezzanotte scarsa `current_date` del server potrebbe essere già domani.
  const today = todayInSalon()

  // Idempotente per costruzione: la UPDATE tocca solo le righe non ancora
  // scadute, quindi rieseguire il lavoro (o rieseguirlo dopo un errore a metà)
  // non cambia niente.
  const pastDay = await query<{ id: string }>(
    pool,
    `update waitlist_entries
        set status = 'EXPIRED', expires_at = now()
      where status in ('WAITING', 'NOTIFIED')
        and on_date < $1::date
      returning id`,
    [today],
  )

  const staleNotified = await query<{ id: string }>(
    pool,
    `update waitlist_entries
        set status = 'EXPIRED', expires_at = now()
      where status = 'NOTIFIED'
        and coalesce(notified_at, created_at) < now() - make_interval(hours => $1)
      returning id`,
    [NOTIFIED_TTL_HOURS],
  )

  log?.info(
    { pastDay: pastDay.length, staleNotified: staleNotified.length, notifiedTtlHours: NOTIFIED_TTL_HOURS },
    "pulizia lista d'attesa",
  )
  return { pastDay: pastDay.length, staleNotified: staleNotified.length }
}
