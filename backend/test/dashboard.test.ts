import { afterAll, beforeEach, describe, expect, it } from 'vitest'
import { upcomingDays, UPCOMING_DAYS } from '../src/modules/admin/service.js'
import { clientInsights } from '../src/modules/crm/service.js'
import { addDays, isoDayOfWeek, zonedToInstant } from '../src/lib/time.js'
import {
  closeDb, createClient, createOperator, createSalon, createService, insertAppointment,
  pool, query, truncateAll, workingDate,
} from './helpers.js'

/**
 * "Prossimi 7 giorni" della dashboard e abitudini del cliente nella scheda.
 * Il primo contro il database vero (turni, appuntamenti, lista d'attesa), il
 * secondo è un conto puro sulla storia.
 */

let operatorId: string
let marco: string

beforeEach(async () => {
  await truncateAll()
  await createSalon()
  const taglio = await createService({ duration: 60 })
  // Un solo operatore, 09–19 dal lunedì al sabato: 600 minuti lavorabili al giorno.
  operatorId = await createOperator({ serviceIds: [taglio] })
  marco = await createClient()
})

afterAll(closeDb)

describe('prossimi 7 giorni', () => {
  it('occupazione, lista d\'attesa e giorni di chiusura', async () => {
    const day = workingDate(2)
    // Tre ore prenotate su dieci: 30%.
    for (const time of ['09:00', '10:00', '11:00']) {
      await insertAppointment({ clientId: marco, operatorId, startsAt: zonedToInstant(day, time), durationMinutes: 60 })
    }
    // Annullati e no-show non occupano la poltrona.
    await insertAppointment({
      clientId: marco, operatorId, startsAt: zonedToInstant(day, '14:00'), durationMinutes: 60, status: 'CANCELLED',
    })
    await query(
      pool,
      `insert into waitlist_entries (client_id, on_date, duration_minutes, total_price_cents) values ($1, $2, 60, 1500)`,
      [marco, day],
    )

    const days = await upcomingDays(pool)
    expect(days).toHaveLength(UPCOMING_DAYS)

    const target = days.find((d) => d.date === day)!
    expect(target).toMatchObject({ occupancyPercent: 30, waitlistCount: 1, closed: false })

    // La domenica il salone è chiuso: 0% e segnata come chiusa, non "vuota".
    const sunday = days.find((d) => isoDayOfWeek(d.date) === 7)!
    expect(sunday).toMatchObject({ occupancyPercent: 0, closed: true })
    expect(days[0]!.date <= day && day <= addDays(days[0]!.date, UPCOMING_DAYS - 1)).toBe(true)
  })
})

describe('abitudini del cliente', () => {
  const visit = (operatorId: string, date: string, status = 'COMPLETED') => ({ operatorId, date, status })

  it('operatore preferito e giorni medi fra le visite completate', () => {
    // Dalla più recente, come arriva dalla scheda.
    const insights = clientInsights([
      visit('luca', '2026-03-31'),
      visit('sara', '2026-03-21', 'NO_SHOW'),
      visit('luca', '2026-03-11'),
      visit('antonio', '2026-02-19'),
      visit('luca', '2026-02-01', 'CANCELLED'),
    ])
    // Visite completate il 19/2, 11/3, 31/3: 20 giorni fra l'una e l'altra.
    expect(insights).toEqual({ favoriteOperatorId: 'luca', averageDaysBetweenVisits: 20 })
  })

  it('a parità vince chi l\'ha servito per ultimo; con una sola visita niente media', () => {
    expect(clientInsights([visit('sara', '2026-03-31'), visit('luca', '2026-03-01')]).favoriteOperatorId).toBe('sara')
    expect(clientInsights([visit('luca', '2026-03-31')])).toEqual({ favoriteOperatorId: 'luca', averageDaysBetweenVisits: null })
    expect(clientInsights([])).toEqual({ favoriteOperatorId: null, averageDaysBetweenVisits: null })
  })
})
