/**
 * Attrezzatura dei test che toccano il database vero. Non usiamo una
 * transazione per test: `book()` apre le sue transazioni su connessioni sue
 * (è proprio la concorrenza che vogliamo mettere alla prova), quindi una
 * transazione esterna non le conterrebbe. Svuotiamo invece le tabelle prima di
 * ogni test: i file girano in sequenza (`fileParallelism: false`), così nessuno
 * cancella i dati di un altro.
 */
import { pool, query, queryOne } from '../src/db/pool.js'
import { addDays, isoDayOfWeek, todayInSalon, type LocalDate, type LocalTime } from '../src/lib/time.js'

export { pool, query, queryOne }

/** Tutte le tabelle di dominio; `schema_migrations` resta dov'è. */
const DOMAIN_TABLES = [
  'campaign_sends', 'push_campaigns', 'reminder_rules', 'notification_settings', 'notifications',
  'waitlist_entry_services', 'waitlist_entries', 'holidays', 'time_blocks',
  'appointment_services', 'appointments', 'device_tokens', 'client_notification_prefs',
  'password_resets', 'refresh_tokens', 'users', 'client_preferred_services', 'clients',
  'operator_services', 'operator_hours', 'operators', 'services', 'salon_hours', 'salon',
]

export async function truncateAll(): Promise<void> {
  // `restart identity` riporta a 1 le identity di salon e notification_settings,
  // che hanno un check "id = 1": senza, il secondo test non potrebbe inserirle.
  await pool.query(`truncate table ${DOMAIN_TABLES.join(', ')} restart identity cascade`)
}

export async function closeDb(): Promise<void> {
  await pool.end()
}

export type HourSpec = [day: number, start: LocalTime, end: LocalTime]

/** Lunedì–sabato 9–19, come il salone vero: la domenica non ha righe. */
export const MON_TO_SAT: HourSpec[] = [1, 2, 3, 4, 5, 6].map((day) => [day, '09:00', '19:00'])

export async function createSalon(hours: HourSpec[] = MON_TO_SAT): Promise<void> {
  await query(pool, 'insert into salon (name, address, city, phone) values ($1, $2, $3, $4)', [
    'Antonio De Vito · Men Care', 'Via Scarlatti 120', 'Napoli', '+390815550180',
  ])
  for (const [day, start, end] of hours) {
    await query(pool, 'insert into salon_hours (day_of_week, starts_at, ends_at) values ($1, $2, $3)', [day, start, end])
  }
}

export async function createService(
  options: { name?: string; duration?: number; price?: number } = {},
): Promise<string> {
  const row = await queryOne<{ id: string }>(
    pool,
    'insert into services (name, duration_minutes, price_cents) values ($1, $2, $3) returning id',
    [options.name ?? 'Shampoo + taglio', options.duration ?? 45, options.price ?? 1500],
  )
  return row!.id
}

export async function createOperator(
  options: { name?: string; isOwner?: boolean; days?: number[]; shift?: Array<[LocalTime, LocalTime]>; serviceIds?: string[] } = {},
): Promise<string> {
  const row = await queryOne<{ id: string }>(
    pool, 'insert into operators (name, is_owner) values ($1, $2) returning id',
    [options.name ?? 'Luca Ferrante', options.isOwner ?? false],
  )
  const id = row!.id
  for (const day of options.days ?? [1, 2, 3, 4, 5, 6]) {
    for (const [start, end] of options.shift ?? [['09:00', '19:00']]) {
      await query(pool, 'insert into operator_hours (operator_id, day_of_week, starts_at, ends_at) values ($1, $2, $3, $4)', [
        id, day, start, end,
      ])
    }
  }
  for (const serviceId of options.serviceIds ?? []) {
    await query(pool, 'insert into operator_services (operator_id, service_id) values ($1, $2)', [id, serviceId])
  }
  return id
}

export async function createClient(
  options: { first?: string; last?: string; phone?: string } = {},
): Promise<string> {
  const row = await queryOne<{ id: string }>(
    pool,
    'insert into clients (first_name, last_name, phone) values ($1, $2, $3) returning id',
    [options.first ?? 'Marco', options.last ?? 'Esposito', options.phone ?? randomPhone()],
  )
  return row!.id
}

let phoneSeq = 0
/** Il numero è unico per tabella: ne serve uno diverso a ogni cliente. */
function randomPhone(): string {
  phoneSeq += 1
  return `+3934781${String(phoneSeq).padStart(5, '0')}`
}

/** Inserisce un appuntamento senza passare da `book()`: serve per preparare casi che il motore degli slot non permetterebbe di creare. */
export async function insertAppointment(options: {
  clientId: string; operatorId: string; startsAt: Date; durationMinutes?: number
  status?: 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
  totalPriceCents?: number
}): Promise<string> {
  const status = options.status ?? 'CONFIRMED'
  const row = await queryOne<{ id: string }>(
    pool,
    `insert into appointments
       (client_id, operator_id, starts_at, duration_minutes, total_price_cents, status, cancelled_at, cancelled_by, completed_at)
     values ($1, $2, $3, $4, $5, $6, $7, $8, $9) returning id`,
    [
      options.clientId, options.operatorId, options.startsAt, options.durationMinutes ?? 45,
      options.totalPriceCents ?? 1500, status,
      status === 'CANCELLED' ? new Date() : null,
      status === 'CANCELLED' ? 'SALON' : null,
      status === 'COMPLETED' ? new Date() : null,
    ],
  )
  return row!.id
}

/**
 * Un giorno futuro in cui il salone è aperto: i test prenotano sempre in
 * avanti, così il preavviso minimo e il "giorno passato" non li disturbano.
 */
export function workingDate(daysAhead = 3): LocalDate {
  let date = addDays(todayInSalon(), daysAhead)
  while (isoDayOfWeek(date) === 7) date = addDays(date, 1)
  return date
}
