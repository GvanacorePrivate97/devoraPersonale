import type { FastifyBaseLogger } from 'fastify'
import { env } from '../config/env.js'
import { pool, query, queryOne } from '../db/pool.js'
import { SALON_TZ, todayInSalon, zonedToInstant } from '../lib/time.js'
import { firstName } from '../lib/format.js'
import { notify, ownerUserIds } from '../services/notifications.js'
import { segmentCondition, type ClientSegment } from '../modules/crm/service.js'

/**
 * Campagne push (§4.2 di FEATURES). Due lavori che si passano il testimone:
 * `campaign.scheduled` guarda l'orologio e mette in coda le campagne
 * programmate, `campaign.send` fa l'invio vero. Tenerli separati serve a non
 * legare l'invio al cron: il titolare che preme "Invia ora" accoda lo stesso
 * lavoro, e il percorso resta uno solo.
 */

export const CAMPAIGN_SEND_JOB = 'campaign.send'
export const CAMPAIGN_SCHEDULED_JOB = 'campaign.scheduled'
/** Ogni 5 minuti: la schermata programma al minuto, questo è il ritardo massimo. */
export const CAMPAIGN_SCHEDULED_CRON = '*/5 * * * *'

/** "Ripeti ogni settimana" si ferma da solo: "Stop dopo 4 invii" (§4.2). */
export const WEEKLY_REPEAT_MAX_RUNS = 4
const WEEK_MS = 7 * 24 * 60 * 60 * 1000

type CampaignRow = {
  id: string
  name: string
  segment: string
  title: string
  body: string
  scheduled_at: Date | null
  repeat_weekly: boolean
  send_cap: number | null
  sent_count: number
  status: string
}

export type CampaignRecipient = {
  clientId: string
  firstName: string
  userId: string | null
  /** Ha dato il consenso alle promozioni ed è raggiungibile da una push. */
  reachable: boolean
}

/**
 * Destinatari di un segmento, uno per uno.
 *
 * Il segmento non si ricalcola a mano: la condizione arriva da
 * `segmentCondition` del CRM, la stessa che usa `campaignReach` dietro
 * GET /admin/campaigns/reach. Se domani "Top spesa" cambia taglio, cambia in un
 * posto solo e il numero promesso al titolare resta quello che parte davvero.
 *
 * "Raggiungibile" ripete parola per parola la definizione di `campaignReach`:
 * consenso commerciale sulla scheda, account collegato e interruttore
 * "Promozioni e novità" acceso nel profilo (nasce spento). Il token push non
 * serve a essere raggiungibili: la campagna arriva comunque nella campanella e
 * la push la fa solo squillare. A un account disattivato non si spedisce
 * niente, nemmeno se un vecchio token è rimasto in giro.
 */
export async function campaignRecipients(segment: ClientSegment): Promise<CampaignRecipient[]> {
  const params: unknown[] = []
  const condition = segmentCondition(segment, params)
  const rows = await query<{
    client_id: string; first_name: string; user_id: string | null; reachable: boolean
  }>(
    pool,
    `select c.id         as client_id,
            c.first_name as first_name,
            u.id         as user_id,
            -- Stessa definizione del conteggio mostrato al titolare in
            -- campaignReach: conta la notifica in app, la push è un extra.
            (c.marketing_opt_in
              and u.id is not null
              and coalesce(p.marketing, false)) as reachable
       from clients c
       join client_stats st on st.client_id = c.id
       left join users u on u.client_id = c.id and u.disabled_at is null
       left join client_notification_prefs p on p.user_id = u.id
      where ${condition}
      order by st.lifetime_spend_cents desc, c.id`,
    params,
  )
  return rows.map((r) => ({
    clientId: r.client_id,
    firstName: r.first_name,
    userId: r.user_id,
    reachable: r.reachable && r.user_id !== null,
  }))
}

/** I segnaposto del compositore: "+ Nome cliente" e "+ Link prenota" (§4.2). */
function personalize(text: string, clientFirstName: string): string {
  return text
    .replaceAll('{{nome}}', firstName(clientFirstName))
    .replaceAll('{{link}}', `${env.PUBLIC_BASE_URL}/prenota`)
}

/** Quante volte questa campagna è già partita, contando un invio per giornata. */
async function runsSoFar(campaignId: string): Promise<number> {
  const row = await queryOne<{ runs: number }>(
    pool,
    `select count(distinct (sent_at at time zone $2)::date)::int as runs
       from campaign_sends
      where campaign_id = $1`,
    [campaignId, SALON_TZ],
  )
  return row?.runs ?? 0
}

/** Il prossimo appuntamento settimanale, sempre spostato nel futuro. */
function nextWeeklyRun(from: Date | null): Date {
  let next = new Date((from ?? new Date()).getTime() + WEEK_MS)
  while (next.getTime() <= Date.now()) next = new Date(next.getTime() + WEEK_MS)
  return next
}

/**
 * Invio di una campagna.
 *
 * Idempotenza: una ripetizione del lavoro (riavvio, ritentativo di pg-boss,
 * doppio accodamento) non deve rimandare la stessa push. Il criterio è "questo
 * cliente ha già una riga `campaign_sends` per questa campagna oggi": le
 * ripetizioni previste dal prodotto sono settimanali, quindi la giornata di
 * salone è una chiave sicura e non serve inventare una colonna di run che lo
 * schema non ha. La riga si scrive *prima* della notifica: se il processo muore
 * in mezzo il cliente perde una push, ma non ne riceve due — su una
 * comunicazione commerciale è il lato giusto in cui sbagliare.
 */
export async function sendCampaign(
  campaignId: string,
  log?: FastifyBaseLogger,
): Promise<{ sent: number; failed: number; skipped: boolean }> {
  const campaign = await queryOne<CampaignRow>(
    pool,
    `select id, name, segment, title, body, scheduled_at, repeat_weekly, send_cap, sent_count, status
       from push_campaigns where id = $1`,
    [campaignId],
  )
  if (!campaign) {
    log?.warn({ campaignId }, 'campagna inesistente: invio ignorato')
    return { sent: 0, failed: 0, skipped: true }
  }
  // Già conclusa: il lavoro è arrivato in ritardo, o in doppio.
  if (campaign.status === 'SENT') {
    log?.info({ campaignId }, 'campagna già inviata: niente da fare')
    return { sent: 0, failed: 0, skipped: true }
  }

  const recipients = await campaignRecipients(campaign.segment as ClientSegment)
  const reachable = recipients.filter((r) => r.reachable && r.userId)

  // Chi ha già ricevuto questa campagna nella giornata di salone in corso.
  const alreadySent = await query<{ client_id: string }>(
    pool,
    'select client_id from campaign_sends where campaign_id = $1 and sent_at >= $2::timestamptz',
    [campaign.id, zonedToInstant(todayInSalon(), '00:00')],
  )
  const done = new Set(alreadySent.map((r) => r.client_id))

  // Il tetto vale sul totale della campagna, non sul singolo giro: con la
  // ripetizione settimanale è quello che ferma la serie.
  const remaining = campaign.send_cap === null
    ? Number.POSITIVE_INFINITY
    : Math.max(0, campaign.send_cap - campaign.sent_count)

  const queue = reachable.filter((r) => !done.has(r.clientId)).slice(0, remaining)

  let sent = 0
  let failed = 0
  for (const recipient of queue) {
    const userId = recipient.userId!
    const inserted = await queryOne<{ id: string }>(
      pool,
      `insert into campaign_sends (campaign_id, client_id, user_id, delivered)
       values ($1, $2, $3, false) returning id`,
      [campaign.id, recipient.clientId, userId],
    )
    try {
      // La riga in `notifications` e la push nascono sempre da notify(): è
      // l'unico punto da cui passano, così la campanella e il dispositivo
      // vedono la stessa cosa.
      await notify(
        pool,
        {
          userId,
          kind: 'CAMPAIGN',
          title: personalize(campaign.title, recipient.firstName),
          body: personalize(campaign.body, recipient.firstName),
          payload: { campaignId: campaign.id },
        },
        log,
      )
      await query(pool, 'update campaign_sends set delivered = true where id = $1', [inserted!.id])
      sent++
    } catch (error) {
      failed++
      await query(pool, 'update campaign_sends set delivered = false, error = $2 where id = $1', [
        inserted!.id,
        (error as Error).message.slice(0, 500),
      ])
    }
  }

  // Stato finale. La ripetizione settimanale non è un lavoro messo in coda per
  // fra sette giorni: è la campagna che torna SCHEDULED con la data successiva.
  // Lo stato vive nel database, resta visibile al titolare e sopravvive a un
  // riavvio o a una coda ripulita.
  const totalSent = campaign.sent_count + sent
  const capReached = campaign.send_cap !== null && totalSent >= campaign.send_cap
  const runs = await runsSoFar(campaign.id)
  const repeats = campaign.repeat_weekly && !capReached && runs < WEEKLY_REPEAT_MAX_RUNS
  const nextRun = repeats ? nextWeeklyRun(campaign.scheduled_at) : campaign.scheduled_at

  await query(
    pool,
    `update push_campaigns
        set sent_count      = $2,
            segment_size    = $3,
            reachable_count = $4,
            last_sent_at    = now(),
            status          = $5::campaign_status,
            scheduled_at    = $6
      where id = $1`,
    [campaign.id, totalSent, recipients.length, reachable.length, repeats ? 'SCHEDULED' : 'SENT', nextRun],
  )

  // Stato della campagna nella campanella del titolare (§5). Niente push: è
  // già davanti allo schermo da cui l'ha lanciata.
  for (const ownerId of await ownerUserIds(pool)) {
    await notify(
      pool,
      {
        userId: ownerId,
        kind: 'CAMPAIGN',
        title: 'Campagna inviata',
        body: `«${campaign.name}» è partita verso ${sent} ${sent === 1 ? 'cliente' : 'clienti'}${
          repeats ? '. Prossimo invio fra una settimana.' : '.'
        }`,
        payload: { campaignId: campaign.id },
        push: false,
      },
      log,
    )
  }

  // Solo numeri e identificativi di campagna: nessun nome, nessun contatto.
  log?.info(
    {
      campaignId: campaign.id,
      segmentSize: recipients.length,
      reachable: reachable.length,
      sent,
      failed,
      totalSent,
      runs,
      repeats,
    },
    'campagna push inviata',
  )
  return { sent, failed, skipped: false }
}

/**
 * Mette in coda le campagne programmate la cui ora è passata. L'accodamento
 * riceve una chiave d'occasione (campagna + orario previsto) che pg-boss usa
 * per non creare due volte lo stesso lavoro: se il giro precedente è ancora in
 * corso, il successivo non aggiunge un doppione. E anche se ci riuscisse,
 * `sendCampaign` non rimanderebbe nulla a chi ha già ricevuto oggi.
 */
export async function enqueueDueCampaigns(
  log: FastifyBaseLogger | undefined,
  enqueueSend: (campaignId: string, occurrenceKey: string) => Promise<void>,
): Promise<{ enqueued: number }> {
  const due = await query<{ id: string; scheduled_at: Date }>(
    pool,
    `select id, scheduled_at
       from push_campaigns
      where status = 'SCHEDULED' and scheduled_at is not null and scheduled_at <= now()
      order by scheduled_at`,
  )
  for (const row of due) {
    await enqueueSend(row.id, `${row.id}:${row.scheduled_at.toISOString()}`)
  }
  if (due.length > 0) log?.info({ enqueued: due.length }, 'campagne programmate messe in coda')
  return { enqueued: due.length }
}
