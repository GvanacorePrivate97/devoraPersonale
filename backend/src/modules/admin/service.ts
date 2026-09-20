import type { FastifyBaseLogger } from 'fastify'
import type { Db } from '../../db/pool.js'
import { pool, query, queryOne, pgCode, PG } from '../../db/pool.js'
import { conflict, notFound, validation } from '../../lib/errors.js'
import {
  addDays, daysBetween, eachDate, instantToZoned, isoDayOfWeek, minutesOfTime,
  startOfWeek, todayInSalon, zonedToInstant, SALON_TZ, type LocalDate, type LocalTime,
} from '../../lib/time.js'
import { intersect, workRanges, type Range } from '../../lib/slots.js'
import { isOnHoliday, loadContext, type AvailabilityContext } from '../../services/availability.js'
import { salonNotificationSettings, type SalonNotificationSettings } from '../../services/notifications.js'
import { listClients, segmentCondition, CLIENT_SEGMENTS, type ClientPayload, type ClientSegment } from '../crm/service.js'
import { JOB, enqueue } from '../../jobs/index.js'

/**
 * Area titolare: numeri del salone, regole delle notifiche e campagne push.
 *
 * I KPI sono conti veri sugli appuntamenti. Nella versione finta erano cifre
 * scritte a mano ("€ 9.840 · +12%") per far sembrare pieno un salone vuoto:
 * qui non c'è nessuna base di partenza, se il periodo è vuoto i numeri sono
 * zero — un cruscotto che mente non serve a decidere niente.
 */

export const DASHBOARD_PERIODS = ['DAY', 'WEEK', 'MONTH'] as const
export type DashboardPeriod = (typeof DASHBOARD_PERIODS)[number]

export type OperatorOccupancyDto = { operatorId: string; operatorName: string; percent: number }
/**
 * Un giorno dei prossimi sette: quanto è pieno, quanti aspettano un posto
 * ("Avvisami") e se il salone è chiuso. Serve a vedere a colpo d'occhio dove
 * c'è spazio da riempire e dove la domanda supera l'offerta.
 */
export type UpcomingDayDto = { date: LocalDate; occupancyPercent: number; waitlistCount: number; closed: boolean }

export type DashboardDto = {
  period: DashboardPeriod
  from: LocalDate
  to: LocalDate
  revenueCents: number
  revenueTrendPercent: number
  appointmentCount: number
  noShowPercent: number
  averageTicketCents: number
  operatorOccupancy: OperatorOccupancyDto[]
  upcomingDays: UpcomingDayDto[]
  inactiveClients: ClientPayload[]
}

/** Quanti clienti dormienti mostrare: è una lista da richiamare, non un report. */
const INACTIVE_CLIENTS_SHOWN = 20
/** "Prossimi 7 giorni": oggi compreso. */
export const UPCOMING_DAYS = 7

/** Estremi del periodo, sempre nel fuso del salone. */
export function periodRange(period: DashboardPeriod, today: LocalDate = todayInSalon()): { from: LocalDate; to: LocalDate } {
  switch (period) {
    case 'DAY':
      return { from: today, to: today }
    case 'WEEK': {
      const from = startOfWeek(today)
      return { from, to: addDays(from, 6) }
    }
    case 'MONTH':
    default: {
      const from = `${today.slice(0, 7)}-01`
      const [y, m] = from.split('-').map(Number) as [number, number, number]
      const firstOfNext = m === 12 ? `${y + 1}-01-01` : `${y}-${String(m + 1).padStart(2, '0')}-01`
      return { from, to: addDays(firstOfNext, -1) }
    }
  }
}

/**
 * Periodo precedente della stessa lunghezza, subito prima di questo: per il
 * mese sono i 30/31 giorni che lo precedono, non il mese di calendario, così
 * il confronto è fra finestre uguali e non fra febbraio e gennaio.
 */
function previousRange(from: LocalDate, to: LocalDate): { from: LocalDate; to: LocalDate } {
  const lengthDays = daysBetween(from, to) + 1
  return { from: addDays(from, -lengthDays), to: addDays(from, -1) }
}

type PeriodTotals = {
  revenue_cents: number
  completed_count: number
  appointment_count: number
  no_show_count: number
}

/** Un solo passaggio sugli appuntamenti del periodo: aggrega il database. */
async function periodTotals(db: Db, from: LocalDate, to: LocalDate): Promise<PeriodTotals> {
  const row = await queryOne<PeriodTotals>(
    db,
    `select
       coalesce(sum(total_price_cents) filter (where status = 'COMPLETED'), 0)::bigint as revenue_cents,
       count(*) filter (where status = 'COMPLETED')::int                               as completed_count,
       count(*) filter (where status <> 'CANCELLED')::int                              as appointment_count,
       count(*) filter (where status = 'NO_SHOW')::int                                 as no_show_count
     from appointments
    where starts_at >= $1::timestamptz and starts_at < $2::timestamptz`,
    [zonedToInstant(from, '00:00'), zonedToInstant(addDays(to, 1), '00:00')],
  )
  return row ?? { revenue_cents: 0, completed_count: 0, appointment_count: 0, no_show_count: 0 }
}

/** Unisce intervalli che si toccano: i blocchi possono accavallarsi fra loro. */
function mergeRanges(ranges: Range[]): Range[] {
  const sorted = [...ranges].sort((a, b) => a.start - b.start)
  const out: Range[] = []
  for (const range of sorted) {
    const last = out[out.length - 1]
    if (last && range.start <= last.end) last.end = Math.max(last.end, range.end)
    else out.push({ ...range })
  }
  return out
}

/**
 * Occupazione = minuti prenotati / minuti in cui si poteva davvero lavorare.
 * I minuti lavorabili sono i turni dell'operatore intersecati con l'apertura
 * del salone (`workRanges`, lo stesso motore degli slot), meno le ferie e meno
 * i permessi: se un operatore è in ferie mezza settimana il denominatore cala,
 * altrimenti risulterebbe scarso quando invece non c'era.
 */
async function occupancy(db: Db, from: LocalDate, to: LocalDate): Promise<OperatorOccupancyDto[]> {
  const ctx = await loadContext(db, from, to)

  const [operators, blocks, booked] = await Promise.all([
    query<{ id: string; name: string }>(db, 'select id, name from operators where active order by name'),
    query<{ operator_id: string; on_date: string; starts_at: string; ends_at: string }>(
      db,
      'select operator_id, on_date, starts_at, ends_at from time_blocks where on_date between $1 and $2',
      [from, to],
    ),
    query<{ operator_id: string; minutes: number }>(
      db,
      `select operator_id, coalesce(sum(duration_minutes), 0)::int as minutes
         from appointments
        where starts_at >= $1::timestamptz and starts_at < $2::timestamptz and status <> 'CANCELLED'
        group by operator_id`,
      [zonedToInstant(from, '00:00'), zonedToInstant(addDays(to, 1), '00:00')],
    ),
  ])

  const blocksByKey = new Map<string, Range[]>()
  for (const row of blocks) {
    const key = `${row.operator_id}|${row.on_date}`
    const list = blocksByKey.get(key) ?? []
    list.push({ start: minutesOfTime(row.starts_at.slice(0, 5)), end: minutesOfTime(row.ends_at.slice(0, 5)) })
    blocksByKey.set(key, list)
  }
  const bookedByOperator = new Map(booked.map((row) => [row.operator_id, row.minutes]))
  const dates = eachDate(from, to)

  return operators
    .map((operator) => {
      const hours = ctx.operators.find((op) => op.id === operator.id)?.hours ?? new Map<number, Range[]>()
      let workableMinutes = 0
      for (const date of dates) {
        workableMinutes += workableMinutesOn(ctx, operator.id, hours, date, blocksByKey)
      }
      const bookedMinutes = bookedByOperator.get(operator.id) ?? 0
      // Un fuori-orario concesso a mano può superare il 100%: si mostra pieno.
      const percent = workableMinutes === 0 ? 0 : Math.min(100, Math.round((bookedMinutes * 100) / workableMinutes))
      return { operatorId: operator.id, operatorName: operator.name, percent }
    })
    .sort((a, b) => b.percent - a.percent)
}

/** Minuti in cui un operatore può lavorare in un giorno: turni ∩ apertura, meno ferie e blocchi. */
function workableMinutesOn(
  ctx: AvailabilityContext,
  operatorId: string,
  hours: Map<number, Range[]>,
  date: LocalDate,
  blocksByKey: Map<string, Range[]>,
): number {
  if (isOnHoliday(ctx, operatorId, date)) return 0
  const dow = isoDayOfWeek(date)
  const ranges = workRanges(hours.get(dow) ?? [], ctx.salonHours.get(dow) ?? [])
  const dayBlocks = mergeRanges(blocksByKey.get(`${operatorId}|${date}`) ?? [])
  let total = 0
  for (const range of ranges) {
    let minutes = range.end - range.start
    for (const block of dayBlocks) {
      const overlap = intersect(range, block)
      if (overlap) minutes -= overlap.end - overlap.start
    }
    total += Math.max(minutes, 0)
  }
  return total
}

/**
 * I prossimi sette giorni del salone, oggi compreso: occupazione di tutti gli
 * operatori insieme (minuti prenotati / minuti lavorabili, come sopra) e
 * persone in lista d'attesa per quel giorno. Non segue il selettore di
 * periodo: guarda sempre avanti.
 */
export async function upcomingDays(db: Db, today: LocalDate = todayInSalon()): Promise<UpcomingDayDto[]> {
  const from = today
  const to = addDays(today, UPCOMING_DAYS - 1)
  const ctx = await loadContext(db, from, to)

  const [operators, blocks, booked, waiting] = await Promise.all([
    query<{ id: string }>(db, 'select id from operators where active'),
    query<{ operator_id: string; on_date: string; starts_at: string; ends_at: string }>(
      db,
      'select operator_id, on_date, starts_at, ends_at from time_blocks where on_date between $1 and $2',
      [from, to],
    ),
    query<{ on_date: string; minutes: number }>(
      db,
      `select (starts_at at time zone $3)::date::text as on_date, coalesce(sum(duration_minutes), 0)::int as minutes
         from appointments
        where starts_at >= $1::timestamptz and starts_at < $2::timestamptz
          and status not in ('CANCELLED', 'NO_SHOW')
        group by 1`,
      [zonedToInstant(from, '00:00'), zonedToInstant(addDays(to, 1), '00:00'), SALON_TZ],
    ),
    query<{ on_date: string; count: number }>(
      db,
      `select on_date::text as on_date, count(*)::int as count
         from waitlist_entries
        where status = 'WAITING' and on_date between $1 and $2
        group by on_date`,
      [from, to],
    ),
  ])

  const blocksByKey = new Map<string, Range[]>()
  for (const row of blocks) {
    const key = `${row.operator_id}|${row.on_date}`
    const list = blocksByKey.get(key) ?? []
    list.push({ start: minutesOfTime(row.starts_at.slice(0, 5)), end: minutesOfTime(row.ends_at.slice(0, 5)) })
    blocksByKey.set(key, list)
  }
  const bookedByDate = new Map(booked.map((row) => [row.on_date, row.minutes]))
  const waitingByDate = new Map(waiting.map((row) => [row.on_date, row.count]))

  return eachDate(from, to).map((date) => {
    let workable = 0
    for (const operator of operators) {
      const hours = ctx.operators.find((op) => op.id === operator.id)?.hours ?? new Map<number, Range[]>()
      workable += workableMinutesOn(ctx, operator.id, hours, date, blocksByKey)
    }
    const bookedMinutes = bookedByDate.get(date) ?? 0
    return {
      date,
      occupancyPercent: workable === 0 ? 0 : Math.min(100, Math.round((bookedMinutes * 100) / workable)),
      waitlistCount: waitingByDate.get(date) ?? 0,
      closed: workable === 0,
    }
  })
}

export async function dashboard(period: DashboardPeriod): Promise<DashboardDto> {
  const { from, to } = periodRange(period)
  const previous = previousRange(from, to)

  const [current, before, upcoming, occupancies, inactive] = await Promise.all([
    periodTotals(pool, from, to),
    periodTotals(pool, previous.from, previous.to),
    upcomingDays(pool),
    occupancy(pool, from, to),
    listClients(pool, {
      segment: 'INATTIVI_60',
      includeMoney: true, // rotta da titolare: qui il denaro si vede
      limit: INACTIVE_CLIENTS_SHOWN,
      order: 'LAST_VISIT',
    }),
  ])

  return {
    period,
    from,
    to,
    revenueCents: current.revenue_cents,
    // Senza un periodo precedente da confrontare la tendenza non esiste: 0,
    // non una percentuale inventata su base zero.
    revenueTrendPercent: before.revenue_cents === 0
      ? 0
      : Math.round(((current.revenue_cents - before.revenue_cents) * 100) / before.revenue_cents),
    appointmentCount: current.appointment_count,
    noShowPercent: current.appointment_count === 0
      ? 0
      : Math.round((current.no_show_count * 1000) / current.appointment_count) / 10,
    averageTicketCents: current.completed_count === 0
      ? 0
      : Math.round(current.revenue_cents / current.completed_count),
    operatorOccupancy: occupancies,
    upcomingDays: upcoming,
    inactiveClients: inactive.clients,
  }
}

// ------------------------------------------------- impostazioni notifiche ---

export async function notificationSettings(): Promise<SalonNotificationSettings> {
  return salonNotificationSettings(pool)
}

export async function updateNotificationSettings(input: SalonNotificationSettings): Promise<SalonNotificationSettings> {
  const params = [input.bookingConfirmation, input.cancellationAlert, input.lateOperatorAlert, input.emptyDayPromos]
  const updated = await queryOne<{ id: number }>(
    pool,
    `update notification_settings
        set booking_confirmation = $1, cancellation_alert = $2,
            late_operator_alert = $3, empty_day_promos = $4, updated_at = now()
      where id = 1
      returning id`,
    params,
  )
  if (!updated) {
    // Prima scrittura su un database appena migrato: la riga unica non c'è
    // ancora e l'identity le assegna proprio l'1 che il check pretende.
    await query(
      pool,
      `insert into notification_settings (booking_confirmation, cancellation_alert, late_operator_alert, empty_day_promos)
       values ($1, $2, $3, $4)`,
      params,
    )
  }
  return salonNotificationSettings(pool)
}

export type ReminderRuleDto = { id: string; hoursBefore: number }

export async function reminderRules(): Promise<ReminderRuleDto[]> {
  const rows = await query<{ id: string; hours_before: number }>(
    pool, 'select id, hours_before from reminder_rules order by hours_before',
  )
  return rows.map((row) => ({ id: row.id, hoursBefore: row.hours_before }))
}

export async function addReminderRule(hoursBefore: number): Promise<ReminderRuleDto> {
  try {
    const row = await queryOne<{ id: string; hours_before: number }>(
      pool, 'insert into reminder_rules (hours_before) values ($1) returning id, hours_before', [hoursBefore],
    )
    return { id: row!.id, hoursBefore: row!.hours_before }
  } catch (error) {
    // Due promemoria alla stessa distanza manderebbero due avvisi uguali.
    if (pgCode(error) === PG.uniqueViolation) throw conflict(`C'è già un promemoria a ${hoursBefore} ore`)
    throw error
  }
}

export async function removeReminderRule(id: string): Promise<void> {
  const row = await queryOne<{ id: string }>(pool, 'delete from reminder_rules where id = $1 returning id', [id])
  if (!row) throw notFound('Promemoria')
}

// -------------------------------------------------------------- campagne ---

/**
 * I segmenti che una campagna può avere salvati sono quelli dell'enum del
 * database (`campaign_segment`): un sottoinsieme di quelli del CRM. Il calcolo
 * della copertura invece accetta tutti i segmenti della rubrica, perché serve
 * anche solo per guardare quanta gente si raggiungerebbe.
 */
export const CAMPAIGN_SEGMENTS = ['TUTTI', 'INATTIVI_60', 'TOP_SPESA'] as const
export type CampaignSegment = (typeof CAMPAIGN_SEGMENTS)[number]
export const CAMPAIGN_STATUSES = ['DRAFT', 'SCHEDULED'] as const
export type CampaignStatus = (typeof CAMPAIGN_STATUSES)[number]

export type CampaignDto = {
  id: string
  name: string
  segment: CampaignSegment
  title: string
  body: string
  /** Istante completo, come `startsAt` degli appuntamenti… */
  scheduledAt: string | null
  /** …più l'orario da muro del salone, che è quello che il titolare ha scelto. */
  scheduledDate: LocalDate | null
  scheduledTime: LocalTime | null
  repeatWeekly: boolean
  sendCap: number | null
  reachableCount: number
  segmentSize: number
  sentCount: number
  status: string
  lastSentAt: string | null
}

type CampaignRow = {
  id: string; name: string; segment: CampaignSegment; title: string; body: string
  scheduled_at: Date | null; repeat_weekly: boolean; send_cap: number | null
  reachable_count: number; segment_size: number; sent_count: number
  status: string; last_sent_at: Date | null
}

function campaignDto(row: CampaignRow): CampaignDto {
  const zoned = row.scheduled_at ? instantToZoned(row.scheduled_at) : null
  return {
    id: row.id,
    name: row.name,
    segment: row.segment,
    title: row.title,
    body: row.body,
    scheduledAt: row.scheduled_at ? row.scheduled_at.toISOString() : null,
    scheduledDate: zoned ? zoned.date : null,
    scheduledTime: zoned ? zoned.time : null,
    repeatWeekly: row.repeat_weekly,
    sendCap: row.send_cap,
    reachableCount: row.reachable_count,
    segmentSize: row.segment_size,
    sentCount: row.sent_count,
    status: row.status,
    lastSentAt: row.last_sent_at ? row.last_sent_at.toISOString() : null,
  }
}

const CAMPAIGN_SELECT = `
  select id, name, segment, title, body, scheduled_at, repeat_weekly, send_cap,
         reachable_count, segment_size, sent_count, status, last_sent_at
    from push_campaigns`

export type CampaignReach = { reachable: number; segmentSize: number }

/**
 * Quante persone il segmento contiene e a quante arriverebbe davvero la push.
 * "Raggiungibile" vuol dire quattro sì messi in fila: consenso commerciale
 * sulla scheda, account attivo, interruttore marketing acceso nel profilo e
 * almeno un dispositivo registrato. Prima questi numeri venivano gonfiati con
 * costanti per somigliare al mockup ("198 di 214"): ora sono quelli che sono.
 *
 * La definizione è la stessa, parola per parola, di `campaignRecipients` in
 * `src/jobs/campaigns.ts`: l'anteprima deve contare esattamente chi poi riceve.
 */
export async function campaignReach(db: Db, segment: ClientSegment): Promise<CampaignReach> {
  const params: unknown[] = []
  const condition = segmentCondition(segment, params)
  const row = await queryOne<{ segment_size: number; reachable: number }>(
    db,
    `select
       count(*)::int as segment_size,
       -- "Raggiungibile" vuol dire che la campagna gli arriva: la notifica in
       -- app basta, il token push serve solo per farla squillare. Richiederlo
       -- azzererebbe il conteggio per chi non ha ancora aperto le notifiche.
       count(*) filter (
         where c.marketing_opt_in
           and u.id is not null
           and coalesce(p.marketing, false)
       )::int as reachable
       from clients c
       join client_stats st on st.client_id = c.id
       left join users u on u.client_id = c.id and u.disabled_at is null
       left join client_notification_prefs p on p.user_id = u.id
      where ${condition}`,
    params,
  )
  return { reachable: row?.reachable ?? 0, segmentSize: row?.segment_size ?? 0 }
}

export async function campaigns(): Promise<CampaignDto[]> {
  const rows = await query<CampaignRow>(pool, `${CAMPAIGN_SELECT} order by created_at desc`)
  return rows.map(campaignDto)
}

export type CampaignInput = {
  name: string
  segment: CampaignSegment
  title: string
  body: string
  scheduledAt?: Date | null
  repeatWeekly?: boolean
  sendCap?: number | null
  status?: CampaignStatus
}

/** Una campagna programmata senza data, o con una data passata, non partirebbe mai. */
function assertSchedulable(status: string, scheduledAt: Date | null) {
  if (status !== 'SCHEDULED') return
  if (!scheduledAt) throw validation('scheduledAt', 'Scegli quando inviarla')
  if (scheduledAt.getTime() <= Date.now()) throw validation('scheduledAt', 'La data di invio deve essere futura')
}

export async function createCampaign(input: CampaignInput): Promise<CampaignDto> {
  const status = input.status ?? 'DRAFT'
  const scheduledAt = input.scheduledAt ?? null
  assertSchedulable(status, scheduledAt)

  // La copertura si fotografa al salvataggio: è il numero che il titolare ha
  // visto quando ha deciso di programmare l'invio.
  const reach = await campaignReach(pool, input.segment)
  const row = await queryOne<CampaignRow>(
    pool,
    `insert into push_campaigns
       (name, segment, title, body, scheduled_at, repeat_weekly, send_cap, reachable_count, segment_size, status)
     values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
     returning id, name, segment, title, body, scheduled_at, repeat_weekly, send_cap,
               reachable_count, segment_size, sent_count, status, last_sent_at`,
    [
      input.name, input.segment, input.title, input.body, scheduledAt,
      input.repeatWeekly ?? false, input.sendCap ?? null, reach.reachable, reach.segmentSize, status,
    ],
  )
  return campaignDto(row!)
}

export type CampaignPatch = Partial<CampaignInput>

export async function updateCampaign(id: string, patch: CampaignPatch): Promise<CampaignDto> {
  const current = await queryOne<CampaignRow>(pool, `${CAMPAIGN_SELECT} where id = $1`, [id])
  if (!current) throw notFound('Campagna')
  if (current.status === 'SENT') throw validation('status', 'Una campagna già inviata non si modifica')

  const status = patch.status ?? (current.status as CampaignStatus)
  const scheduledAt = patch.scheduledAt !== undefined ? patch.scheduledAt : current.scheduled_at
  assertSchedulable(status, scheduledAt ?? null)

  const sets: string[] = []
  const params: unknown[] = [id]
  const set = (column: string, value: unknown) => {
    params.push(value)
    sets.push(`${column} = $${params.length}`)
  }
  if (patch.name !== undefined) set('name', patch.name)
  if (patch.title !== undefined) set('title', patch.title)
  if (patch.body !== undefined) set('body', patch.body)
  if (patch.scheduledAt !== undefined) set('scheduled_at', patch.scheduledAt)
  if (patch.repeatWeekly !== undefined) set('repeat_weekly', patch.repeatWeekly)
  if (patch.sendCap !== undefined) set('send_cap', patch.sendCap)
  if (patch.status !== undefined) set('status', patch.status)
  if (patch.segment !== undefined) {
    set('segment', patch.segment)
    // Cambiare pubblico cambia la copertura: si ricalcola, non si eredita.
    const reach = await campaignReach(pool, patch.segment)
    set('reachable_count', reach.reachable)
    set('segment_size', reach.segmentSize)
  }
  if (sets.length === 0) return campaignDto(current)

  const row = await queryOne<CampaignRow>(
    pool,
    `update push_campaigns set ${sets.join(', ')} where id = $1
     returning id, name, segment, title, body, scheduled_at, repeat_weekly, send_cap,
               reachable_count, segment_size, sent_count, status, last_sent_at`,
    params,
  )
  return campaignDto(row!)
}

/**
 * Cucitura con la coda dei lavori. `enqueue` torna `null` quando pg-boss non è
 * partito (database irraggiungibile all'avvio, o modalità test): in quel caso
 * il lavoro non esiste, e va detto — non si può rispondere "inviata" a una
 * campagna che nessuno manderà.
 */
async function enqueueJob(name: string, data: Record<string, unknown>, log?: FastifyBaseLogger): Promise<boolean> {
  try {
    const jobId = await enqueue(name, data)
    return jobId !== null
  } catch (error) {
    log?.error({ err: error, job: name }, 'coda dei lavori non raggiungibile')
    return false
  }
}

export type CampaignSendResult = { campaignId: string; queued: true; reach: CampaignReach }

/**
 * L'invio non parte mai dentro la richiesta HTTP: mille push dentro un handler
 * significherebbero timeout, doppioni a ogni ritentativo e nessun modo di
 * riprendere. Qui si aggiorna la copertura e si mette in coda il lavoro; a
 * mandarle e a segnare lo stato SENT ci pensa il job.
 */
export async function sendCampaign(id: string, log?: FastifyBaseLogger): Promise<CampaignSendResult> {
  const campaign = await queryOne<CampaignRow>(pool, `${CAMPAIGN_SELECT} where id = $1`, [id])
  if (!campaign) throw notFound('Campagna')

  const reach = await campaignReach(pool, campaign.segment)
  if (reach.reachable === 0) {
    throw validation('segment', 'Nessun cliente di questo segmento può ricevere la notifica')
  }
  await query(
    pool, 'update push_campaigns set reachable_count = $2, segment_size = $3 where id = $1',
    [id, reach.reachable, reach.segmentSize],
  )

  const queued = await enqueueJob(JOB.campaignSend, { campaignId: id }, log)
  if (!queued) {
    // Meglio un errore chiaro che una conferma falsa: il titolare deve sapere
    // che la campagna non è partita.
    throw conflict('Invio non disponibile: il servizio delle notifiche programmate non è attivo')
  }
  return { campaignId: id, queued: true, reach }
}

export { CLIENT_SEGMENTS }
