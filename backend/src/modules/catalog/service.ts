import { randomBytes, randomInt } from 'node:crypto'
import type { Db } from '../../db/pool.js'
import { pool, query, queryOne, transaction, pgCode, PG } from '../../db/pool.js'
import type { OperatorDto, SalonDto, ServiceDto, TimeRangeDto } from '../../db/mappers.js'
import { rangeDto } from '../../db/mappers.js'
import { conflict, emailTaken, forbidden, notFound, validation } from '../../lib/errors.js'
import { hashPassword } from '../../lib/auth.js'
import { instantToZoned, isoDayOfWeek, minutesOfTime, type LocalDate } from '../../lib/time.js'

/**
 * Listino, squadra e anagrafica del salone: ciò che le app leggono per
 * disegnare le schermate e che il titolare modifica dal pannello.
 *
 * Due scelte reggono tutto il modulo:
 *  * la lettura è una sola (`catalog`), perché le app chiedevano salone,
 *    servizi e operatori con tre giri di rete e mostravano schermate a metà
 *    quando uno dei tre arrivava in ritardo;
 *  * ogni scrittura che tocca più di una tabella sta in transazione, e le
 *    sostituzioni in blocco (orari, abilitazioni) cancellano e riscrivono
 *    dentro la stessa transazione: non esiste un istante in cui l'operatore
 *    risulta senza turni.
 */

/** Orari settimanali come li scambiano le app: chiavi "1".."7", ISO. */
export type WeeklyHours = Record<string, TimeRangeDto[]>

type HourRow = { day_of_week: number; starts_at: string; ends_at: string }

/** Righe `time` del database -> mappa per giorno, turni in ordine di inizio. */
function weeklyHoursOf(rows: HourRow[]): WeeklyHours {
  const out: WeeklyHours = {}
  for (const row of [...rows].sort((a, b) => a.starts_at.localeCompare(b.starts_at))) {
    const key = String(row.day_of_week)
    const list = out[key] ?? []
    list.push(rangeDto(row.starts_at, row.ends_at))
    out[key] = list
  }
  return out
}

/** Le righe dei turni da scrivere, appiattite: un insert per fascia. */
function weeklyHoursRows(weeklyHours: WeeklyHours): { day: number; start: string; end: string }[] {
  const rows: { day: number; start: string; end: string }[] = []
  for (const [day, ranges] of Object.entries(weeklyHours)) {
    for (const range of ranges) rows.push({ day: Number(day), start: range.start, end: range.end })
  }
  return rows
}

// ------------------------------------------------------------------ salone --

type SalonRow = { name: string; address: string; city: string; phone: string | null; timezone: string }

// Le letture di questo modulo girano anche dentro una transazione, dove `db` è
// una sola connessione: le query si incatenano invece di partire in parallelo.
export async function salon(db: Db): Promise<SalonDto> {
  const row = await queryOne<SalonRow>(db, 'select name, address, city, phone, timezone from salon where id = 1')
  if (!row) throw notFound('Scheda del salone')
  const hours = await query<HourRow>(db, 'select day_of_week, starts_at, ends_at from salon_hours')
  return {
    name: row.name,
    address: row.address,
    city: row.city,
    phone: row.phone,
    timezone: row.timezone,
    weeklyHours: weeklyHoursOf(hours),
  }
}

export type SalonInput = {
  name: string
  address: string
  city: string
  phone?: string | null
  weeklyHours: WeeklyHours
}

/**
 * Anagrafica e orari di apertura in un colpo solo. Gli orari si sostituiscono
 * per intero: più fasce nello stesso giorno restano tutte (turno spezzato
 * mattina/pomeriggio), che è proprio quello che l'editor perdeva per strada
 * tenendo solo la prima fascia.
 */
export async function updateSalon(input: SalonInput): Promise<SalonDto> {
  return transaction(async (db) => {
    const existing = await queryOne<{ id: number }>(db, 'select id from salon where id = 1')
    if (existing) {
      await query(db, 'update salon set name = $1, address = $2, city = $3, phone = $4 where id = 1', [
        input.name, input.address, input.city, input.phone ?? null,
      ])
    } else {
      // Prima riga: l'identity del database assegna da sé l'unico id ammesso.
      await query(db, 'insert into salon (name, address, city, phone) values ($1, $2, $3, $4)', [
        input.name, input.address, input.city, input.phone ?? null,
      ])
    }

    await query(db, 'delete from salon_hours')
    for (const row of weeklyHoursRows(input.weeklyHours)) {
      await query(db, 'insert into salon_hours (day_of_week, starts_at, ends_at) values ($1, $2, $3)', [
        row.day, row.start, row.end,
      ])
    }
    return salon(db)
  })
}

// ----------------------------------------------------------------- servizi --

const SERVICE_SELECT = `
  select id, name, duration_minutes, price_cents, description, featured, active
    from services`

type ServiceRow = {
  id: string; name: string; duration_minutes: number; price_cents: number
  description: string | null; featured: boolean; active: boolean
}

function serviceDto(row: ServiceRow): ServiceDto {
  return {
    id: row.id,
    name: row.name,
    durationMinutes: row.duration_minutes,
    priceCents: row.price_cents,
    description: row.description,
    featured: row.featured,
    active: row.active,
  }
}

/**
 * Tutto il listino, disattivati compresi: lo storico di un cliente rimanda a
 * servizi che il salone non vende più e senza la riga l'app mostrerebbe un
 * appuntamento senza nome. Chi prenota filtra su `active`.
 */
export async function services(db: Db): Promise<ServiceDto[]> {
  const rows = await query<ServiceRow>(db, `${SERVICE_SELECT} order by featured desc, name`)
  return rows.map(serviceDto)
}

async function serviceOrThrow(db: Db, id: string): Promise<ServiceDto> {
  const row = await queryOne<ServiceRow>(db, `${SERVICE_SELECT} where id = $1`, [id])
  if (!row) throw notFound('Servizio')
  return serviceDto(row)
}

export type NewServiceInput = {
  name: string
  durationMinutes: number
  priceCents: number
  description?: string | null
  featured?: boolean
  active?: boolean
  /** Assente = tutta la squadra: un servizio che nessuno esegue non si prenota. */
  operatorIds?: string[]
}

export async function createService(input: NewServiceInput): Promise<ServiceDto> {
  return transaction(async (db) => {
    const inserted = await queryOne<{ id: string }>(
      db,
      `insert into services (name, duration_minutes, price_cents, description, featured, active)
       values ($1, $2, $3, $4, $5, $6)
       returning id`,
      [
        input.name, input.durationMinutes, input.priceCents,
        input.description ?? null, input.featured ?? false, input.active ?? true,
      ],
    )
    const id = inserted!.id

    const operatorIds = input.operatorIds ?? (await query<{ id: string }>(db, 'select id from operators where active')).map((r) => r.id)
    await replaceServiceOperators(db, id, operatorIds)
    return serviceOrThrow(db, id)
  })
}

export type ServicePatch = {
  name?: string
  durationMinutes?: number
  priceCents?: number
  description?: string | null
  featured?: boolean
  active?: boolean
}

/**
 * Modifica parziale: tocca solo le colonne davvero inviate, così azzerare la
 * descrizione (`null`) resta distinguibile dal non averla mandata.
 * I nomi delle colonne vengono da qui, mai dalla richiesta.
 */
export async function updateService(id: string, patch: ServicePatch): Promise<ServiceDto> {
  const columns: Record<string, unknown> = {}
  if (patch.name !== undefined) columns.name = patch.name
  if (patch.durationMinutes !== undefined) columns.duration_minutes = patch.durationMinutes
  if (patch.priceCents !== undefined) columns.price_cents = patch.priceCents
  if (patch.description !== undefined) columns.description = patch.description
  if (patch.featured !== undefined) columns.featured = patch.featured
  if (patch.active !== undefined) columns.active = patch.active

  const keys = Object.keys(columns)
  if (keys.length === 0) return serviceOrThrow(pool, id)

  const assignments = keys.map((key, index) => `${key} = $${index + 2}`).join(', ')
  const updated = await queryOne<{ id: string }>(
    pool,
    `update services set ${assignments} where id = $1 returning id`,
    [id, ...keys.map((key) => columns[key])],
  )
  if (!updated) throw notFound('Servizio')
  return serviceOrThrow(pool, id)
}

/** Riscrive in blocco chi esegue il servizio: prima si svuota, poi si riempie. */
async function replaceServiceOperators(db: Db, serviceId: string, operatorIds: string[]): Promise<void> {
  await query(db, 'delete from operator_services where service_id = $1', [serviceId])
  if (operatorIds.length === 0) return
  try {
    await query(
      db,
      `insert into operator_services (operator_id, service_id)
       select distinct id, $2::uuid from unnest($1::uuid[]) as id`,
      [operatorIds, serviceId],
    )
  } catch (error) {
    // Un id che non esiste più arriva qui come violazione di chiave esterna.
    if (pgCode(error) === PG.foreignKeyViolation) {
      throw validation('operatorIds', 'Uno degli operatori scelti non esiste più')
    }
    throw error
  }
}

/**
 * Chi esegue il servizio, in una sola chiamata: l'app spediva un PATCH per
 * operatore e un errore a metà lasciava le abilitazioni mezze scritte.
 */
export async function setServiceOperators(serviceId: string, operatorIds: string[]): Promise<ServiceDto> {
  return transaction(async (db) => {
    const service = await serviceOrThrow(db, serviceId)
    await assertNoFutureAppointmentsLosingService(db, serviceId, operatorIds)
    await replaceServiceOperators(db, serviceId, operatorIds)
    return service
  })
}

/** Appuntamenti futuri che resterebbero in carico a chi non fa più quel servizio. */
async function assertNoFutureAppointmentsLosingService(
  db: Db, serviceId: string, operatorIds: string[],
): Promise<void> {
  const rows = await query<{ count: number }>(
    db,
    `select count(*)::int as count
       from appointments a
      where a.status in ('CONFIRMED', 'IN_PROGRESS')
        and a.starts_at >= now()
        and not (a.operator_id = any($2::uuid[]))
        and exists (select 1 from appointment_services s where s.appointment_id = a.id and s.service_id = $1)`,
    [serviceId, operatorIds],
  )
  const count = rows[0]?.count ?? 0
  if (count > 0) {
    throw validation(
      'operatorIds',
      count === 1
        ? 'Un appuntamento futuro è in carico a un operatore che non eseguirebbe più questo servizio: spostalo prima di salvare'
        : `${count} appuntamenti futuri sono in carico a operatori che non eseguirebbero più questo servizio: spostali prima di salvare`,
    )
  }
}

// ---------------------------------------------------------------- operatori --

type OperatorRow = {
  id: string; name: string; title: string; bio: string
  specialties: string[]; is_owner: boolean; active: boolean
}

/** Operatori con turni e abilitazioni: tre letture e l'incrocio in memoria. */
async function loadOperators(db: Db, operatorId?: string): Promise<OperatorDto[]> {
  const rows = await query<OperatorRow>(
    db,
    `select id, name, title, bio, specialties, is_owner, active
       from operators
      where ($1::uuid is null or id = $1::uuid)
      order by is_owner desc, name`,
    [operatorId ?? null],
  )
  const hourRows = await query<HourRow & { operator_id: string }>(
    db,
    `select operator_id, day_of_week, starts_at, ends_at
       from operator_hours
      where ($1::uuid is null or operator_id = $1::uuid)`,
    [operatorId ?? null],
  )
  const serviceRows = await query<{ operator_id: string; service_id: string }>(
    db,
    `select operator_id, service_id
       from operator_services
      where ($1::uuid is null or operator_id = $1::uuid)`,
    [operatorId ?? null],
  )

  const hours = new Map<string, HourRow[]>()
  for (const row of hourRows) {
    const list = hours.get(row.operator_id) ?? []
    list.push(row)
    hours.set(row.operator_id, list)
  }
  const serviceIds = new Map<string, string[]>()
  for (const row of serviceRows) {
    const list = serviceIds.get(row.operator_id) ?? []
    list.push(row.service_id)
    serviceIds.set(row.operator_id, list)
  }

  return rows.map((row) => ({
    id: row.id,
    name: row.name,
    title: row.title,
    bio: row.bio,
    specialties: row.specialties,
    isOwner: row.is_owner,
    active: row.active,
    weeklyHours: weeklyHoursOf(hours.get(row.id) ?? []),
    serviceIds: (serviceIds.get(row.id) ?? []).sort(),
  }))
}

export async function operators(db: Db): Promise<OperatorDto[]> {
  return loadOperators(db)
}

async function operatorOrThrow(db: Db, id: string): Promise<OperatorDto> {
  const operator = (await loadOperators(db, id))[0]
  if (!operator) throw notFound('Operatore')
  return operator
}

/**
 * Salone, listino e squadra in una sola risposta: le app aprono con un giro
 * solo. Vuole il pool, non una connessione di transazione: le tre letture
 * partono insieme su connessioni diverse.
 */
export async function catalog(db: Db) {
  const [salonDto, serviceList, operatorList] = await Promise.all([salon(db), services(db), operators(db)])
  return { salon: salonDto, services: serviceList, operators: operatorList }
}

export type NewOperatorInput = {
  name: string
  email: string
  phone: string
  title?: string
  bio?: string
  specialties?: string[]
  weeklyHours?: WeeklyHours
  /** Assente = tutto il listino attivo. */
  serviceIds?: string[]
}

export type NewOperatorResult = {
  operator: OperatorDto
  account: { userId: string; email: string; temporaryPassword: string }
}

/**
 * Password provvisoria consegnata una volta sola al titolare: rispetta le
 * stesse regole del form (lettere e almeno una cifra) e in chiaro non esiste
 * da nessuna parte se non in quella risposta — nel database va solo l'hash.
 */
function temporaryPassword(): string {
  const letters = randomBytes(12).toString('base64url').replace(/[^A-Za-z]/g, '')
  return `Mc${letters.slice(0, 8).padEnd(8, 'x')}${randomInt(1000, 9999)}`
}

/**
 * Profilo operatore e account STAFF nascono insieme. Erano due passaggi
 * distinti e un errore sul secondo lasciava un operatore in agenda che non
 * poteva entrare in app: qui o si creano entrambi o non si crea niente.
 */
export async function createOperator(input: NewOperatorInput): Promise<NewOperatorResult> {
  // L'hash argon2 costa decine di millisecondi: si calcola prima di aprire la
  // transazione, per non tenere occupata una connessione mentre lavora.
  const password = temporaryPassword()
  const passwordHash = await hashPassword(password)

  // Il cognome è tutto ciò che segue il primo spazio; con un nome solo si
  // ripete, perché `users.last_name` non ammette il vuoto.
  const parts = input.name.trim().split(/\s+/)
  const firstName = parts[0]!
  const lastName = parts.slice(1).join(' ') || firstName

  return transaction(async (db) => {
    const taken = await queryOne<{ id: string }>(db, 'select id from users where email = $1', [input.email])
    if (taken) throw emailTaken()

    const insertedOperator = await queryOne<{ id: string }>(
      db,
      `insert into operators (name, title, bio, specialties)
       values ($1, $2, $3, $4::text[])
       returning id`,
      [input.name, input.title ?? 'Barbiere', input.bio ?? '', input.specialties ?? []],
    )
    const operatorId = insertedOperator!.id

    for (const row of weeklyHoursRows(input.weeklyHours ?? {})) {
      await query(
        db,
        'insert into operator_hours (operator_id, day_of_week, starts_at, ends_at) values ($1, $2, $3, $4)',
        [operatorId, row.day, row.start, row.end],
      )
    }

    const serviceIds = input.serviceIds
      ?? (await query<{ id: string }>(db, 'select id from services where active')).map((r) => r.id)
    await replaceOperatorServices(db, operatorId, serviceIds)

    let userId: string
    try {
      const insertedUser = await queryOne<{ id: string }>(
        db,
        `insert into users (first_name, last_name, email, phone, role, password_hash, operator_id)
         values ($1, $2, $3, $4, 'STAFF', $5, $6)
         returning id`,
        [firstName, lastName, input.email, input.phone, passwordHash, operatorId],
      )
      userId = insertedUser!.id
    } catch (error) {
      // Qualcuno ha registrato la stessa email fra il controllo e l'insert.
      if (pgCode(error) === PG.uniqueViolation) throw emailTaken()
      throw error
    }

    return {
      operator: await operatorOrThrow(db, operatorId),
      account: { userId, email: input.email, temporaryPassword: password },
    }
  })
}

export type OperatorPatch = {
  name?: string
  title?: string
  bio?: string
  specialties?: string[]
  active?: boolean
}

export async function updateOperator(id: string, patch: OperatorPatch): Promise<OperatorDto> {
  const columns: Record<string, unknown> = {}
  if (patch.name !== undefined) columns.name = patch.name
  if (patch.title !== undefined) columns.title = patch.title
  if (patch.bio !== undefined) columns.bio = patch.bio
  if (patch.specialties !== undefined) columns.specialties = patch.specialties
  if (patch.active !== undefined) columns.active = patch.active

  const keys = Object.keys(columns)
  if (keys.length === 0) return operatorOrThrow(pool, id)

  const assignments = keys.map((key, index) => `${key} = $${index + 2}`).join(', ')
  const updated = await queryOne<{ id: string }>(
    pool,
    `update operators set ${assignments} where id = $1 returning id`,
    [id, ...keys.map((key) => columns[key])],
  )
  if (!updated) throw notFound('Operatore')
  return operatorOrThrow(pool, id)
}

/**
 * Turni dell'operatore, sostituiti per intero. Prima di scrivere si controlla
 * che nessun appuntamento già confermato finisca fuori dal nuovo orario:
 * stringere i turni senza accorgersene lasciava appuntamenti in un orario in
 * cui l'operatore non lavora più, invisibili in agenda e mai spostati.
 */
export async function setOperatorHours(operatorId: string, weeklyHours: WeeklyHours): Promise<OperatorDto> {
  return transaction(async (db) => {
    await operatorOrThrow(db, operatorId)
    await assertFutureAppointmentsFitHours(db, operatorId, weeklyHours)

    await query(db, 'delete from operator_hours where operator_id = $1', [operatorId])
    for (const row of weeklyHoursRows(weeklyHours)) {
      await query(
        db,
        'insert into operator_hours (operator_id, day_of_week, starts_at, ends_at) values ($1, $2, $3, $4)',
        [operatorId, row.day, row.start, row.end],
      )
    }
    return operatorOrThrow(db, operatorId)
  })
}

async function assertFutureAppointmentsFitHours(
  db: Db, operatorId: string, weeklyHours: WeeklyHours,
): Promise<void> {
  const rows = await query<{ starts_at: Date; ends_at: Date }>(
    db,
    `select starts_at, ends_at
       from appointments
      where operator_id = $1 and status in ('CONFIRMED', 'IN_PROGRESS') and starts_at >= now()`,
    [operatorId],
  )

  const outside = rows.filter((row) => {
    const start = instantToZoned(row.starts_at)
    const end = instantToZoned(row.ends_at)
    // Un appuntamento a cavallo della mezzanotte non sta dentro nessun turno.
    if (end.date !== start.date) return true
    const from = minutesOfTime(start.time)
    const to = minutesOfTime(end.time)
    const ranges = weeklyHours[String(isoDayOfWeek(start.date))] ?? []
    return !ranges.some((range) => minutesOfTime(range.start) <= from && to <= minutesOfTime(range.end))
  }).length

  if (outside > 0) {
    throw validation(
      'hours',
      outside === 1
        ? 'Un appuntamento già confermato resterebbe fuori dal nuovo orario: spostalo prima di salvare'
        : `${outside} appuntamenti già confermati resterebbero fuori dal nuovo orario: spostali prima di salvare`,
    )
  }
}

async function replaceOperatorServices(db: Db, operatorId: string, serviceIds: string[]): Promise<void> {
  await query(db, 'delete from operator_services where operator_id = $1', [operatorId])
  if (serviceIds.length === 0) return
  try {
    await query(
      db,
      `insert into operator_services (operator_id, service_id)
       select distinct $1::uuid, id from unnest($2::uuid[]) as id`,
      [operatorId, serviceIds],
    )
  } catch (error) {
    if (pgCode(error) === PG.foreignKeyViolation) {
      throw validation('serviceIds', 'Uno dei servizi scelti non esiste più')
    }
    throw error
  }
}

/**
 * Servizi che l'operatore esegue, sostituiti per intero. Stesso controllo dei
 * turni: se un appuntamento futuro usa un servizio che l'operatore non farebbe
 * più, il salvataggio si ferma invece di lasciare un appuntamento incoerente.
 */
export async function setOperatorServices(operatorId: string, serviceIds: string[]): Promise<OperatorDto> {
  return transaction(async (db) => {
    await operatorOrThrow(db, operatorId)

    const rows = await query<{ count: number }>(
      db,
      `select count(*)::int as count
         from appointments a
        where a.operator_id = $1
          and a.status in ('CONFIRMED', 'IN_PROGRESS')
          and a.starts_at >= now()
          and exists (
            select 1 from appointment_services s
             where s.appointment_id = a.id and not (s.service_id = any($2::uuid[]))
          )`,
      [operatorId, serviceIds],
    )
    const count = rows[0]?.count ?? 0
    if (count > 0) {
      throw validation(
        'services',
        count === 1
          ? 'Un appuntamento futuro usa un servizio che questo operatore non eseguirebbe più: spostalo prima di salvare'
          : `${count} appuntamenti futuri usano servizi che questo operatore non eseguirebbe più: spostali prima di salvare`,
      )
    }

    await replaceOperatorServices(db, operatorId, serviceIds)
    return operatorOrThrow(db, operatorId)
  })
}

// -------------------------------------------------------------------- ferie --

/** Assenza su più giorni, estremi inclusi. */
export type HolidayDto = {
  id: string
  operatorId: string
  from: LocalDate
  to: LocalDate
  label: string
}

type HolidayRow = { id: string; operator_id: string; from_date: string; to_date: string; label: string }

function holidayDto(row: HolidayRow): HolidayDto {
  return { id: row.id, operatorId: row.operator_id, from: row.from_date, to: row.to_date, label: row.label }
}

export async function holidays(db: Db, operatorId: string): Promise<HolidayDto[]> {
  const rows = await query<HolidayRow>(
    db,
    `select id, operator_id, from_date, to_date, label
       from holidays where operator_id = $1 order by from_date`,
    [operatorId],
  )
  return rows.map(holidayDto)
}

export type NewHolidayInput = {
  operatorId: string
  from: LocalDate
  to: LocalDate
  label?: string
}

export async function addHoliday(input: NewHolidayInput): Promise<HolidayDto> {
  if (input.to < input.from) throw validation('to', 'La fine delle ferie viene prima dell’inizio')
  await operatorOrThrow(pool, input.operatorId)
  try {
    const row = await queryOne<HolidayRow>(
      pool,
      `insert into holidays (operator_id, from_date, to_date, label)
       values ($1, $2, $3, coalesce($4, 'Ferie'))
       returning id, operator_id, from_date, to_date, label`,
      [input.operatorId, input.from, input.to, input.label ?? null],
    )
    return holidayDto(row!)
  } catch (error) {
    // Il vincolo di esclusione del database: queste ferie ne toccano altre.
    if (pgCode(error) === PG.exclusionViolation) {
      throw conflict('Queste date si sovrappongono a un periodo di ferie già inserito')
    }
    throw error
  }
}

/** `restrictToOperatorId` c'è quando a cancellare è un operatore: solo le sue. */
export async function deleteHoliday(id: string, restrictToOperatorId?: string): Promise<void> {
  const row = await queryOne<{ id: string; operator_id: string }>(
    pool, 'select id, operator_id from holidays where id = $1', [id],
  )
  if (!row) return
  if (restrictToOperatorId && row.operator_id !== restrictToOperatorId) throw forbidden('Queste ferie non sono tue')
  await query(pool, 'delete from holidays where id = $1', [id])
}
