/**
 * Dati dimostrativi. Sono gli stessi che le due app già mostrano dal loro
 * livello finto (Android `core/data/fake/DemoSeed.kt`, iOS identico): portarli
 * nel database vuol dire che, staccata la fake repository, l'app continua a
 * mostrare esattamente la stessa giornata — nessuna schermata resta vuota e le
 * demo al titolare non cambiano.
 *
 * Gli appuntamenti sono relativi a **oggi**, come nel seed Kotlin: l'agenda
 * sembra viva qualunque sia il giorno in cui si fa la demo. Proprio per questo
 * non basta copiare le date del seed Kotlin: quel seed metteva appuntamenti
 * anche nei giorni in cui l'operatore non lavora (Antonio il lunedì, Sara il
 * martedì…) e servizi che quell'operatore non esegue. Qui ogni appuntamento
 * viene "posato" dentro l'orario vero del suo operatore, e alla fine un
 * controllo di coerenza rifiuta il seed se qualcosa non torna.
 *
 * Uso:
 *   npm run seed            (rifiuta di girare su un database già popolato)
 *   npm run seed -- --reset (svuota prima le tabelle di dominio)
 */
import { createHash } from 'node:crypto'
import type { PoolClient } from 'pg'

// `config/env` legge process.env al momento dell'import: se le variabili non
// sono già nell'ambiente carichiamo .env prima di toccare qualunque modulo di
// src/, e solo dopo importiamo (import dinamico, altrimenti sarebbe issato).
if (!process.env.DATABASE_URL) {
  try {
    process.loadEnvFile(new URL('../.env', import.meta.url))
  } catch {
    // Nessun .env: vanno bene le variabili d'ambiente già presenti.
  }
}

const { pool, query, queryOne, transaction, closePool } = await import('../src/db/pool.js')
const { hashPassword } = await import('../src/lib/auth.js')
const { normalizePhone } = await import('../src/lib/validation.js')
const { addDays, isoDayOfWeek, minutesOfTime, todayInSalon, zonedToInstant, SALON_TZ } =
  await import('../src/lib/time.js')
/** La connessione dentro la transazione: tutte le scritture passano di lì. */
type Db = PoolClient

type LocalDate = string
type LocalTime = string

// --------------------------------------------------------- identificatori ---

/**
 * UUID deterministici (v5) a partire dalle chiavi leggibili del seed Kotlin
 * ("op_luca", "svc_taglio", …): due esecuzioni producono gli stessi id, quindi
 * i link che si salvano durante una demo restano validi dopo un `--reset`.
 */
const NAMESPACE = Buffer.from('6d656e63617265736565646e73763031', 'hex')

function uid(key: string): string {
  const hash = createHash('sha1').update(NAMESPACE).update(key).digest()
  hash[6] = (hash[6]! & 0x0f) | 0x50 // versione 5
  hash[8] = (hash[8]! & 0x3f) | 0x80 // variante RFC 4122
  const hex = hash.subarray(0, 16).toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`
}

/** I numeri devono passare il check E.164 della tabella: normalizziamo qui. */
function phone(raw: string): string {
  const e164 = normalizePhone(raw)
  if (!e164) throw new Error(`Numero di telefono non valido nel seed: ${raw}`)
  return e164
}

// ---------------------------------------------------------------- anagrafe ---

const OP_ANTONIO = 'op_antonio'
const OP_LUCA = 'op_luca'
const OP_GIULIA = 'op_giulia'
const OP_SARA = 'op_sara'

const SVC_TAGLIO = 'svc_taglio'
const SVC_TAGLIO_BARBA = 'svc_taglio_barba'
const SVC_BABY = 'svc_baby'
const SVC_RASATURA = 'svc_rasatura'
const SVC_COLORE = 'svc_colore'

const CLIENT_MARCO = 'cli_marco'

/** Password unica per tutti gli account dimostrativi. */
const DEMO_PASSWORD = 'mencare2026'

const SALON = {
  name: 'Antonio De Vito · Men Care',
  address: 'Via Scarlatti 120',
  city: 'Napoli',
  phone: phone('081 555 0180'),
}

/** Lunedì–sabato 9–19; la domenica non ha righe, ed è così che il salone è chiuso. */
const SALON_HOURS: Array<[number, LocalTime, LocalTime]> = [1, 2, 3, 4, 5, 6].map(
  (day) => [day, '09:00', '19:00'] as [number, LocalTime, LocalTime],
)

const SERVICES = [
  { key: SVC_TAGLIO, name: 'Shampoo + taglio', duration: 45, price: 1500, description: 'lavaggio e piega', featured: false },
  { key: SVC_TAGLIO_BARBA, name: 'Shampoo + taglio + barba', duration: 60, price: 2000, description: null, featured: false },
  { key: SVC_BABY, name: 'Taglio baby men', duration: 30, price: 1000, description: 'bambini 2–8 anni', featured: false },
  { key: SVC_RASATURA, name: 'Rasatura o sfumatura barba', duration: 30, price: 700, description: 'definizioni a rasoio', featured: true },
  { key: SVC_COLORE, name: 'Sfumatura barba + colore', duration: 10, price: 1000, description: null, featured: true },
] as const

const serviceByKey = new Map<string, (typeof SERVICES)[number]>(SERVICES.map((s) => [s.key, s]))

/** Turno spezzato (mattina e pomeriggio) e turno lungo del pomeriggio. */
const SHIFT_SPLIT: Array<[LocalTime, LocalTime]> = [['09:00', '13:00'], ['14:00', '19:00']]
const SHIFT_LATE: Array<[LocalTime, LocalTime]> = [['10:00', '19:00']]

const OPERATORS = [
  {
    key: OP_ANTONIO, name: 'Antonio De Vito', title: 'Titolare', bio: 'Master barber · dal 2009',
    specialties: ['Classico', 'Rasoio', 'Barba'], isOwner: true,
    days: [2, 3, 4, 5, 6], shift: SHIFT_SPLIT, // il titolare non lavora il lunedì
    services: [SVC_TAGLIO, SVC_TAGLIO_BARBA, SVC_RASATURA],
  },
  {
    key: OP_LUCA, name: 'Luca Ferrante', title: 'Barbiere', bio: 'Barbiere · fade & texture',
    specialties: ['Fade', 'Texture'], isOwner: false,
    days: [1, 2, 3, 4, 5, 6], shift: SHIFT_SPLIT,
    services: [SVC_TAGLIO, SVC_TAGLIO_BARBA, SVC_RASATURA, SVC_COLORE],
  },
  {
    key: OP_GIULIA, name: 'Giulia Marchetti', title: 'Hair stylist', bio: 'Hair stylist · colore e forbici',
    specialties: ['Colore', 'Forbici'], isOwner: false,
    days: [1, 2, 3, 4, 5], shift: SHIFT_LATE, // niente sabato
    services: [SVC_TAGLIO, SVC_BABY],
  },
  {
    key: OP_SARA, name: 'Sara Coppola', title: 'Barber', bio: 'Barber · rasoio tradizionale',
    specialties: ['Barba', 'Rasoio'], isOwner: false,
    days: [3, 4, 5, 6], shift: SHIFT_LATE, // da mercoledì a sabato
    services: [SVC_BABY, SVC_RASATURA, SVC_COLORE],
  },
] as const

const operatorByKey = new Map<string, (typeof OPERATORS)[number]>(OPERATORS.map((o) => [o.key, o]))

type ClientSeed = {
  key: string; first: string; last: string; phone: string; sinceYear: number
  prefServices?: string[]; prefOperator?: string
}

const CLIENTS: ClientSeed[] = [
  { key: CLIENT_MARCO, first: 'Marco', last: 'Esposito', phone: '+39 347 812 4490', sinceYear: 2024, prefServices: [SVC_TAGLIO, SVC_RASATURA], prefOperator: OP_ANTONIO },
  { key: 'cli_davide', first: 'Davide', last: 'Russo', phone: '+39 348 771 2094', sinceYear: 2023, prefServices: [SVC_TAGLIO_BARBA], prefOperator: OP_LUCA },
  { key: 'cli_gennaro', first: 'Gennaro', last: 'Aiello', phone: '+39 333 402 5561', sinceYear: 2022, prefServices: [SVC_RASATURA] },
  { key: 'cli_salvatore', first: 'Salvatore', last: 'Cinque', phone: '+39 339 118 6402', sinceYear: 2021, prefServices: [SVC_TAGLIO_BARBA], prefOperator: OP_ANTONIO },
  { key: 'cli_antonio_g', first: 'Antonio', last: 'Guida', phone: '+39 320 555 7821', sinceYear: 2025 },
  { key: 'cli_ciro', first: 'Ciro', last: 'Espo', phone: '+39 366 902 1145', sinceYear: 2023, prefOperator: OP_LUCA },
  { key: 'cli_paolo', first: 'Paolo', last: 'Ferri', phone: '+39 347 220 9310', sinceYear: 2022 },
  { key: 'cli_luigi', first: 'Luigi', last: 'Amato', phone: '+39 331 774 0921', sinceYear: 2024 },
  { key: 'cli_franco', first: 'Franco', last: 'Vitale', phone: '+39 338 410 5578', sinceYear: 2021 },
  { key: 'cli_peppe', first: 'Giuseppe', last: 'Riccio', phone: '+39 345 660 2287', sinceYear: 2023, prefServices: [SVC_TAGLIO], prefOperator: OP_GIULIA },
  { key: 'cli_mario', first: 'Mario', last: 'Sorrentino', phone: '+39 349 802 6634', sinceYear: 2025 },
  { key: 'cli_enzo', first: 'Vincenzo', last: 'Longo', phone: '+39 328 917 4450', sinceYear: 2022, prefOperator: OP_ANTONIO },
]

type UserSeed = {
  key: string; first: string; last: string; email: string; phone: string
  role: 'CLIENT' | 'STAFF' | 'OWNER'; memberSince: LocalDate
  clientKey?: string; operatorKey?: string
}

/**
 * I tre account della demo più un account per ogni altro operatore: il
 * titolare deve poter far entrare chiunque del team durante una prova.
 */
const USERS: UserSeed[] = [
  { key: 'user_marco', first: 'Marco', last: 'Esposito', email: 'marco.esposito@gmail.com', phone: '+39 347 812 4490', role: 'CLIENT', memberSince: '2024-03-12', clientKey: CLIENT_MARCO },
  { key: 'user_luca', first: 'Luca', last: 'Ferrante', email: 'luca.ferrante@mencare.it', phone: '+39 340 221 8734', role: 'STAFF', memberSince: '2019-05-02', operatorKey: OP_LUCA },
  { key: 'user_antonio', first: 'Antonio', last: 'De Vito', email: 'antonio@mencare.it', phone: '+39 335 660 1200', role: 'OWNER', memberSince: '2009-01-10', operatorKey: OP_ANTONIO },
  { key: 'user_giulia', first: 'Giulia', last: 'Marchetti', email: 'giulia.marchetti@mencare.it', phone: '+39 351 447 2210', role: 'STAFF', memberSince: '2021-09-01', operatorKey: OP_GIULIA },
  { key: 'user_sara', first: 'Sara', last: 'Coppola', email: 'sara.coppola@mencare.it', phone: '+39 342 118 9075', role: 'STAFF', memberSince: '2023-02-15', operatorKey: OP_SARA },
]

// ------------------------------------------------------------------ blocchi ---

type BlockSeed = {
  key: string; operatorKey: string; reason: 'PERMESSO' | 'PAUSA' | 'FERIE' | 'CORSO'
  date: LocalDate; start: LocalTime; end: LocalTime; label: string | null
}

/** La domenica è chiusa: quello che ci cadrebbe sopra slitta al lunedì. */
function openDay(date: LocalDate): LocalDate {
  return isoDayOfWeek(date) === 7 ? addDays(date, 1) : date
}

function buildBlocks(today: LocalDate): BlockSeed[] {
  const blocks: BlockSeed[] = [
    { key: 'blk_lunch_luca', operatorKey: OP_LUCA, reason: 'PAUSA', date: openDay(today), start: '13:00', end: '14:00', label: 'Pausa pranzo' },
    { key: 'blk_corso_giulia', operatorKey: OP_GIULIA, reason: 'CORSO', date: workingDayFor(OP_GIULIA, addDays(today, 2), 1), start: '14:00', end: '17:00', label: 'Formazione' },
  ]
  // Una settimana di ferie di Luca: la scheda operatore del titolare deve
  // avere qualcosa da mostrare, e il cliente non deve poterlo prenotare.
  const holidayStart = addDays(today, 20)
  for (let offset = 0; offset < 7; offset++) {
    blocks.push({
      key: `blk_ferie_luca_${offset}`, operatorKey: OP_LUCA, reason: 'FERIE',
      date: addDays(holidayStart, offset), start: '09:00', end: '19:00', label: null,
    })
  }
  return blocks
}

/** Primo giorno, da `date` in poi (o indietro), in cui quell'operatore lavora. */
function workingDayFor(operatorKey: string, date: LocalDate, step: 1 | -1): LocalDate {
  const operator = operatorByKey.get(operatorKey)!
  let candidate = date
  for (let i = 0; i < 14; i++) {
    if ((operator.days as readonly number[]).includes(isoDayOfWeek(candidate))) return candidate
    candidate = addDays(candidate, step)
  }
  throw new Error(`Nessun giorno lavorativo trovato per ${operatorKey} attorno a ${date}`)
}

// ------------------------------------------------------------- appuntamenti ---

type Status = 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

type AppointmentSeed = {
  clientKey: string; operatorKey: string; serviceKeys: string[]
  date: LocalDate; time: LocalTime; status: Status
  channel?: 'APP' | 'PHONE' | 'WALK_IN'
  cancelledBy?: 'CLIENT' | 'SALON'
  note?: string
}

type PlacedAppointment = AppointmentSeed & { durationMinutes: number; totalPriceCents: number }

type PlacementOptions = {
  /** In avanti per il futuro, indietro per lo storico: la coorte non cambia. */
  direction: 1 | -1
  /**
   * 'shift'  = sposta al primo giorno utile dell'operatore (appuntamenti
   *            personali: devono esserci).
   * 'skip'   = lascia perdere (coorti legate a una giornata precisa: se quel
   *            giorno l'operatore non c'è, semplicemente non ha appuntamenti).
   */
  strategy: 'shift' | 'skip'
}

type Placement = { appointments: PlacedAppointment[]; blocks: BlockSeed[]; moved: number; skipped: number }

/** L'appuntamento deve entrare tutto in un turno dell'operatore. */
function fitsShift(operatorKey: string, time: LocalTime, durationMinutes: number): boolean {
  const operator = operatorByKey.get(operatorKey)!
  const start = minutesOfTime(time)
  const end = start + durationMinutes
  return operator.shift.some(([from, to]) => minutesOfTime(from) <= start && end <= minutesOfTime(to))
}

function isFree(state: Placement, operatorKey: string, date: LocalDate, start: number, end: number): boolean {
  const busyAppointments = state.appointments.some(
    (a) => a.operatorKey === operatorKey && a.date === date && a.status !== 'CANCELLED'
      && start < minutesOfTime(a.time) + a.durationMinutes && minutesOfTime(a.time) < end,
  )
  const busyBlocks = state.blocks.some(
    (b) => b.operatorKey === operatorKey && b.date === date
      && start < minutesOfTime(b.end) && minutesOfTime(b.start) < end,
  )
  return !busyAppointments && !busyBlocks
}

/**
 * Mette l'appuntamento nell'agenda vera dell'operatore. Orario e servizi non
 * si aggiustano da soli: se non entrano nel turno o l'operatore non esegue quel
 * servizio è un errore del seed, e va corretto qui sopra.
 */
function place(state: Placement, seed: AppointmentSeed, options: PlacementOptions): void {
  const operator = operatorByKey.get(seed.operatorKey)
  if (!operator) throw new Error(`Operatore sconosciuto nel seed: ${seed.operatorKey}`)

  const items = seed.serviceKeys.map((key) => {
    const service = serviceByKey.get(key)
    if (!service) throw new Error(`Servizio sconosciuto nel seed: ${key}`)
    if (!(operator.services as readonly string[]).includes(key)) {
      throw new Error(`${operator.name} non esegue "${service.name}": correggi la coorte nel seed`)
    }
    return service
  })
  const durationMinutes = items.reduce((sum, s) => sum + s.duration, 0)
  const totalPriceCents = items.reduce((sum, s) => sum + s.price, 0)
  if (!fitsShift(seed.operatorKey, seed.time, durationMinutes)) {
    throw new Error(`${seed.time} + ${durationMinutes} min non entra nel turno di ${operator.name}: correggi il seed`)
  }

  const start = minutesOfTime(seed.time)
  const end = start + durationMinutes
  let date = seed.date
  for (let attempt = 0; attempt < 14; attempt++) {
    const worksThatDay = (operator.days as readonly number[]).includes(isoDayOfWeek(date))
    if (worksThatDay && isFree(state, seed.operatorKey, date, start, end)) {
      state.appointments.push({ ...seed, date, durationMinutes, totalPriceCents })
      if (date !== seed.date) state.moved++
      return
    }
    if (options.strategy === 'skip') break
    date = addDays(date, options.direction)
  }
  state.skipped++
}

function buildAppointments(today: LocalDate, blocks: BlockSeed[]): Placement {
  const state: Placement = { appointments: [], blocks, moved: 0, skipped: 0 }
  const future: PlacementOptions = { direction: 1, strategy: 'shift' }
  const past: PlacementOptions = { direction: -1, strategy: 'shift' }
  const thatDay: PlacementOptions = { direction: 1, strategy: 'skip' }

  // --- Marco, il cliente della demo: il prossimo appuntamento, uno fra tre
  // settimane, lo storico da riprenotare con un tocco e un annullamento.
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO, SVC_RASATURA], date: today, time: '17:30', status: 'CONFIRMED' }, future)
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO, SVC_RASATURA], date: addDays(today, 21), time: '17:30', status: 'CONFIRMED' }, future)
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO], date: addDays(today, -21), time: '18:00', status: 'COMPLETED' }, past)
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_LUCA, serviceKeys: [SVC_COLORE], date: addDays(today, -60), time: '17:00', status: 'COMPLETED' }, past)
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO, SVC_RASATURA], date: addDays(today, -42), time: '17:30', status: 'COMPLETED' }, past)
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO_BARBA], date: addDays(today, -84), time: '11:00', status: 'COMPLETED' }, past)
  place(state, { clientKey: CLIENT_MARCO, operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO], date: addDays(today, -100), time: '10:00', status: 'CANCELLED', cancelledBy: 'CLIENT' }, past)

  // --- La giornata di Luca: è l'agenda che vede l'operatore appena entra.
  // Luca lavora da lunedì a sabato, quindi "oggi" (o lunedì, se è domenica) c'è sempre.
  const agendaDay = openDay(today)
  place(state, { clientKey: 'cli_mario', operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO], date: agendaDay, time: '09:00', status: 'CONFIRMED' }, thatDay)
  place(state, { clientKey: 'cli_davide', operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO_BARBA], date: agendaDay, time: '10:00', status: 'IN_PROGRESS' }, thatDay)
  place(state, { clientKey: 'cli_gennaro', operatorKey: OP_LUCA, serviceKeys: [SVC_RASATURA], date: agendaDay, time: '11:30', status: 'CONFIRMED' }, thatDay)
  place(state, { clientKey: 'cli_salvatore', operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO_BARBA], date: agendaDay, time: '14:00', status: 'CONFIRMED', note: 'Cliente abituale, sfumatura media.' }, thatDay)
  place(state, { clientKey: 'cli_antonio_g', operatorKey: OP_LUCA, serviceKeys: [SVC_COLORE], date: agendaDay, time: '15:30', status: 'CONFIRMED', channel: 'WALK_IN' }, thatDay)

  // --- Il resto della settimana su più operatori: serve all'agenda
  // settimanale del titolare. Qui la giornata conta, quindi chi non lavora
  // quel giorno resta (giustamente) senza appuntamenti.
  for (let offset = 0; offset <= 5; offset++) {
    const day = addDays(today, offset)
    if (isoDayOfWeek(day) === 7) continue
    place(state, { clientKey: 'cli_ciro', operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO_BARBA], date: day, time: '10:00', status: 'CONFIRMED' }, thatDay)
    place(state, { clientKey: 'cli_peppe', operatorKey: OP_GIULIA, serviceKeys: [SVC_TAGLIO], date: day, time: '11:00', status: 'CONFIRMED' }, thatDay)
    if (offset % 2 === 0) {
      place(state, { clientKey: 'cli_enzo', operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO], date: day, time: '15:00', status: 'CONFIRMED' }, thatDay)
      place(state, { clientKey: 'cli_davide', operatorKey: OP_SARA, serviceKeys: [SVC_RASATURA], date: day, time: '16:00', status: 'CONFIRMED', channel: 'PHONE' }, thatDay)
    }
  }

  // --- Antonio tutto pieno fra una settimana: è il giorno che porta il
  // cliente sulla pagina "Avvisami" della lista d'attesa. Ogni ora del suo
  // turno (9–13 e 14–19) è occupata da un taglio + barba di un'ora.
  const bookedOut = bookedOutDay(today)
  const bookedOutClients = ['cli_davide', 'cli_gennaro', 'cli_salvatore', 'cli_peppe', 'cli_enzo', 'cli_ciro']
  for (const [i, hour] of [9, 10, 11, 12, 14, 15, 16, 17, 18].entries()) {
    place(state, {
      clientKey: bookedOutClients[i % bookedOutClients.length]!,
      operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO_BARBA],
      date: bookedOut, time: `${String(hour).padStart(2, '0')}:00`, status: 'CONFIRMED',
    }, thatDay)
  }

  // --- Volume del mese passato: senza storico i KPI del titolare sono a zero.
  // Orario e servizio di ognuno stanno dentro il turno e fra i servizi che
  // quell'operatore esegue davvero.
  const pastCohort: Array<{ clientKey: string; operatorKey: string; serviceKeys: string[]; time: LocalTime }> = [
    { clientKey: 'cli_davide', operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO], time: '09:00' },
    { clientKey: 'cli_gennaro', operatorKey: OP_SARA, serviceKeys: [SVC_RASATURA], time: '10:00' },
    { clientKey: 'cli_salvatore', operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO_BARBA], time: '11:00' },
    { clientKey: 'cli_peppe', operatorKey: OP_GIULIA, serviceKeys: [SVC_TAGLIO], time: '12:00' },
    { clientKey: 'cli_enzo', operatorKey: OP_ANTONIO, serviceKeys: [SVC_TAGLIO], time: '15:00' },
  ]
  for (let weekAgo = 1; weekAgo <= 4; weekAgo++) {
    for (const [i, entry] of pastCohort.entries()) {
      place(state, { ...entry, date: addDays(today, -7 * weekAgo + i), status: 'COMPLETED' }, past)
    }
  }
  // Due assenze, altrimenti il KPI "no-show" resta finto a zero.
  place(state, { clientKey: 'cli_paolo', operatorKey: OP_LUCA, serviceKeys: [SVC_TAGLIO], date: addDays(today, -10), time: '12:00', status: 'NO_SHOW' }, past)
  place(state, { clientKey: 'cli_franco', operatorKey: OP_SARA, serviceKeys: [SVC_RASATURA], date: addDays(today, -6), time: '17:00', status: 'NO_SHOW' }, past)

  return state
}

/**
 * La giornata di Antonio tutta prenotata, fra una settimana: la stessa su cui
 * Marco aspetta un posto. Una richiesta in lista d'attesa ha senso solo su un
 * giorno davvero pieno per quell'operatore.
 */
function bookedOutDay(today: LocalDate): LocalDate {
  return workingDayFor(OP_ANTONIO, addDays(today, 7), 1)
}

// ------------------------------------------------------------------ scrittura ---

/** Tabelle di dominio: `--reset` le svuota, `schema_migrations` resta. */
const DOMAIN_TABLES = [
  'campaign_sends', 'push_campaigns', 'reminder_rules', 'notification_settings', 'notifications',
  'waitlist_entry_services', 'waitlist_entries', 'holidays', 'time_blocks',
  'appointment_services', 'appointments', 'device_tokens', 'client_notification_prefs',
  'password_resets', 'refresh_tokens', 'users', 'client_preferred_services', 'clients',
  'operator_services', 'operator_hours', 'operators', 'services', 'salon_hours', 'salon',
]

async function main() {
  const reset = process.argv.includes('--reset')
  const today = todayInSalon()

  // Tutto in una transazione: se il controllo finale di coerenza non passa, il
  // database resta com'era invece di restare seminato a metà.
  const placement = await transaction(async (db) => {
    if (reset) {
      // `restart identity` rimette a 1 le identity di salon e notification_settings,
      // che hanno un check "id = 1".
      await db.query(`truncate table ${DOMAIN_TABLES.join(', ')} restart identity cascade`)
      console.log('tabelle di dominio svuotate')
    } else {
      const row = await queryOne<{ total: number }>(
        db,
        `select (select count(*) from salon) + (select count(*) from services)
              + (select count(*) from operators) + (select count(*) from clients)
              + (select count(*) from users) + (select count(*) from appointments) as total`,
      )
      if ((row?.total ?? 0) > 0) {
        throw new Error(
          'Il database contiene già dei dati: non lo sovrascrivo.\n' +
          'Usa "npm run seed -- --reset" per svuotare le tabelle di dominio e ripartire dal seed dimostrativo.',
        )
      }
    }

    await writeSalon(db)
    await writeCatalogue(db)
    await writeClientsAndUsers(db)

    const blocks = buildBlocks(today)
    const state = buildAppointments(today, blocks)
    await writeAppointments(db, state.appointments)
    await writeBlocks(db, blocks)
    await writeWaitlist(db, today)
    await writeNotifications(db)

    await assertCoherent(db)
    return state
  })

  await printSummary(placement)
}

async function writeSalon(db: Db) {
  await query(db, 'insert into salon (name, address, city, phone) values ($1, $2, $3, $4)', [
    SALON.name, SALON.address, SALON.city, SALON.phone,
  ])
  for (const [day, start, end] of SALON_HOURS) {
    await query(db, 'insert into salon_hours (day_of_week, starts_at, ends_at) values ($1, $2, $3)', [day, start, end])
  }
}

async function writeCatalogue(db: Db) {
  for (const service of SERVICES) {
    await query(
      db,
      'insert into services (id, name, duration_minutes, price_cents, description, featured) values ($1, $2, $3, $4, $5, $6)',
      [uid(service.key), service.name, service.duration, service.price, service.description, service.featured],
    )
  }
  for (const operator of OPERATORS) {
    await query(
      db,
      'insert into operators (id, name, title, bio, specialties, is_owner) values ($1, $2, $3, $4, $5, $6)',
      [uid(operator.key), operator.name, operator.title, operator.bio, operator.specialties, operator.isOwner],
    )
    for (const day of operator.days) {
      for (const [start, end] of operator.shift) {
        await query(
          db,
          'insert into operator_hours (operator_id, day_of_week, starts_at, ends_at) values ($1, $2, $3, $4)',
          [uid(operator.key), day, start, end],
        )
      }
    }
    for (const serviceKey of operator.services) {
      await query(db, 'insert into operator_services (operator_id, service_id) values ($1, $2)', [
        uid(operator.key), uid(serviceKey),
      ])
    }
  }
}

async function writeClientsAndUsers(db: Db) {
  for (const client of CLIENTS) {
    await query(
      db,
      `insert into clients (id, first_name, last_name, phone, email, customer_since, preferred_operator_id)
       values ($1, $2, $3, $4, $5, $6, $7)`,
      [
        uid(client.key), client.first, client.last, phone(client.phone),
        `${client.first.toLowerCase()}.${client.last.toLowerCase()}@gmail.com`,
        `${client.sinceYear}-03-12`,
        client.prefOperator ? uid(client.prefOperator) : null,
      ],
    )
    for (const serviceKey of client.prefServices ?? []) {
      await query(db, 'insert into client_preferred_services (client_id, service_id) values ($1, $2)', [
        uid(client.key), uid(serviceKey),
      ])
    }
  }

  // Un solo hash per tutti: l'argon2id costa, e in demo la password è la stessa.
  const passwordHash = await hashPassword(DEMO_PASSWORD)
  for (const user of USERS) {
    await query(
      db,
      `insert into users (id, first_name, last_name, email, phone, role, password_hash, member_since, client_id, operator_id)
       values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
      [
        uid(user.key), user.first, user.last, user.email, phone(user.phone), user.role, passwordHash,
        user.memberSince, user.clientKey ? uid(user.clientKey) : null, user.operatorKey ? uid(user.operatorKey) : null,
      ],
    )
    if (user.role === 'CLIENT') {
      await query(
        db,
        `insert into client_notification_prefs (user_id, appointment_reminder, waitlist_alerts, marketing)
         values ($1, true, true, true)`,
        [uid(user.key)],
      )
    }
  }
}

async function writeAppointments(db: Db, appointments: PlacedAppointment[]) {
  const ownerUserId = uid('user_antonio')
  for (const [index, appointment] of appointments.entries()) {
    const startsAt = zonedToInstant(appointment.date, appointment.time)
    const id = uid(`apt_seed_${index + 1}`)
    const channel = appointment.channel ?? 'APP'
    // Chi ha creato la riga: l'app del cliente per le prenotazioni APP dei
    // clienti con account, il titolare per telefono e walk-in.
    const createdBy = channel === 'APP'
      ? (appointment.clientKey === CLIENT_MARCO ? uid('user_marco') : null)
      : ownerUserId

    await query(
      db,
      `insert into appointments
         (id, client_id, operator_id, starts_at, duration_minutes, total_price_cents, status, channel,
          note_for_operator, cancelled_by, cancelled_at, completed_at, created_by_user_id)
       values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)`,
      [
        id, uid(appointment.clientKey), uid(appointment.operatorKey), startsAt,
        appointment.durationMinutes, appointment.totalPriceCents, appointment.status, channel,
        appointment.note ?? null, appointment.cancelledBy ?? null,
        // I vincoli della tabella legano lo stato ai suoi timestamp: annullato
        // vuole cancelled_at, concluso vuole completed_at.
        appointment.status === 'CANCELLED' ? new Date(startsAt.getTime() - 86_400_000) : null,
        appointment.status === 'COMPLETED' ? new Date(startsAt.getTime() + appointment.durationMinutes * 60_000) : null,
        createdBy,
      ],
    )
    for (const [position, key] of appointment.serviceKeys.entries()) {
      const service = serviceByKey.get(key)!
      await query(
        db,
        `insert into appointment_services (appointment_id, position, service_id, name, duration_minutes, price_cents)
         values ($1, $2, $3, $4, $5, $6)`,
        [id, position, uid(service.key), service.name, service.duration, service.price],
      )
    }
  }
}

async function writeBlocks(db: Db, blocks: BlockSeed[]) {
  for (const block of blocks) {
    await query(
      db,
      `insert into time_blocks (id, operator_id, reason, on_date, starts_at, ends_at, label, created_by_user_id)
       values ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [uid(block.key), uid(block.operatorKey), block.reason, block.date, block.start, block.end, block.label, uid('user_antonio')],
    )
  }
}

async function writeWaitlist(db: Db, today: LocalDate) {
  // Marco in attesa sulla giornata piena di Antonio, a qualsiasi orario: è la
  // richiesta che nasce da "Avvisami", con la posizione in coda.
  const waitlistId = uid('wl_marco')
  const items = [SVC_TAGLIO, SVC_RASATURA].map((key) => serviceByKey.get(key)!)
  await query(
    db,
    `insert into waitlist_entries (id, client_id, on_date, at_time, operator_id, duration_minutes, total_price_cents)
     values ($1, $2, $3, $4, $5, $6, $7)`,
    [
      waitlistId, uid(CLIENT_MARCO), bookedOutDay(today), null, uid(OP_ANTONIO),
      items.reduce((sum, s) => sum + s.duration, 0),
      items.reduce((sum, s) => sum + s.price, 0),
    ],
  )
  for (const [position, service] of items.entries()) {
    await query(db, 'insert into waitlist_entry_services (entry_id, position, service_id) values ($1, $2, $3)', [
      waitlistId, position, uid(service.key),
    ])
  }
}

async function writeNotifications(db: Db) {
  await query(db, 'insert into notification_settings default values')
  for (const hours of [24, 2]) {
    await query(db, 'insert into reminder_rules (id, hours_before) values ($1, $2)', [uid(`rem_${hours}`), hours])
  }

  // Copertura contata sui dati appena seminati, non inventata: il numero che
  // vede il titolare è lo stesso che userebbe l'invio.
  const reachRow = await queryOne<{ segment_size: number; reachable: number }>(
    db,
    `select count(*)::int as segment_size,
            count(*) filter (
              where c.marketing_opt_in and u.id is not null and coalesce(p.marketing, false)
            )::int as reachable
       from clients c
       join client_stats st on st.client_id = c.id
       left join users u on u.client_id = c.id and u.disabled_at is null
       left join client_notification_prefs p on p.user_id = u.id
      where st.last_visit_at is null or st.last_visit_at < now() - interval '60 days'`,
  )
  const segmentSize = reachRow?.segment_size ?? 0
  const reachable = reachRow?.reachable ?? 0

  await query(
    db,
    `insert into push_campaigns (id, name, segment, title, body, reachable_count, segment_size, status)
     values ($1, $2, $3, $4, $5, $6, $7, $8)`,
    [
      uid('camp_inattivi'), 'Recupero inattivi settembre', 'INATTIVI_60', 'Ci manchi, {{nome}}!',
      "È passato un po' di tempo. Prenota il tuo prossimo taglio: {{link}}", reachable, segmentSize, 'DRAFT',
    ],
  )

  // Campanella già popolata per tutti e tre i ruoli: la schermata notifiche
  // non deve mai aprirsi vuota in demo.
  const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000)
  const rows: Array<[string, string, string, string, string, Date, boolean]> = [
    ['ntf_1', 'user_marco', 'BOOKING_REMINDER', 'Promemoria appuntamento', 'Ci vediamo domani alle 17:30 con Antonio. Rispondi per spostare.', minutesAgo(120), false],
    ['ntf_2', 'user_marco', 'WAITLIST_SLOT', "Lista d'attesa", 'Si è liberato uno slot venerdì alle 17:30 con Antonio.', minutesAgo(60 * 24), true],
    ['ntf_3', 'user_luca', 'BOOKING_CONFIRMED', 'Nuova prenotazione', 'Marco Esposito ha prenotato Shampoo + taglio domani alle 10:30.', minutesAgo(40), false],
    ['ntf_4', 'user_luca', 'BOOKING_CANCELLED', 'Appuntamento annullato', "Davide Russo ha annullato l'appuntamento di venerdì alle 16:00.", minutesAgo(300), false],
    ['ntf_5', 'user_luca', 'GENERIC', 'Agenda di domani', 'Domani hai 5 appuntamenti, il primo alle 9:30.', minutesAgo(60 * 48), true],
    ['ntf_6', 'user_antonio', 'BOOKING_CONFIRMED', 'Nuova prenotazione', 'Marco Esposito ha prenotato con Luca domani alle 10:30.', minutesAgo(40), false],
    ['ntf_7', 'user_antonio', 'BOOKING_CANCELLED', 'Appuntamento annullato', "Davide Russo ha annullato l'appuntamento con Giulia di venerdì alle 16:00.", minutesAgo(300), false],
    ['ntf_8', 'user_antonio', 'CAMPAIGN', 'Campagna pronta', `La bozza "Recupero inattivi settembre" raggiunge ${reachable} clienti su ${segmentSize}.`, minutesAgo(60 * 24), true],
  ]
  for (const [key, userKey, kind, title, body, at, read] of rows) {
    await query(
      db,
      'insert into notifications (id, user_id, kind, title, body, created_at, read_at) values ($1, $2, $3, $4, $5, $6, $7)',
      [uid(key), uid(userKey), kind, title, body, at, read ? at : null],
    )
  }
}

// ---------------------------------------------------- controllo di coerenza ---

/**
 * L'invariante che tiene in piedi tutto il resto: ogni appuntamento sta dentro
 * l'orario del suo operatore e del salone, non cade su un blocco o sulle ferie,
 * usa servizi che quell'operatore esegue e non si accavalla con un altro.
 * Se salta anche solo una riga la transazione torna indietro: meglio nessun
 * dato che dati che poi fanno fallire il salvataggio degli orari dal pannello
 * del titolare.
 */
async function assertCoherent(db: Db) {
  const tz = SALON_TZ
  const problems: string[] = []

  const count = async (label: string, sql: string) => {
    const row = await queryOne<{ n: number; sample: string | null }>(db, sql, [tz])
    if ((row?.n ?? 0) > 0) problems.push(`${label}: ${row!.n} (es. ${row!.sample})`)
  }

  const described = `a.id || ' ' || (a.starts_at at time zone $1)::text`

  await count('fuori dall\'orario dell\'operatore', `
    select count(*)::int as n, min(${described}) as sample
      from appointments a
     where not exists (
       select 1 from operator_hours oh
        where oh.operator_id = a.operator_id
          and oh.day_of_week = extract(isodow from (a.starts_at at time zone $1))::int
          and oh.starts_at <= (a.starts_at at time zone $1)::time
          and oh.ends_at   >= (a.ends_at   at time zone $1)::time
     )`)

  await count('fuori dall\'orario di apertura', `
    select count(*)::int as n, min(${described}) as sample
      from appointments a
     where not exists (
       select 1 from salon_hours sh
        where sh.day_of_week = extract(isodow from (a.starts_at at time zone $1))::int
          and sh.starts_at <= (a.starts_at at time zone $1)::time
          and sh.ends_at   >= (a.ends_at   at time zone $1)::time
     )`)

  await count('sopra un blocco', `
    select count(*)::int as n, min(${described}) as sample
      from appointments a
      join time_blocks b
        on b.operator_id = a.operator_id
       and b.on_date = (a.starts_at at time zone $1)::date
       and b.starts_at < (a.ends_at   at time zone $1)::time
       and b.ends_at   > (a.starts_at at time zone $1)::time
     where a.status <> 'CANCELLED'`)

  await count('durante le ferie', `
    select count(*)::int as n, min(${described}) as sample
      from appointments a
      join holidays h
        on h.operator_id = a.operator_id
       and (a.starts_at at time zone $1)::date between h.from_date and h.to_date
     where a.status <> 'CANCELLED'`)

  await count('servizio non eseguito da quell\'operatore', `
    select count(*)::int as n, min(${described}) as sample
      from appointments a
      join appointment_services s on s.appointment_id = a.id
     where $1 is not null
       and not exists (
         select 1 from operator_services os
          where os.operator_id = a.operator_id and os.service_id = s.service_id
       )`)

  await count('sovrapposto a un altro appuntamento', `
    select count(*)::int as n, min(${described}) as sample
      from appointments a
      join appointments b
        on b.id <> a.id and b.operator_id = a.operator_id
       and b.starts_at < a.ends_at and a.starts_at < b.ends_at
     where $1 is not null and a.status <> 'CANCELLED' and b.status <> 'CANCELLED'`)

  if (problems.length > 0) {
    throw new Error(
      `Seed incoerente, nessun dato scritto:\n  - ${problems.join('\n  - ')}\n` +
      'Correggi le coorti in scripts/seed.ts: ogni appuntamento deve stare nel turno del suo operatore.',
    )
  }
}

async function printSummary(placement: Placement) {
  const counts = await queryOne<Record<string, number>>(
    pool,
    `select (select count(*)::int from services)          as servizi,
            (select count(*)::int from operators)         as operatori,
            (select count(*)::int from clients)           as clienti,
            (select count(*)::int from users)             as account,
            (select count(*)::int from appointments)      as appuntamenti,
            (select count(*)::int from time_blocks)       as blocchi,
            (select count(*)::int from waitlist_entries)  as attese,
            (select count(*)::int from reminder_rules)    as promemoria,
            (select count(*)::int from push_campaigns)    as campagne,
            (select count(*)::int from notifications)     as notifiche`,
  )
  const byStatus = await query<{ status: string; n: number }>(
    pool, 'select status, count(*)::int as n from appointments group by status order by status',
  )

  console.log('\nseed completato — coerenza verificata')
  for (const [label, value] of Object.entries(counts ?? {})) {
    console.log(`  ${label.padEnd(14)} ${value}`)
  }
  console.log(`  appuntamenti per stato: ${byStatus.map((r) => `${r.status} ${r.n}`).join(', ')}`)
  console.log(`  spostati al primo giorno utile dell'operatore: ${placement.moved}; saltati (operatore non in turno): ${placement.skipped}`)
  console.log('\naccessi dimostrativi (password unica):')
  for (const user of USERS) {
    console.log(`  ${user.role.padEnd(6)} ${user.email.padEnd(30)} ${DEMO_PASSWORD}`)
  }
}

main()
  .then(closePool)
  .catch(async (error) => {
    console.error(error instanceof Error ? error.message : error)
    await closePool()
    process.exit(1)
  })
