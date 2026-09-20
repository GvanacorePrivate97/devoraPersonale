import { afterAll, beforeEach, describe, expect, it } from 'vitest'
import { pgCode, PG } from '../src/db/pool.js'
import { zonedToInstant } from '../src/lib/time.js'
import {
  closeDb, createClient, createOperator, createSalon, createService, insertAppointment,
  pool, query, queryOne, truncateAll, workingDate,
} from './helpers.js'

/**
 * Lo schema è l'ultima difesa: anche se un giorno una route sbagliasse i
 * controlli, il database non deve accettare due appuntamenti sovrapposti, un
 * account legato alla scheda sbagliata o due ferie che si accavallano. Questi
 * test parlano direttamente con Postgres, senza passare dai servizi.
 */

/** Codice SQLSTATE dell'errore, oppure null se la scrittura è passata. */
async function failureCode(run: () => Promise<unknown>): Promise<string | null> {
  try {
    await run()
    return null
  } catch (error) {
    return pgCode(error) ?? null
  }
}

let operatorId: string
let otherOperatorId: string
let clientId: string
const date = workingDate(4)

beforeEach(async () => {
  await truncateAll()
  await createSalon()
  operatorId = await createOperator({ name: 'Luca Ferrante' })
  otherOperatorId = await createOperator({ name: 'Sara Coppola' })
  clientId = await createClient()
})

afterAll(closeDb)

describe('appuntamenti: vincolo di non sovrapposizione', () => {
  it('rifiuta due appuntamenti attivi che si accavallano sullo stesso operatore', async () => {
    await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00'), durationMinutes: 45 })
    const code = await failureCode(() =>
      insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:30'), durationMinutes: 30 }),
    )
    expect(code).toBe(PG.exclusionViolation)
  })

  it('accetta due appuntamenti attaccati: la fine coincide con l\'inizio', async () => {
    await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00'), durationMinutes: 45 })
    await expect(
      insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:45'), durationMinutes: 30 }),
    ).resolves.toBeTypeOf('string')
  })

  it('lo stesso orario su due operatori diversi è legittimo', async () => {
    await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00') })
    await expect(
      insertAppointment({ clientId, operatorId: otherOperatorId, startsAt: zonedToInstant(date, '10:00') }),
    ).resolves.toBeTypeOf('string')
  })

  it('un appuntamento annullato non occupa più il suo orario', async () => {
    await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00'), status: 'CANCELLED' })
    await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00'), status: 'COMPLETED' })
    await expect(
      insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00') }),
    ).resolves.toBeTypeOf('string')
  })

  it('annullare libera l\'orario anche dopo l\'inserimento', async () => {
    const id = await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00') })
    await query(pool, `update appointments set status = 'CANCELLED', cancelled_at = now(), cancelled_by = 'CLIENT' where id = $1`, [id])
    await expect(
      insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00') }),
    ).resolves.toBeTypeOf('string')
  })
})

describe('appuntamenti: colonne coerenti', () => {
  it('ends_at lo calcola il trigger, non chi scrive', async () => {
    const id = await insertAppointment({ clientId, operatorId, startsAt: zonedToInstant(date, '10:00'), durationMinutes: 75 })
    const row = await queryOne<{ starts_at: Date; ends_at: Date }>(pool, 'select starts_at, ends_at from appointments where id = $1', [id])
    expect(row!.ends_at.getTime() - row!.starts_at.getTime()).toBe(75 * 60_000)
  })

  it('annullato senza cancelled_at, o concluso senza completed_at, non passa', async () => {
    const insert = (status: string, extra: string) =>
      query(pool, `insert into appointments (client_id, operator_id, starts_at, duration_minutes, total_price_cents, status${extra ? `, ${extra}` : ''})
                   values ($1, $2, $3, 45, 1500, '${status}'${extra ? ', now()' : ''})`, [clientId, operatorId, zonedToInstant(date, '12:00')])

    expect(await failureCode(() => insert('CANCELLED', ''))).toBe(PG.checkViolation)
    expect(await failureCode(() => insert('COMPLETED', ''))).toBe(PG.checkViolation)
    // Annullato con la data ma senza chi ha annullato: nemmeno.
    expect(await failureCode(() => insert('CANCELLED', 'cancelled_at'))).toBe(PG.checkViolation)
  })
})

describe('account: il ruolo decide a cosa è collegato', () => {
  const insertUser = (role: string, link: { clientId?: string; operatorId?: string }) =>
    query(
      pool,
      `insert into users (first_name, last_name, email, phone, role, password_hash, client_id, operator_id)
       values ('Marco', 'Esposito', $1, '+393478124490', $2, 'hash', $3, $4)`,
      [`${role.toLowerCase()}-${Math.random().toString(36).slice(2)}@mencare.it`, role, link.clientId ?? null, link.operatorId ?? null],
    )

  it('un CLIENT con operator_id non esiste', async () => {
    expect(await failureCode(() => insertUser('CLIENT', { clientId, operatorId }))).toBe(PG.checkViolation)
    expect(await failureCode(() => insertUser('CLIENT', { operatorId }))).toBe(PG.checkViolation)
  })

  it('un CLIENT senza scheda cliente non esiste', async () => {
    expect(await failureCode(() => insertUser('CLIENT', {}))).toBe(PG.checkViolation)
  })

  it('uno STAFF o un OWNER vogliono l\'operatore e non la scheda cliente', async () => {
    expect(await failureCode(() => insertUser('STAFF', { clientId }))).toBe(PG.checkViolation)
    expect(await failureCode(() => insertUser('OWNER', { clientId, operatorId }))).toBe(PG.checkViolation)
    expect(await failureCode(() => insertUser('STAFF', { operatorId }))).toBeNull()
  })

  it('lo stesso operatore non può avere due account', async () => {
    await insertUser('STAFF', { operatorId })
    expect(await failureCode(() => insertUser('OWNER', { operatorId }))).toBe(PG.uniqueViolation)
  })
})

describe('ferie', () => {
  const holiday = (op: string, from: string, to: string) =>
    query(pool, 'insert into holidays (operator_id, from_date, to_date) values ($1, $2, $3)', [op, from, to])

  it('due periodi che si accavallano sullo stesso operatore non passano', async () => {
    await holiday(operatorId, '2026-08-01', '2026-08-15')
    expect(await failureCode(() => holiday(operatorId, '2026-08-10', '2026-08-20'))).toBe(PG.exclusionViolation)
    // Gli estremi sono inclusi: toccarsi su un giorno è già sovrapporsi.
    expect(await failureCode(() => holiday(operatorId, '2026-08-15', '2026-08-31'))).toBe(PG.exclusionViolation)
  })

  it('periodi attaccati ma non sovrapposti passano', async () => {
    await holiday(operatorId, '2026-08-01', '2026-08-15')
    expect(await failureCode(() => holiday(operatorId, '2026-08-16', '2026-08-31'))).toBeNull()
  })

  it('due operatori possono essere in ferie negli stessi giorni', async () => {
    await holiday(operatorId, '2026-08-01', '2026-08-15')
    expect(await failureCode(() => holiday(otherOperatorId, '2026-08-01', '2026-08-15'))).toBeNull()
  })

  it('la fine non può precedere l\'inizio', async () => {
    expect(await failureCode(() => holiday(operatorId, '2026-08-15', '2026-08-01'))).toBe(PG.checkViolation)
  })
})

describe('clienti e salone', () => {
  it('il telefono deve essere in formato E.164', async () => {
    expect(await failureCode(() => createClient({ phone: '3478124490' }))).toBe(PG.checkViolation)
    expect(await failureCode(() => createClient({ phone: '+39 347 812 4490' }))).toBe(PG.checkViolation)
  })

  it('due clienti non possono avere lo stesso numero', async () => {
    await createClient({ phone: '+393471110001' })
    expect(await failureCode(() => createClient({ phone: '+393471110001' }))).toBe(PG.uniqueViolation)
  })

  it('il salone è uno solo', async () => {
    expect(await failureCode(() => createSalon([]))).toBe(PG.checkViolation)
  })

  it('una fascia oraria che finisce prima di iniziare non passa', async () => {
    expect(await failureCode(() =>
      query(pool, 'insert into salon_hours (day_of_week, starts_at, ends_at) values (7, $1, $2)', ['19:00', '09:00']),
    )).toBe(PG.checkViolation)
  })

  it('una sola attesa per cliente, giorno e operatore', async () => {
    const insertWaiting = () =>
      query(
        pool,
        `insert into waitlist_entries (client_id, on_date, operator_id, duration_minutes, total_price_cents)
         values ($1, $2, $3, 45, 1500)`,
        [clientId, date, operatorId],
      )
    await insertWaiting()
    expect(await failureCode(insertWaiting)).toBe(PG.uniqueViolation)
  })

  it('un servizio con durata non positiva non passa', async () => {
    expect(await failureCode(() => createService({ duration: 0 }))).toBe(PG.checkViolation)
    expect(await failureCode(() => createService({ price: -1 }))).toBe(PG.checkViolation)
  })
})
