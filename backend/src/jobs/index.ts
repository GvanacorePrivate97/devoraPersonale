import PgBoss from 'pg-boss'
import type { FastifyBaseLogger } from 'fastify'
import { env } from '../config/env.js'
import { SALON_TZ } from '../lib/time.js'
import { idSchema } from '../lib/validation.js'
import { REMINDERS_CRON, REMINDERS_JOB, runReminders } from './reminders.js'
import { WAITLIST_SWEEPER_CRON, WAITLIST_SWEEPER_JOB, sweepWaitlist } from './waitlistSweeper.js'
import { APPOINTMENT_STATUS_CRON, APPOINTMENT_STATUS_JOB, advanceAppointmentStatuses } from './appointmentStatus.js'
import {
  CAMPAIGN_SCHEDULED_CRON, CAMPAIGN_SCHEDULED_JOB, CAMPAIGN_SEND_JOB,
  enqueueDueCampaigns, sendCampaign,
} from './campaigns.js'

/**
 * Lavori pianificati. La coda è pg-boss, che si crea il proprio schema nello
 * stesso database: nessun Redis, nessun secondo servizio con stato da tenere in
 * piedi — il Postgres del salone basta e avanza.
 *
 * Regola d'oro: l'API non dipende dalla coda. Se il database non risponde
 * all'avvio, `startJobs` scrive un avviso e non parte: `npm run dev` deve
 * restare utilizzabile anche senza Postgres acceso, altrimenti si finisce per
 * disattivare i lavori a mano e dimenticarseli spenti.
 */

export const JOB = {
  reminders: REMINDERS_JOB,
  waitlistSweeper: WAITLIST_SWEEPER_JOB,
  appointmentStatus: APPOINTMENT_STATUS_JOB,
  campaignSend: CAMPAIGN_SEND_JOB,
  campaignScheduled: CAMPAIGN_SCHEDULED_JOB,
} as const

export type JobName = (typeof JOB)[keyof typeof JOB]

let boss: PgBoss | null = null
let logger: FastifyBaseLogger | null = null

/**
 * Motivo leggibile di un errore. Il fallimento di connessione di Node arriva
 * come AggregateError con il messaggio vuoto: senza scendere al primo errore
 * interno, nel log resterebbe una riga che non dice niente.
 */
function reason(error: unknown): string {
  if (error instanceof AggregateError) {
    const first = error.errors[0]
    if (first !== undefined) return reason(first)
  }
  if (error instanceof Error) return error.message || error.name
  return String(error)
}

export async function startJobs(log: FastifyBaseLogger): Promise<void> {
  if (boss) return
  logger = log
  // Nei test la coda non serve e sporcherebbe il database con il suo schema.
  if (env.isTest) return

  const instance = new PgBoss({
    connectionString: env.DATABASE_URL,
    application_name: 'mencare-jobs',
    schedule: true,
    supervise: true,
    // Lo storico dei lavori non è un archivio: due settimane e via.
    deleteAfterDays: 14,
  })
  // pg-boss emette gli errori di runtime come evento: senza ascoltatore
  // diventerebbero eccezioni non gestite che abbattono il processo.
  instance.on('error', (error) => log.error({ reason: reason(error) }, 'coda dei lavori in errore'))

  try {
    await instance.start()

    // In pg-boss 10 le code vanno dichiarate prima di lavorarle. I tre lavori a
    // orario sono `singleton`: se un giro è ancora in corso quando scatta il
    // successivo, il secondo non si accoda e basta — meglio saltare un turno
    // che averne due che leggono le stesse righe. L'invio campagna resta
    // standard, perché due campagne diverse possono partire insieme.
    await instance.createQueue(JOB.reminders, { name: JOB.reminders, policy: 'singleton' })
    await instance.createQueue(JOB.waitlistSweeper, { name: JOB.waitlistSweeper, policy: 'singleton' })
    await instance.createQueue(JOB.appointmentStatus, { name: JOB.appointmentStatus, policy: 'singleton' })
    await instance.createQueue(JOB.campaignScheduled, { name: JOB.campaignScheduled, policy: 'singleton' })
    await instance.createQueue(JOB.campaignSend, { name: JOB.campaignSend, policy: 'standard' })

    await instance.work(JOB.reminders, async () => {
      await runReminders(log)
    })

    await instance.work(JOB.waitlistSweeper, async () => {
      await sweepWaitlist(log)
    })

    await instance.work(JOB.appointmentStatus, async () => {
      await advanceAppointmentStatuses(log)
    })

    await instance.work<{ campaignId?: unknown }>(JOB.campaignSend, async (jobs) => {
      for (const job of jobs) {
        // Il payload arriva dal database: si valida come si valida un body.
        const parsed = idSchema.safeParse(job.data?.campaignId)
        if (!parsed.success) {
          log.warn({ jobId: job.id }, 'invio campagna senza identificativo valido')
          continue
        }
        await sendCampaign(parsed.data, log)
      }
    })

    await instance.work(JOB.campaignScheduled, async () => {
      await enqueueDueCampaigns(log, async (campaignId, occurrenceKey) => {
        await instance.send(JOB.campaignSend, { campaignId }, { singletonKey: occurrenceKey })
      })
    })

    // Il cron gira nel fuso del salone: per questi intervalli non cambia nulla
    // oggi, ma il giorno in cui una regola diventerà "ogni mattina alle 8"
    // l'ora legale non la sposterà.
    await instance.schedule(JOB.reminders, REMINDERS_CRON, {}, { tz: SALON_TZ })
    await instance.schedule(JOB.waitlistSweeper, WAITLIST_SWEEPER_CRON, {}, { tz: SALON_TZ })
    await instance.schedule(JOB.appointmentStatus, APPOINTMENT_STATUS_CRON, {}, { tz: SALON_TZ })
    await instance.schedule(JOB.campaignScheduled, CAMPAIGN_SCHEDULED_CRON, {}, { tz: SALON_TZ })

    boss = instance
    log.info(
      {
        jobs: [
          { name: JOB.reminders, cron: REMINDERS_CRON },
          { name: JOB.waitlistSweeper, cron: WAITLIST_SWEEPER_CRON },
          { name: JOB.appointmentStatus, cron: APPOINTMENT_STATUS_CRON },
          { name: JOB.campaignScheduled, cron: CAMPAIGN_SCHEDULED_CRON },
          { name: JOB.campaignSend, cron: null },
        ],
      },
      'lavori pianificati attivi',
    )
  } catch (error) {
    // Niente stack e niente connection string nel log: solo il motivo.
    log.warn(
      { reason: reason(error) },
      "coda dei lavori non disponibile: l'API parte lo stesso, ma promemoria e campagne non girano",
    )
    await instance.stop({ wait: false }).catch(() => {})
    boss = null
  }
}

export async function stopJobs(): Promise<void> {
  const instance = boss
  boss = null
  if (!instance) return
  try {
    // `graceful` lascia finire il lavoro in corso: un invio a metà campagna
    // ripartirebbe comunque, ma tanto vale non interromperlo.
    await instance.stop({ graceful: true, wait: true, timeout: 10_000 })
  } catch (error) {
    logger?.warn({ reason: reason(error) }, 'arresto della coda non riuscito')
  }
}

/**
 * Accoda un lavoro. La usano le rotte che devono far partire qualcosa fuori
 * dalla richiesta — il titolare che preme "Invia ora" su una campagna accoda
 * `campaign.send` e riceve subito la risposta, senza restare appeso all'invio.
 *
 * Se la coda non è attiva torna `null` invece di sollevare: l'operazione
 * dell'utente è comunque andata a buon fine, è l'invio che non parte, e questo
 * deve risultare dal log e dalla risposta, non da un 500.
 */
export async function enqueue(
  name: string,
  data: Record<string, unknown>,
  options?: PgBoss.SendOptions,
): Promise<string | null> {
  if (!boss) {
    logger?.warn({ job: name }, 'coda non attiva: lavoro non accodato')
    return null
  }
  return boss.send(name, data, options ?? {})
}
