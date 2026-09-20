import type { FastifyBaseLogger } from 'fastify'
import { pool, query } from '../db/pool.js'
import { daysBetween, instantToZoned, todayInSalon, type LocalDate } from '../lib/time.js'
import { firstName, formatDateLong } from '../lib/format.js'
import { notify } from '../services/notifications.js'

/**
 * Promemoria automatici (§4.7 di FEATURES). Il titolare configura una o più
 * regole ("1° promemoria: un giorno prima", "2°: 2 ore prima") e questo lavoro
 * le fa scattare. Se `reminder_rules` è vuota non parte niente: l'elenco delle
 * regole È l'interruttore, non serve un altro flag.
 */

export const REMINDERS_JOB = 'booking.reminders'
/** Ogni quarto d'ora: la regola più stretta prevista dal mockup è "2 ore prima". */
export const REMINDERS_CRON = '*/15 * * * *'

type DueReminderRow = {
  appointment_id: string
  rule_id: string
  hours_before: number
  user_id: string
  starts_at: Date
  operator_name: string
}

/**
 * Una riga per ogni coppia (appuntamento, regola) ancora da avvisare.
 *
 * Esattamente-una-volta senza tabella di appoggio: lo schema non ha una colonna
 * di deduplica e non possiamo aggiungerne una qui, ma `notifications.payload` è
 * jsonb e porta già i riferimenti del tap. Ci scriviamo dentro `appointmentId` e
 * `ruleId` e il NOT EXISTS qui sotto li rilegge: la notifica stessa diventa la
 * ricevuta dell'invio. Costa una scansione in più ma non può divergere dal
 * risultato, perché non esiste uno stato "inviato" separato dalla notifica
 * inviata. Se un giorno i volumi lo giustificano, la mossa è un indice
 * funzionale su (kind, payload->>'appointmentId').
 *
 * Due paletti sulla finestra:
 *  - `starts_at > now()`: un appuntamento già iniziato non si ricorda più;
 *  - `created_at <= starts_at - hours_before`: la regola vale solo se
 *    l'appuntamento esisteva già quando la sua finestra si è aperta. Senza
 *    questo, chi prenota per fra un'ora riceverebbe di colpo il promemoria di
 *    24 ore e quello di 2 ore, insieme alla conferma appena letta.
 */
const DUE_REMINDERS_SQL = `
  select a.id           as appointment_id,
         a.starts_at    as starts_at,
         r.id           as rule_id,
         r.hours_before as hours_before,
         u.id           as user_id,
         o.name         as operator_name
    from appointments a
    cross join reminder_rules r
    join users u on u.client_id = a.client_id and u.disabled_at is null
    join operators o on o.id = a.operator_id
    left join client_notification_prefs p on p.user_id = u.id
   where a.status in ('CONFIRMED', 'IN_PROGRESS')
     and coalesce(p.appointment_reminder, true)
     and a.starts_at > now()
     and a.starts_at <= now() + make_interval(hours => r.hours_before)
     and a.created_at <= a.starts_at - make_interval(hours => r.hours_before)
     and not exists (
           select 1
             from notifications n
            where n.user_id = u.id
              and n.kind = 'BOOKING_REMINDER'
              and n.payload ->> 'appointmentId' = a.id::text
              and n.payload ->> 'ruleId' = r.id::text
         )
   order by a.starts_at, r.hours_before desc`

/** "oggi"/"domani" come nel testo del mockup, la data estesa per il resto. */
function whenLabel(date: LocalDate): string {
  const delta = daysBetween(todayInSalon(), date)
  if (delta === 0) return 'oggi'
  if (delta === 1) return 'domani'
  return formatDateLong(date)
}

export async function runReminders(log?: FastifyBaseLogger): Promise<{ sent: number; failed: number }> {
  const due = await query<DueReminderRow>(pool, DUE_REMINDERS_SQL)
  let sent = 0
  let failed = 0

  for (const row of due) {
    const zoned = instantToZoned(row.starts_at)
    try {
      await notify(
        pool,
        {
          userId: row.user_id,
          kind: 'BOOKING_REMINDER',
          title: 'Promemoria appuntamento',
          body: `Ci vediamo ${whenLabel(zoned.date)} alle ${zoned.time} con ${firstName(row.operator_name)}.`,
          // Le due chiavi che rendono l'invio ripetibile senza duplicati.
          payload: { appointmentId: row.appointment_id, ruleId: row.rule_id, hoursBefore: row.hours_before },
        },
        log,
      )
      sent++
    } catch (error) {
      // Un destinatario che fallisce non deve fermare gli altri: al giro dopo
      // torna fra quelli da avvisare, perché la notifica non è stata scritta.
      failed++
      log?.warn({ reason: (error as Error).message }, 'promemoria non inviato')
    }
  }

  // Solo numeri: né nomi, né orari di clienti, né identificativi di persone.
  log?.info({ due: due.length, sent, failed }, 'promemoria appuntamenti')
  return { sent, failed }
}
