import { afterAll, beforeEach, describe, expect, it } from 'vitest'
import {
  canPlaceAt, daySlots, daysOverview, eligibleOperators, isOnHoliday, loadContext, resolveOperatorFor,
} from '../src/services/availability.js'
import { addDays, isoDayOfWeek, zonedToInstant } from '../src/lib/time.js'
import {
  closeDb, createClient, createOperator, createSalon, createService, insertAppointment,
  pool, query, truncateAll, workingDate,
} from './helpers.js'

/**
 * La disponibilità è la somma di quattro cose lette dal database: turni,
 * apertura, occupato (appuntamenti attivi + blocchi) e ferie. Il motore degli
 * slot è già provato a tavolino: qui si controlla che dal database arrivi
 * davvero quello che il motore si aspetta — soprattutto che un appuntamento
 * annullato non risulti più occupato.
 */

const date = workingDate(3)
const SPLIT: Array<[string, string]> = [['09:00', '13:00'], ['14:00', '19:00']]

let taglio: string
let barba: string
let luca: string
let sara: string
let clientId: string

async function slotsFor(operatorId: string | null, serviceIds: string[], durationMinutes: number, on = date) {
  const ctx = await loadContext(pool, on, on)
  return daySlots(ctx, operatorId, serviceIds, on, durationMinutes)
}

beforeEach(async () => {
  await truncateAll()
  await createSalon()
  taglio = await createService({ name: 'Shampoo + taglio', duration: 45, price: 1500 })
  barba = await createService({ name: 'Rasatura', duration: 30, price: 700 })
  luca = await createOperator({ name: 'Luca Ferrante', shift: SPLIT, serviceIds: [taglio, barba] })
  sara = await createOperator({ name: 'Sara Coppola', shift: [['10:00', '19:00']], serviceIds: [barba] })
  clientId = await createClient()
})

afterAll(closeDb)

describe('orari proponibili', () => {
  it('seguono i turni dell\'operatore e la pausa in mezzo non compare', async () => {
    const slots = await slotsFor(luca, [taglio], 45)
    expect(slots[0]).toBe('09:00')
    expect(slots).toContain('14:00')
    // 12:30 + 45 minuti sborderebbe dalle 13:00, e alle 13 Luca non c'è.
    expect(slots).not.toContain('12:30')
    expect(slots).not.toContain('13:00')
    expect(slots.at(-1)).toBe('18:00')
  })

  it('un appuntamento confermato toglie i suoi orari, uno annullato no', async () => {
    const id = await insertAppointment({
      clientId, operatorId: luca, startsAt: zonedToInstant(date, '10:00'), durationMinutes: 45,
    })
    const busy = await slotsFor(luca, [taglio], 45)
    expect(busy).not.toContain('10:00')
    expect(busy).not.toContain('09:30') // 09:30 + 45 finirebbe dentro l'appuntamento
    expect(busy).toContain('11:00')

    await query(pool, `update appointments set status = 'CANCELLED', cancelled_at = now(), cancelled_by = 'CLIENT' where id = $1`, [id])
    expect(await slotsFor(luca, [taglio], 45)).toContain('10:00')
  })

  it('un blocco nasconde i suoi orari', async () => {
    await query(
      pool,
      "insert into time_blocks (operator_id, reason, on_date, starts_at, ends_at) values ($1, 'PAUSA', $2, '09:00', '11:00')",
      [luca, date],
    )
    const slots = await slotsFor(luca, [taglio], 45)
    expect(slots).not.toContain('09:00')
    expect(slots).not.toContain('10:00')
    expect(slots[0]).toBe('11:00')
  })

  it('le ferie svuotano la giornata', async () => {
    await query(pool, 'insert into holidays (operator_id, from_date, to_date) values ($1, $2, $3)', [
      luca, addDays(date, -1), addDays(date, 1),
    ])
    const ctx = await loadContext(pool, date, date)
    expect(isOnHoliday(ctx, luca, date)).toBe(true)
    expect(isOnHoliday(ctx, luca, addDays(date, 2))).toBe(false)
    expect(daySlots(ctx, luca, [taglio], date, 45)).toEqual([])
  })

  it('nel giorno di chiusura non c\'è niente, nemmeno con l\'operatore in turno', async () => {
    let sunday = date
    while (isoDayOfWeek(sunday) !== 7) sunday = addDays(sunday, 1)
    expect(await slotsFor(luca, [taglio], 45, sunday)).toEqual([])
  })

  it('"qualsiasi operatore" è l\'unione, non l\'intersezione', async () => {
    // Sara comincia alle 10: le 09:00 le offre solo Luca, e devono esserci.
    const union = await slotsFor(null, [barba], 30)
    const onlySara = await slotsFor(sara, [barba], 30)
    expect(union).toContain('09:00')
    expect(onlySara).not.toContain('09:00')
    expect(union.length).toBe(new Set(union).size)
  })
})

describe('operatori abilitati', () => {
  it('servono tutti i servizi scelti, non uno qualsiasi', async () => {
    const ctx = await loadContext(pool, date, date)
    expect(eligibleOperators(ctx, null, [barba]).map((o) => o.id).sort()).toEqual([luca, sara].sort())
    expect(eligibleOperators(ctx, null, [taglio]).map((o) => o.id)).toEqual([luca])
    expect(eligibleOperators(ctx, null, [taglio, barba]).map((o) => o.id)).toEqual([luca])
    expect(eligibleOperators(ctx, sara, [taglio])).toEqual([])
  })

  it('un operatore disattivato sparisce dalla disponibilità', async () => {
    await query(pool, 'update operators set active = false where id = $1', [sara])
    const ctx = await loadContext(pool, date, date)
    expect(ctx.operators.map((o) => o.id)).toEqual([luca])
  })

  it('resolveOperatorFor sceglie chi è libero a quell\'ora', async () => {
    await insertAppointment({ clientId, operatorId: luca, startsAt: zonedToInstant(date, '11:00'), durationMinutes: 30 })
    const ctx = await loadContext(pool, date, date)
    expect(resolveOperatorFor(ctx, null, [barba], date, '11:00', 30)).toBe(sara)
    expect(resolveOperatorFor(ctx, luca, [barba], date, '11:00', 30)).toBeNull()
    expect(resolveOperatorFor(ctx, null, [taglio], date, '11:00', 45)).toBeNull() // solo Luca lo esegue
  })
})

describe('panoramica dei giorni', () => {
  it('distingue i giorni liberi, quelli pieni e quelli chiusi', async () => {
    // Un solo operatore in gioco, con la giornata tappata da un blocco:
    // quel giorno è "pieno", non "chiuso".
    await query(pool, 'update operators set active = false where id = $1', [sara])
    await query(
      pool,
      "insert into time_blocks (operator_id, reason, on_date, starts_at, ends_at) values ($1, 'CORSO', $2, '09:00', '19:00')",
      [luca, date],
    )

    const to = addDays(date, 7)
    const ctx = await loadContext(pool, date, to)
    const overview = daysOverview(ctx, null, [taglio], date, to, 45)

    expect(overview.fullyBooked).toContain(date)
    expect(overview.available).not.toContain(date)
    expect(overview.available).toContain(nextOpen(addDays(date, 1)))

    // La domenica il salone è chiuso: non è né libera né piena.
    let sunday = date
    while (isoDayOfWeek(sunday) !== 7) sunday = addDays(sunday, 1)
    expect(overview.available).not.toContain(sunday)
    expect(overview.fullyBooked).not.toContain(sunday)
  })
})

describe('spostamento a mano', () => {
  it('accetta le 09:45, rifiuta fuori turno e sopra un appuntamento', async () => {
    await insertAppointment({ clientId, operatorId: luca, startsAt: zonedToInstant(date, '11:00'), durationMinutes: 60 })
    const ctx = await loadContext(pool, date, date)

    expect(canPlaceAt(ctx, luca, date, '09:45', 45)).toBe(true)
    expect(canPlaceAt(ctx, luca, date, '13:15', 45)).toBe(false) // pausa fra i due turni
    expect(canPlaceAt(ctx, luca, date, '11:30', 30)).toBe(false) // sopra l'appuntamento
    expect(canPlaceAt(ctx, luca, date, '18:45', 30)).toBe(false) // sborda dalla chiusura
  })
})

/** Primo giorno di apertura da `date` in poi. */
function nextOpen(from: string): string {
  let day = from
  while (isoDayOfWeek(day) === 7) day = addDays(day, 1)
  return day
}
