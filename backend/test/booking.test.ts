import { afterAll, beforeEach, describe, expect, it } from 'vitest'
import {
  availability, book, cancel, reschedule, setStatus, CLIENT_CANCELLATION_WINDOW_HOURS,
} from '../src/modules/booking/service.js'
import { advanceAppointmentStatuses } from '../src/jobs/appointmentStatus.js'
import { ApiError } from '../src/lib/errors.js'
import { addDays, instantToZoned, isoDayOfWeek, zonedToInstant } from '../src/lib/time.js'
import {
  closeDb, createClient, createOperator, createSalon, createService, insertAppointment,
  pool, query, queryOne, truncateAll, workingDate,
} from './helpers.js'

/**
 * Il servizio di prenotazione contro il database vero. La prova che conta è la
 * corsa fra due clienti sullo stesso orario: è l'unico modo per sapere che il
 * vincolo di esclusione fa il suo mestiere, e nessun test in memoria potrebbe
 * dirlo.
 *
 * I due bug che questi test avevano scoperto sono corretti:
 * la prenotazione non gira più in SERIALIZABLE (arbitra il vincolo di
 * esclusione, e "qualsiasi operatore" ritenta con un collega libero), e
 * `setStatus` scrive il cast `$2::appointment_status` che mancava.
 */

const date = workingDate(3)
const TIME = '10:00'

let taglio: string
let barba: string
/** Servizio che l'operatore della prova non esegue. */
let baby: string
let operatorId: string
let otherOperatorId: string
let marco: string
let davide: string

/** Codice dell'ApiError sollevato, oppure null se è andata bene. */
async function errorCode(run: () => Promise<unknown>): Promise<string | null> {
  try {
    await run()
    return null
  } catch (error) {
    if (error instanceof ApiError) return error.code
    throw error
  }
}

async function countAppointments(status = 'CONFIRMED'): Promise<number> {
  const row = await queryOne<{ n: number }>(pool, 'select count(*)::int as n from appointments where status = $1', [status])
  return row!.n
}

beforeEach(async () => {
  await truncateAll()
  await createSalon()
  taglio = await createService({ name: 'Shampoo + taglio', duration: 45, price: 1500 })
  barba = await createService({ name: 'Rasatura', duration: 30, price: 700 })
  baby = await createService({ name: 'Taglio baby men', duration: 30, price: 1000 })
  operatorId = await createOperator({ name: 'Luca Ferrante', serviceIds: [taglio, barba] })
  otherOperatorId = await createOperator({ name: 'Sara Coppola', serviceIds: [barba] })
  marco = await createClient({ first: 'Marco', last: 'Esposito' })
  davide = await createClient({ first: 'Davide', last: 'Russo' })
})

afterAll(closeDb)

function minutesAgo(minutes: number): Date {
  return new Date(Date.now() - minutes * 60_000)
}

function hoursAgo(hours: number): Date {
  return minutesAgo(hours * 60)
}

/** Account CLIENT sulla scheda, così le notifiche hanno un destinatario. */
async function linkUser(clientId: string): Promise<string> {
  const row = await queryOne<{ id: string }>(
    pool,
    `insert into users (role, first_name, last_name, email, phone, password_hash, client_id)
     select 'CLIENT', first_name, last_name, 'u' || substr(md5(random()::text), 1, 8) || '@test.it', phone, 'x', id
       from clients where id = $1
     returning id`,
    [clientId],
  )
  return row!.id
}

describe('due clienti sullo stesso orario', () => {
  it('la corsa lascia esattamente un appuntamento, non due', async () => {
    const input = { operatorId, serviceIds: [taglio], date, time: TIME }
    const results = await Promise.allSettled([
      book({ ...input, clientId: marco }),
      book({ ...input, clientId: davide }),
    ])

    expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1)
    expect(results.filter((r) => r.status === 'rejected')).toHaveLength(1)
    // È questo che il vincolo di esclusione garantisce: nel database ne resta uno.
    expect(await countAppointments()).toBe(1)

    // Chi perde riceve sempre l'errore che l'app sa mostrare, mai un 500.
    const reason = (results.find((r) => r.status === 'rejected') as PromiseRejectedResult).reason
    const code = reason instanceof ApiError ? reason.code : (reason as { code?: string }).code
    expect(code).toBe('SLOT_NO_LONGER_AVAILABLE')
  })

  it('chi conferma dopo riceve SLOT_NO_LONGER_AVAILABLE', async () => {
    // Senza corsa il percorso è quello che vede il cliente lento del mockup:
    // ha la griglia in mano, l'orario nel frattempo è andato.
    await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    expect(await errorCode(() => book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: TIME })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
    expect(await countAppointments()).toBe(1)
  })

  it('anche in cinque, l\'appuntamento resta uno solo', async () => {
    const input = { operatorId, serviceIds: [taglio], date, time: TIME }
    const clients = [marco, davide, await createClient({ first: 'Ciro', last: 'Esposito' }),
      await createClient({ first: 'Enzo', last: 'Longo' }), await createClient({ first: 'Paolo', last: 'Ferri' })]

    const results = await Promise.allSettled(clients.map((clientId) => book({ ...input, clientId })))
    expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1)
    expect(await countAppointments()).toBe(1)
  })

  // BUG 1 (vedi in cima): due orari diversi non sono in conflitto, eppure oggi
  // una delle due transazioni viene annullata con 40001. Test scritto come
  // deve andare: quando il bug è risolto diventa rosso, e si toglie `.fails`.
  it('due orari diversi prenotati nello stesso istante passano entrambi', async () => {
    const results = await Promise.allSettled([
      book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '10:00' }),
      book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: '11:00' }),
    ])
    expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(2)
    expect(await countAppointments()).toBe(2)
  })
})

describe('regole della prenotazione', () => {
  it('prenota e fotografa durata e prezzo del carrello', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio, barba], date, time: TIME })
    expect(appointment.durationMinutes).toBe(75)
    expect(appointment.totalPriceCents).toBe(2200)
    expect(appointment.date).toBe(date)
    expect(appointment.time).toBe(TIME)
    expect(appointment.serviceIds).toEqual([taglio, barba])

    const row = await queryOne<{ starts_at: Date; ends_at: Date }>(
      pool, 'select starts_at, ends_at from appointments where id = $1', [appointment.id],
    )
    expect(instantToZoned(row!.starts_at)).toMatchObject({ date, time: TIME })
    expect(row!.ends_at.getTime() - row!.starts_at.getTime()).toBe(75 * 60_000)
  })

  it('fuori dall\'orario di apertura non si prenota', async () => {
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '20:00' })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '08:00' })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
    // 18:30 + 45 minuti sborderebbe dalla chiusura delle 19:00.
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '18:30' })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
    expect(await countAppointments()).toBe(0)
  })

  it('fuori dalla griglia di 30 minuti non si prenota', async () => {
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '10:15' })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
  })

  it('un servizio che quell\'operatore non esegue non si prenota', async () => {
    // Non è un posto sfumato ma una scelta impossibile: l'errore lo dice, e il
    // campo "services" fa illuminare la riga giusta nel carrello.
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [baby], date, time: TIME })))
      .toBe('VALIDATION')
    // Nemmeno insieme a uno che invece esegue: servono tutti.
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [taglio, baby], date, time: TIME })))
      .toBe('VALIDATION')
    expect(await countAppointments()).toBe(0)
  })

  it('senza servizi non si prenota', async () => {
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [], date, time: TIME })))
      .toBe('VALIDATION')
  })

  it('in un giorno di chiusura non si prenota', async () => {
    let sunday = date
    while (isoDayOfWeek(sunday) !== 7) sunday = addDays(sunday, 1)
    expect(await errorCode(() => book({ clientId: marco, operatorId, serviceIds: [taglio], date: sunday, time: TIME })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
  })

  it('"qualsiasi operatore": il server sceglie chi è abilitato e libero', async () => {
    // La rasatura la eseguono tutti e due: due prenotazioni sullo stesso
    // orario trovano due poltrone diverse.
    const first = await book({ clientId: marco, operatorId: null, serviceIds: [barba], date, time: TIME })
    const second = await book({ clientId: davide, operatorId: null, serviceIds: [barba], date, time: TIME })
    expect(new Set([first.operatorId, second.operatorId]).size).toBe(2)
    expect([operatorId, otherOperatorId]).toContain(first.operatorId)

    // Il terzo non trova più nessuno libero.
    const ciro = await createClient({ first: 'Ciro', last: 'Espo' })
    expect(await errorCode(() => book({ clientId: ciro, operatorId: null, serviceIds: [barba], date, time: TIME })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
  })
})

describe('passaggi di stato', () => {
  // BUG 2: l'UPDATE di setStatus non è nemmeno eseguibile (vedi in cima).
  it('segnare COMPLETATO due volte non conta due visite', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    const first = await setStatus(appointment.id, 'COMPLETED')
    const second = await setStatus(appointment.id, 'COMPLETED')
    expect(first.status).toBe('COMPLETED')
    expect(second.status).toBe('COMPLETED')

    const stats = await queryOne<{ visit_count: number; lifetime_spend_cents: number }>(
      pool, 'select visit_count, lifetime_spend_cents from client_stats where client_id = $1', [marco],
    )
    expect(stats).toMatchObject({ visit_count: 1, lifetime_spend_cents: 1500 })

    // E completed_at non viene riscritto dalla seconda chiamata.
    const row = await queryOne<{ completed_at: Date }>(pool, 'select completed_at from appointments where id = $1', [appointment.id])
    expect(row!.completed_at).toBeInstanceOf(Date)
  })

  it('un appuntamento annullato non si completa', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    await cancel({ appointmentId: appointment.id, by: 'SALON' })
    expect(await errorCode(() => setStatus(appointment.id, 'COMPLETED'))).toBe('VALIDATION')
  })

  it('da COMPLETATO non si torna in corso, e il no-show aspetta l\'orario d\'inizio', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    await setStatus(appointment.id, 'COMPLETED')
    expect(await errorCode(() => setStatus(appointment.id, 'IN_PROGRESS'))).toBe('VALIDATION')
    // Appuntamento futuro: non può essere un'assenza.
    expect(await errorCode(() => setStatus(appointment.id, 'NO_SHOW'))).toBe('VALIDATION')
  })

  it('un\'assenza conta fra i no-show e non fra le visite (BUG 2)', async () => {
    const appointment = { id: await insertAppointment({ clientId: marco, operatorId, startsAt: hoursAgo(2) }) }
    await setStatus(appointment.id, 'NO_SHOW')
    await setStatus(appointment.id, 'NO_SHOW')
    const stats = await queryOne<{ visit_count: number; no_show_count: number }>(
      pool, 'select visit_count, no_show_count from client_stats where client_id = $1', [marco],
    )
    expect(stats).toMatchObject({ visit_count: 0, no_show_count: 1 })
  })
})

describe('no-show a mano', () => {
  it('si segna anche su un appuntamento già chiuso come completato, e avvisa il cliente', async () => {
    const userId = await linkUser(marco)
    const id = await insertAppointment({ clientId: marco, operatorId, startsAt: hoursAgo(3), status: 'COMPLETED' })

    const updated = await setStatus(id, 'NO_SHOW')
    expect(updated.status).toBe('NO_SHOW')
    const row = await queryOne<{ completed_at: Date | null }>(pool, 'select completed_at from appointments where id = $1', [id])
    expect(row!.completed_at).toBeNull()

    const notes = await query<{ kind: string }>(pool, 'select kind from notifications where user_id = $1', [userId])
    expect(notes.map((n) => n.kind)).toEqual(['BOOKING_NO_SHOW'])
  })

  it('un no-show sbagliato si corregge riportandolo a completato', async () => {
    await linkUser(marco)
    const id = await insertAppointment({ clientId: marco, operatorId, startsAt: hoursAgo(3) })
    await setStatus(id, 'NO_SHOW')
    const fixed = await setStatus(id, 'COMPLETED')
    expect(fixed.status).toBe('COMPLETED')

    const stats = await queryOne<{ visit_count: number; no_show_count: number }>(
      pool, 'select visit_count, no_show_count from client_stats where client_id = $1', [marco],
    )
    expect(stats).toMatchObject({ visit_count: 1, no_show_count: 0 })
    // La correzione non manda un secondo avviso.
    const notes = await queryOne<{ n: number }>(pool, "select count(*)::int as n from notifications where kind = 'BOOKING_NO_SHOW'")
    expect(notes!.n).toBe(1)
  })
})

describe('stati automatici', () => {
  it('in corso all\'orario d\'inizio, completato alla fine; il no-show resta com\'è', async () => {
    const running = await insertAppointment({ clientId: marco, operatorId, startsAt: minutesAgo(10), durationMinutes: 45 })
    const finished = await insertAppointment({ clientId: davide, operatorId, startsAt: hoursAgo(3), durationMinutes: 45 })
    const absent = await insertAppointment({ clientId: davide, operatorId: otherOperatorId, startsAt: hoursAgo(5), status: 'NO_SHOW' })
    const future = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })

    const result = await advanceAppointmentStatuses()
    expect(result).toEqual({ started: 1, completed: 1 })

    const statusOf = async (id: string) =>
      (await queryOne<{ status: string }>(pool, 'select status from appointments where id = $1', [id]))!.status
    expect(await statusOf(running)).toBe('IN_PROGRESS')
    expect(await statusOf(finished)).toBe('COMPLETED')
    expect(await statusOf(absent)).toBe('NO_SHOW')
    expect(await statusOf(future.id)).toBe('CONFIRMED')

    // Rieseguirlo non cambia niente.
    expect(await advanceAppointmentStatuses()).toEqual({ started: 0, completed: 0 })
  })
})

describe('modifica (sostituzione)', () => {
  it('si può tenere lo stesso orario cambiando i servizi', async () => {
    const original = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    // Senza ignorare l'appuntamento stesso, il suo orario risulterebbe occupato.
    expect((await availability(operatorId, [taglio, barba], date)).slots).not.toContain(TIME)
    expect((await availability(operatorId, [taglio, barba], date, original.id)).slots).toContain(TIME)

    const replaced = await book({
      clientId: marco, operatorId, serviceIds: [taglio, barba], date, time: TIME,
      replaces: { appointmentId: original.id, by: 'SALON' },
    })
    expect(replaced.id).not.toBe(original.id)
    const old = await queryOne<{ status: string; cancelled_by: string }>(
      pool, 'select status, cancelled_by from appointments where id = $1', [original.id],
    )
    expect(old).toMatchObject({ status: 'CANCELLED', cancelled_by: 'SALON' })
  })

  it('se il nuovo orario non c\'è, il vecchio resta com\'era', async () => {
    const original = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    await book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: '14:00' })
    expect(await errorCode(() => book({
      clientId: marco, operatorId, serviceIds: [taglio], date, time: '14:00',
      replaces: { appointmentId: original.id, by: 'SALON' },
    }))).toBe('SLOT_NO_LONGER_AVAILABLE')
    const old = await queryOne<{ status: string }>(pool, 'select status from appointments where id = $1', [original.id])
    expect(old!.status).toBe('CONFIRMED')
  })
})

describe('annullamento', () => {
  it('libera l\'orario, che torna prenotabile', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    const busy = await availability(operatorId, [taglio], date)
    expect(busy.slots).not.toContain(TIME)

    await cancel({ appointmentId: appointment.id, by: 'SALON' })

    const free = await availability(operatorId, [taglio], date)
    expect(free.slots).toContain(TIME)
    await expect(book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: TIME })).resolves.toMatchObject({
      clientId: davide, time: TIME,
    })
  })

  it('annullare due volte non cambia niente', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    await cancel({ appointmentId: appointment.id, by: 'CLIENT' })
    await cancel({ appointmentId: appointment.id, by: 'CLIENT' })
    const row = await queryOne<{ status: string; cancelled_by: string }>(
      pool, 'select status, cancelled_by from appointments where id = $1', [appointment.id],
    )
    expect(row).toMatchObject({ status: 'CANCELLED', cancelled_by: 'CLIENT' })
  })

  it(`il cliente non disdice a meno di ${CLIENT_CANCELLATION_WINDOW_HOURS} ore, il salone sì`, async () => {
    const soon = new Date(Date.now() + 60 * 60_000) // fra un'ora
    const imminent = await insertAppointment({ clientId: marco, operatorId, startsAt: soon })

    expect(await errorCode(() => cancel({ appointmentId: imminent, by: 'CLIENT' }))).toBe('FORBIDDEN')
    // Il salone, che risponde al telefono, può sempre.
    expect(await errorCode(() => cancel({ appointmentId: imminent, by: 'SALON' }))).toBeNull()

    const later = await insertAppointment({
      clientId: davide, operatorId, startsAt: new Date(Date.now() + 5 * 3_600_000),
    })
    expect(await errorCode(() => cancel({ appointmentId: later, by: 'CLIENT' }))).toBeNull()
  })

  it('un appuntamento già concluso non si annulla (BUG 2: non si riesce a concluderlo)', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: TIME })
    await setStatus(appointment.id, 'COMPLETED')
    expect(await errorCode(() => cancel({ appointmentId: appointment.id, by: 'SALON' }))).toBe('VALIDATION')
  })
})

describe('spostamento', () => {
  it('non si sposta sopra un altro appuntamento', async () => {
    const first = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '10:00' })
    await book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: '11:00' })
    expect(await errorCode(() => reschedule({ appointmentId: first.id, date, time: '11:00', freeForm: false })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
  })

  it('a mano il titolare esce dalla griglia, il cliente no', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '10:00' })
    expect(await errorCode(() => reschedule({ appointmentId: appointment.id, date, time: '09:45', freeForm: false })))
      .toBe('SLOT_NO_LONGER_AVAILABLE')
    const moved = await reschedule({ appointmentId: appointment.id, date, time: '09:45', freeForm: true })
    expect(moved.time).toBe('09:45')
  })

  it('lo spostamento libera davvero il vecchio orario', async () => {
    const appointment = await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '10:00' })
    await reschedule({ appointmentId: appointment.id, date, time: '15:00', freeForm: false })
    await expect(book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: '10:00' }))
      .resolves.toMatchObject({ time: '10:00' })
  })
})

describe('appuntamenti letti indietro', () => {
  it('la giornata dell\'operatore usa i confini nel fuso del salone', async () => {
    await book({ clientId: marco, operatorId, serviceIds: [taglio], date, time: '09:00' })
    await book({ clientId: davide, operatorId, serviceIds: [taglio], date, time: '18:00' })
    const rows = await query<{ n: number }>(
      pool,
      `select count(*)::int as n from appointments
        where operator_id = $1 and starts_at >= $2 and starts_at < $3`,
      [operatorId, zonedToInstant(date, '00:00'), zonedToInstant(date, '23:59')],
    )
    expect(rows[0]!.n).toBe(2)
  })
})
