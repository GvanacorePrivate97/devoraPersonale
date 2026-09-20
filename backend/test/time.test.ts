import { describe, expect, it } from 'vitest'
import {
  addDays, daysBetween, eachDate, instantToZoned, isoDayOfWeek, minutesOfTime,
  startOfWeek, timeOfMinutes, todayInSalon, zonedToInstant,
} from '../src/lib/time.js'

/**
 * Il salone ragiona in orario da muro, il database in istanti UTC: se la
 * conversione sbaglia di un'ora nei due giorni del cambio d'ora, gli
 * appuntamenti di quelle domeniche finiscono all'ora sbagliata. Qui si
 * verificano proprio quei due giorni.
 */

const iso = (date: string, time: string) => zonedToInstant(date, time).toISOString()

describe('zonedToInstant', () => {
  it('in inverno Roma è UTC+1', () => {
    expect(iso('2026-01-15', '10:00')).toBe('2026-01-15T09:00:00.000Z')
  })

  it('in estate Roma è UTC+2', () => {
    expect(iso('2026-07-15', '10:00')).toBe('2026-07-15T08:00:00.000Z')
  })

  it('mezzanotte resta sul suo giorno e non scivola indietro', () => {
    // `Intl` con hour12:false può restituire l'ora 24: il motore la riporta a 0.
    const midnight = zonedToInstant('2026-09-18', '00:00')
    expect(instantToZoned(midnight)).toEqual({ date: '2026-09-18', time: '00:00', dayOfWeek: 5 })
    expect(instantToZoned(zonedToInstant('2026-01-15', '23:59')).time).toBe('23:59')
  })
})

describe('cambio dell\'ora legale', () => {
  // L'ultima domenica di marzo 2026 è il 29: alle 02:00 le lancette vanno a 03:00.
  const springForward = '2026-03-29'
  // L'ultima domenica di ottobre 2026 è il 25: alle 03:00 si torna alle 02:00.
  const fallBack = '2026-10-25'

  it('i due giorni scelti sono davvero le ultime domeniche di marzo e ottobre', () => {
    for (const day of [springForward, fallBack]) {
      expect(isoDayOfWeek(day)).toBe(7)
      // Sette giorni dopo si cambia mese: quindi è l'ultima domenica.
      expect(addDays(day, 7).slice(5, 7)).not.toBe(day.slice(5, 7))
    }
  })

  it('marzo: prima del salto vale UTC+1, dopo UTC+2', () => {
    expect(iso(springForward, '01:30')).toBe('2026-03-29T00:30:00.000Z')
    expect(iso(springForward, '03:00')).toBe('2026-03-29T01:00:00.000Z')
    // Un'ora di differenza sul muro, due istanti distanti novanta minuti.
    expect(zonedToInstant(springForward, '03:00').getTime() - zonedToInstant(springForward, '01:30').getTime())
      .toBe(30 * 60_000)
  })

  it('marzo: l\'ora che non esiste finisce dopo il salto, non un\'ora prima', () => {
    // Le 02:30 del 29 marzo non esistono: il risultato è l'istante delle 03:30.
    const instant = zonedToInstant(springForward, '02:30')
    expect(instant.toISOString()).toBe('2026-03-29T01:30:00.000Z')
    expect(instantToZoned(instant).time).toBe('03:30')
  })

  it('ottobre: prima del ritorno vale UTC+2, dopo UTC+1', () => {
    expect(iso(fallBack, '01:30')).toBe('2026-10-24T23:30:00.000Z')
    expect(iso(fallBack, '03:00')).toBe('2026-10-25T02:00:00.000Z')
  })

  it('ottobre: l\'ora doppia si risolve sulla seconda passata e torna indietro uguale', () => {
    const instant = zonedToInstant(fallBack, '02:30')
    expect(instant.toISOString()).toBe('2026-10-25T01:30:00.000Z')
    expect(instantToZoned(instant).time).toBe('02:30')
  })

  it('l\'istante del salto si legge già con il nuovo scarto', () => {
    expect(instantToZoned(new Date('2026-03-29T00:59:59Z')).time).toBe('01:59')
    expect(instantToZoned(new Date('2026-03-29T01:00:00Z')).time).toBe('03:00')
    expect(instantToZoned(new Date('2026-10-25T00:59:59Z')).time).toBe('02:59')
    expect(instantToZoned(new Date('2026-10-25T01:00:00Z')).time).toBe('02:00')
  })
})

describe('andata e ritorno', () => {
  it('instantToZoned(zonedToInstant(d, t)) restituisce d e t', () => {
    const dates = ['2026-01-15', '2026-03-28', '2026-03-29', '2026-03-30', '2026-07-15', '2026-10-24', '2026-10-25', '2026-10-26', '2026-12-31']
    // Le 02:00–02:59 del 29 marzo non esistono in Europe/Rome: unica esclusione.
    const times = ['00:00', '01:30', '03:00', '09:00', '12:45', '17:30', '23:30']
    for (const date of dates) {
      for (const time of times) {
        // L'atteso contiene data e ora di partenza: se salta, si legge subito quale.
        expect(instantToZoned(zonedToInstant(date, time))).toEqual({ date, time, dayOfWeek: isoDayOfWeek(date) })
      }
    }
  })
})

describe('calendario', () => {
  it('isoDayOfWeek: lunedì 1, domenica 7', () => {
    expect(isoDayOfWeek('2026-09-14')).toBe(1)
    expect(isoDayOfWeek('2026-09-20')).toBe(7)
  })

  it('startOfWeek riporta al lunedì', () => {
    expect(startOfWeek('2026-09-18')).toBe('2026-09-14')
    expect(startOfWeek('2026-09-14')).toBe('2026-09-14')
    expect(startOfWeek('2026-09-20')).toBe('2026-09-14')
  })

  it('addDays e daysBetween contano giorni di calendario, anche sul cambio d\'ora', () => {
    expect(addDays('2026-02-28', 1)).toBe('2026-03-01')
    expect(addDays('2026-03-29', -1)).toBe('2026-03-28')
    expect(daysBetween('2026-03-28', '2026-03-30')).toBe(2)
    expect(daysBetween('2026-10-26', '2026-10-24')).toBe(-2)
  })

  it('eachDate include gli estremi', () => {
    expect(eachDate('2026-09-18', '2026-09-20')).toEqual(['2026-09-18', '2026-09-19', '2026-09-20'])
    expect(eachDate('2026-09-18', '2026-09-18')).toEqual(['2026-09-18'])
  })

  it('minuti e orari sono la stessa cosa scritta in due modi', () => {
    expect(minutesOfTime('09:30')).toBe(570)
    expect(timeOfMinutes(570)).toBe('09:30')
    expect(timeOfMinutes(minutesOfTime('00:05'))).toBe('00:05')
  })

  it('todayInSalon è una data valida nel fuso del salone', () => {
    const today = todayInSalon()
    expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(instantToZoned(new Date()).date).toBe(today)
  })
})
