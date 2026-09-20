import type { Db } from '../../db/pool.js'
import { pool, query, queryOne, transaction, pgCode, PG } from '../../db/pool.js'
import type { ClientDto } from '../../db/mappers.js'
import { conflict, notFound, validation } from '../../lib/errors.js'
import { addDays, daysBetween, instantToZoned, todayInSalon, zonedToInstant, type LocalDate, type LocalTime } from '../../lib/time.js'

/**
 * Rubrica clienti. La scheda è la stessa per l'operatore e per il titolare, ma
 * non mostra le stesse cose: i numeri economici li vede solo il titolare, e la
 * differenza si fa qui — nella query e nel DTO — non nella schermata.
 *
 * I contatori (visite, speso, no-show, ultima visita) arrivano dalla vista
 * `client_stats`: sono calcolati dagli appuntamenti, non salvati, quindi un
 * annullamento o un "segna completato" ripetuto non li sporca mai.
 */

export const CLIENT_SEGMENTS = ['TUTTI', 'FEDELI', 'INATTIVI_60', 'NO_SHOW', 'TOP_SPESA'] as const
export type ClientSegment = (typeof CLIENT_SEGMENTS)[number]

/** Giorni senza passare in salone dopo i quali il cliente è "inattivo". */
export const INACTIVITY_DAYS = 60
/** Visite completate da cui in poi il cliente è "fedele". */
export const LOYAL_VISIT_COUNT = 10
/** Taglio dei migliori per spesa: il 10% più alto, non una soglia fissa. */
export const TOP_SPEND_PERCENTILE = 0.9

const DEFAULT_LIMIT = 30
const MAX_LIMIT = 100
/** Sotto le tre cifre un numero non cerca nulla: matcherebbe mezza rubrica. */
const MIN_PHONE_DIGITS = 3

/**
 * Istante prima del quale una visita conta come "vecchia". Il conto parte dalla
 * mezzanotte del salone, non da "adesso meno 60 giorni": due clienti visti lo
 * stesso giorno devono cadere nello stesso segmento.
 */
export function inactivityCutoff(today: LocalDate = todayInSalon()): Date {
  return zonedToInstant(addDays(today, -INACTIVITY_DAYS), '00:00')
}

/**
 * Condizione SQL del segmento. Il parametro dell'istante di inattività lo
 * aggiunge questa funzione stessa, e solo dove serve davvero: Postgres rifiuta
 * una query che dichiara un parametro e poi non lo usa.
 * Il filtro lo fa il database, non un ciclo su tutta la rubrica.
 */
export function segmentCondition(segment: ClientSegment, params: unknown[]): string {
  const cutoff = () => {
    params.push(inactivityCutoff())
    return `$${params.length}::timestamptz`
  }
  switch (segment) {
    case 'FEDELI':
      // Fedele è chi torna *e* c'è ancora: chi ha 20 visite ma manca da un anno
      // è un cliente da recuperare, non un fedele.
      return `(st.visit_count >= ${LOYAL_VISIT_COUNT}
               and st.last_visit_at is not null and st.last_visit_at >= ${cutoff()})`
    case 'INATTIVI_60':
      return `(st.last_visit_at is null or st.last_visit_at < ${cutoff()})`
    case 'NO_SHOW':
      return '(st.no_show_count > 0)'
    case 'TOP_SPESA':
      // Prima era una soglia fissa a 400 € scelta a caso: cambiando il listino
      // o la stagione quel numero diceva tutto e niente. Ora il taglio è
      // relativo alla rubrica di oggi — il decimo più alto di chi ha speso.
      return `(st.lifetime_spend_cents > 0 and st.lifetime_spend_cents >= (
                 select percentile_cont(${TOP_SPEND_PERCENTILE}) within group (order by s2.lifetime_spend_cents)
                   from client_stats s2
                  where s2.lifetime_spend_cents > 0))`
    case 'TUTTI':
    default:
      return 'true'
  }
}

/**
 * Scheda cliente come la vede chi ha chiesto: `lifetimeSpendCents` esiste solo
 * per il titolare. È un `Omit` apposta, così il compilatore ricorda che quel
 * campo può non esserci.
 */
export type ClientPayload = Omit<ClientDto, 'lifetimeSpendCents'> & { lifetimeSpendCents?: number }

export type ClientHistoryEntry = {
  id: string
  operatorId: string
  date: LocalDate
  time: LocalTime
  startsAt: string
  durationMinutes: number
  status: string
  channel: string
  serviceIds: string[]
  serviceNames: string[]
  /** Come sopra: il prezzo dell'appuntamento è roba da titolare. */
  totalPriceCents?: number
}

/**
 * Abitudini del cliente, calcolate dalle visite completate: l'operatore con
 * cui è stato più spesso (a parità, il più recente) e ogni quanti giorni torna
 * in media. Null finché non ci sono abbastanza visite per dirlo.
 */
export type ClientInsights = { favoriteOperatorId: string | null; averageDaysBetweenVisits: number | null }

export type ClientDetail = { client: ClientPayload; insights: ClientInsights; appointments: ClientHistoryEntry[] }

export function clientInsights(history: Pick<ClientHistoryEntry, 'operatorId' | 'date' | 'status'>[]): ClientInsights {
  const completed = history.filter((entry) => entry.status === 'COMPLETED')

  // La storia arriva dalla più recente: a parità di visite vince chi l'ha
  // servito per ultimo, che è anche il primo incontrato.
  const counts = new Map<string, number>()
  for (const entry of completed) counts.set(entry.operatorId, (counts.get(entry.operatorId) ?? 0) + 1)
  let favoriteOperatorId: string | null = null
  let best = 0
  for (const entry of completed) {
    const count = counts.get(entry.operatorId)!
    if (count > best) {
      best = count
      favoriteOperatorId = entry.operatorId
    }
  }

  const dates = [...new Set(completed.map((entry) => entry.date))].sort()
  let averageDaysBetweenVisits: number | null = null
  if (dates.length >= 2) {
    let total = 0
    for (let i = 1; i < dates.length; i++) total += daysBetween(dates[i - 1]!, dates[i]!)
    // Per difetto, come nel tab Appuntamenti del cliente.
    averageDaysBetweenVisits = Math.floor(total / (dates.length - 1))
  }
  return { favoriteOperatorId, averageDaysBetweenVisits }
}

type ClientRow = {
  id: string
  first_name: string
  last_name: string
  phone: string
  email: string | null
  customer_since: string
  marketing_opt_in: boolean
  preferred_operator_id: string | null
  preferred_service_ids: string[]
  visit_count: number
  no_show_count: number
  last_visit_at: Date | null
  lifetime_spend_cents: number | null
}

function toPayload(row: ClientRow, includeMoney: boolean): ClientPayload {
  const payload: ClientPayload = {
    id: row.id,
    firstName: row.first_name,
    lastName: row.last_name,
    phone: row.phone,
    email: row.email,
    customerSince: row.customer_since,
    visitCount: row.visit_count,
    noShowCount: row.no_show_count,
    lastVisit: row.last_visit_at ? instantToZoned(row.last_visit_at).date : null,
    preferredServiceIds: row.preferred_service_ids,
    preferredOperatorId: row.preferred_operator_id,
    marketingOptIn: row.marketing_opt_in,
  }
  if (includeMoney) payload.lifetimeSpendCents = row.lifetime_spend_cents ?? 0
  return payload
}

/** Colonne della scheda; il totale speso entra nella select solo se ammesso. */
function clientSelect(includeMoney: boolean): string {
  return `
    select c.id, c.first_name, c.last_name, c.phone, c.email, c.customer_since,
           c.marketing_opt_in, c.preferred_operator_id,
           st.visit_count, st.no_show_count, st.last_visit_at,
           ${includeMoney ? 'st.lifetime_spend_cents' : 'null::bigint as lifetime_spend_cents'},
           coalesce(
             (select array_agg(ps.service_id) from client_preferred_services ps where ps.client_id = c.id),
             '{}'
           ) as preferred_service_ids
      from clients c
      join client_stats st on st.client_id = c.id`
}

/** `%` e `_` scritti dall'utente sono caratteri, non caratteri jolly. */
function likeTerm(term: string): string {
  return `%${term.replace(/[\\%_]/g, (ch) => `\\${ch}`)}%`
}

function encodeCursor(row: { last_name: string; first_name: string; id: string }): string {
  return Buffer.from(JSON.stringify([row.last_name, row.first_name, row.id])).toString('base64url')
}

function decodeCursor(cursor: string): [string, string, string] {
  try {
    const parts: unknown = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'))
    if (!Array.isArray(parts) || parts.length !== 3 || parts.some((p) => typeof p !== 'string')) {
      throw new Error('cursor')
    }
    return parts as [string, string, string]
  } catch {
    throw validation('cursor', 'Pagina non valida')
  }
}

export type ListClientsOptions = {
  query?: string | null
  segment?: ClientSegment
  limit?: number
  cursor?: string | null
  /** Vero solo per il titolare. */
  includeMoney: boolean
  /** La rubrica va in ordine alfabetico; la dashboard per ultima visita. */
  order?: 'NAME' | 'LAST_VISIT'
}

export type ClientPage = { clients: ClientPayload[]; nextCursor: string | null }

export async function listClients(db: Db, options: ListClientsOptions): Promise<ClientPage> {
  const limit = Math.min(Math.max(Math.trunc(options.limit ?? DEFAULT_LIMIT), 1), MAX_LIMIT)
  const order = options.order ?? 'NAME'

  const params: unknown[] = []
  const conditions: string[] = [segmentCondition(options.segment ?? 'TUTTI', params)]

  const term = (options.query ?? '').trim()
  if (term.length > 0) {
    params.push(likeTerm(term))
    const textParam = params.length
    // Il telefono si confronta a sole cifre da tutte e due le parti: in rubrica
    // sta in E.164 (+39…), ma si cerca scrivendolo come viene.
    const digits = term.replace(/[^0-9]/g, '')
    params.push(digits.length >= MIN_PHONE_DIGITS ? `%${digits}%` : null)
    const digitsParam = params.length
    conditions.push(`(
      c.first_name ilike $${textParam} escape '\\'
      or c.last_name ilike $${textParam} escape '\\'
      or (c.first_name || ' ' || c.last_name) ilike $${textParam} escape '\\'
      or c.email ilike $${textParam} escape '\\'
      or ($${digitsParam}::text is not null and regexp_replace(c.phone, '[^0-9]', '', 'g') like $${digitsParam})
    )`)
  }

  if (options.cursor && order === 'NAME') {
    // Paginazione a chiave: niente OFFSET, così una scheda creata nel frattempo
    // non fa saltare o ripetere una riga.
    const [lastName, firstName, id] = decodeCursor(options.cursor)
    params.push(lastName, firstName, id)
    const base = params.length - 2
    conditions.push(`(c.last_name, c.first_name, c.id) > ($${base}, $${base + 1}, $${base + 2}::uuid)`)
  }

  const orderBy = order === 'NAME'
    ? 'order by c.last_name, c.first_name, c.id'
    : 'order by st.last_visit_at desc nulls last, c.last_name, c.first_name, c.id'

  params.push(limit)
  const rows = await query<ClientRow & { last_name: string }>(
    db,
    `${clientSelect(options.includeMoney)}
      where ${conditions.join(' and ')}
      ${orderBy}
      limit $${params.length}`,
    params,
  )

  const clients = rows.map((row) => toPayload(row, options.includeMoney))
  // Cursore solo a pagina piena: se è corta, non c'è un dopo.
  const last = rows.length === limit ? rows[rows.length - 1] : undefined
  return { clients, nextCursor: last && order === 'NAME' ? encodeCursor(last) : null }
}

export async function clientById(db: Db, id: string, includeMoney: boolean): Promise<ClientPayload | null> {
  const row = await queryOne<ClientRow>(db, `${clientSelect(includeMoney)} where c.id = $1`, [id])
  return row ? toPayload(row, includeMoney) : null
}

/** Scheda completa: anagrafica più storico, che è il motivo per cui si apre. */
export async function clientDetail(db: Db, id: string, includeMoney: boolean): Promise<ClientDetail> {
  const client = await clientById(db, id, includeMoney)
  if (!client) throw notFound('Cliente')

  const rows = await query<{
    id: string; operator_id: string; starts_at: Date; duration_minutes: number
    status: string; channel: string; total_price_cents: number | null
    service_ids: string[]; service_names: string[]
  }>(
    db,
    `select a.id, a.operator_id, a.starts_at, a.duration_minutes, a.status, a.channel,
            ${includeMoney ? 'a.total_price_cents' : 'null::bigint as total_price_cents'},
            coalesce(
              (select array_agg(s.service_id order by s.position) from appointment_services s where s.appointment_id = a.id),
              '{}'
            ) as service_ids,
            coalesce(
              (select array_agg(s.name order by s.position) from appointment_services s where s.appointment_id = a.id),
              '{}'
            ) as service_names
       from appointments a
      where a.client_id = $1
      order by a.starts_at desc`,
    [id],
  )

  const appointments = rows.map((row) => {
    const zoned = instantToZoned(row.starts_at)
    const entry: ClientHistoryEntry = {
      id: row.id,
      operatorId: row.operator_id,
      date: zoned.date,
      time: zoned.time,
      startsAt: row.starts_at.toISOString(),
      durationMinutes: row.duration_minutes,
      status: row.status,
      channel: row.channel,
      serviceIds: row.service_ids,
      serviceNames: row.service_names,
    }
    if (includeMoney) entry.totalPriceCents = row.total_price_cents ?? 0
    return entry
  })

  return { client, insights: clientInsights(appointments), appointments }
}

export type CreateClientInput = {
  firstName: string
  lastName: string
  /** Già normalizzato in E.164 dallo schema zod. */
  phone: string
  email?: string | null
}

/**
 * Scheda nuova, quella che nasce dal foglio di prenotazione manuale. Il numero
 * di telefono è l'identità del cliente in salone: se c'è già, si dice di chi è
 * invece di creare un doppione muto (prima succedeva, e la rubrica si sdoppiava).
 */
export async function createClient(input: CreateClientInput, includeMoney: boolean): Promise<ClientPayload> {
  const existing = await queryOne<{ id: string; first_name: string; last_name: string }>(
    pool, 'select id, first_name, last_name from clients where phone = $1', [input.phone],
  )
  if (existing) {
    throw conflict(`Questo numero è già la scheda di ${existing.first_name} ${existing.last_name}`)
  }

  let id: string
  try {
    const inserted = await queryOne<{ id: string }>(
      pool,
      `insert into clients (first_name, last_name, phone, email)
       values ($1, $2, $3, $4) returning id`,
      [input.firstName, input.lastName, input.phone, input.email ?? null],
    )
    id = inserted!.id
  } catch (error) {
    // Corsa fra due tablet sullo stesso banco: l'indice unico ha l'ultima parola.
    if (pgCode(error) === PG.uniqueViolation) throw duplicateClient(input)
    throw error
  }
  return (await clientById(pool, id, includeMoney))!
}

function duplicateClient(input: CreateClientInput) {
  return conflict(`Esiste già una scheda con questo ${input.email ? 'numero o questa email' : 'numero'}`)
}

export type UpdateClientInput = {
  firstName?: string
  lastName?: string
  phone?: string
  email?: string | null
  marketingOptIn?: boolean
  preferredOperatorId?: string | null
  preferredServiceIds?: string[]
}

export async function updateClient(
  id: string,
  input: UpdateClientInput,
  includeMoney: boolean,
): Promise<ClientPayload> {
  await transaction(async (db) => {
    const exists = await queryOne<{ id: string }>(db, 'select id from clients where id = $1 for update', [id])
    if (!exists) throw notFound('Cliente')

    // Solo i campi arrivati davvero: un PATCH non azzera ciò che non nomina.
    const sets: string[] = []
    const params: unknown[] = [id]
    const set = (column: string, value: unknown) => {
      params.push(value)
      sets.push(`${column} = $${params.length}`)
    }
    if (input.firstName !== undefined) set('first_name', input.firstName)
    if (input.lastName !== undefined) set('last_name', input.lastName)
    if (input.phone !== undefined) set('phone', input.phone)
    if (input.email !== undefined) set('email', input.email)
    if (input.marketingOptIn !== undefined) set('marketing_opt_in', input.marketingOptIn)
    if (input.preferredOperatorId !== undefined) set('preferred_operator_id', input.preferredOperatorId)

    if (sets.length > 0) {
      try {
        await query(db, `update clients set ${sets.join(', ')} where id = $1`, params)
      } catch (error) {
        if (pgCode(error) === PG.uniqueViolation) {
          throw conflict('Numero o email già assegnati a un altro cliente')
        }
        throw error
      }
    }

    if (input.preferredServiceIds !== undefined) {
      await query(db, 'delete from client_preferred_services where client_id = $1', [id])
      if (input.preferredServiceIds.length > 0) {
        try {
          await query(
            db,
            `insert into client_preferred_services (client_id, service_id)
             select $1, unnest($2::uuid[])`,
            [id, input.preferredServiceIds],
          )
        } catch (error) {
          if (pgCode(error) === PG.foreignKeyViolation) {
            throw validation('preferredServiceIds', 'Uno dei servizi indicati non esiste')
          }
          throw error
        }
      }
    }
  })

  return (await clientById(pool, id, includeMoney))!
}
