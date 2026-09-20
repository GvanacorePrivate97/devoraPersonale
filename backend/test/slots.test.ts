import { describe, expect, it } from 'vitest'
import { canPlace, intersect, overlaps, slotsFor, unionSlots, workRanges, type Range } from '../src/lib/slots.js'
import { minutesOfTime, timeOfMinutes } from '../src/lib/time.js'

/**
 * Gli stessi casi che le app hanno già fissato (Android
 * `core/data/src/test/.../SlotEngineTest.kt`, iOS identico). Se il server
 * rispondesse anche un solo orario diverso, il cliente vedrebbe una griglia e
 * ne otterrebbe un'altra: questi test sono il contratto fra i due motori.
 */

const t = minutesOfTime
const asTimes = (slots: number[]) => slots.map(timeOfMinutes)

/** Salone aperto 9–19; gli orari dell'operatore vengono comunque tagliati su questo. */
const SALON: Range[] = [{ start: t('09:00'), end: t('19:00') }]
/** Turno di riferimento: solo mattina, 9–13. */
const MORNING: Range[] = [{ start: t('09:00'), end: t('13:00') }]

function slots(options: Partial<Parameters<typeof slotsFor>[0]> = {}): string[] {
  return asTimes(slotsFor({
    operatorHours: MORNING,
    salonHours: SALON,
    busy: [],
    durationMinutes: 30,
    ...options,
  }))
}

describe('slotsFor', () => {
  it('propone la griglia di 30 minuti su tutta la mattina libera', () => {
    expect(slots({ durationMinutes: 60 })).toEqual(['09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '12:00'])
  })

  it('accetta solo gli orari in cui il servizio entra tutto nella fascia', () => {
    // Quattro ore in un turno di quattro ore: un solo inizio possibile.
    expect(slots({ durationMinutes: 240 })).toEqual(['09:00'])
  })

  it('un appuntamento di 45 minuti alle 10:00 toglie 10:00 e 10:30 ma non 09:30', () => {
    const busy = [{ start: t('10:00'), end: t('10:45') }]
    expect(slots({ busy })).toEqual(['09:00', '09:30', '11:00', '11:30', '12:00', '12:30'])
  })

  it('un appuntamento annullato non occupa niente e il suo orario torna libero', () => {
    // Chi costruisce "busy" filtra su CONFIRMED/IN_PROGRESS: annullato =
    // nessuna fascia occupata (la controprova sul database sta in availability.test.ts).
    expect(slots({ busy: [] })).toContain('10:00')
  })

  it('qualunque blocco nasconde i suoi orari al cliente', () => {
    const pausa = [{ start: t('09:00'), end: t('11:00') }]
    expect(slots({ busy: pausa })).toEqual(['11:00', '11:30', '12:00', '12:30'])
  })

  it('giorno di chiusura: nessun orario, anche se l\'operatore avrebbe il turno', () => {
    expect(slots({ salonHours: [] })).toEqual([])
  })

  it('operatore in ferie: nessun orario', () => {
    expect(slots({ onHoliday: true })).toEqual([])
  })

  it('giorno già passato: nessun orario', () => {
    expect(slots({ isPast: true })).toEqual([])
  })

  it('oggi alle 09:45 con 30 minuti di preavviso: il primo orario è 10:30', () => {
    const result = slots({ nowMinutes: t('09:45') })
    expect(result[0]).toBe('10:30')
    expect(result).not.toContain('10:00')
  })

  it('l\'apertura del salone vince sul turno più largo dell\'operatore', () => {
    const wide = [{ start: t('07:00'), end: t('21:00') }]
    const result = slots({ operatorHours: wide, durationMinutes: 60 })
    expect(result[0]).toBe('09:00')
    expect(result.at(-1)).toBe('18:00')
  })

  it('turno spezzato: orari nelle due fasce e niente in mezzo', () => {
    const split = [
      { start: t('09:00'), end: t('11:00') },
      { start: t('15:00'), end: t('17:00') },
    ]
    expect(slots({ operatorHours: split, durationMinutes: 60 })).toEqual([
      '09:00', '09:30', '10:00', '15:00', '15:30', '16:00',
    ])
  })

  it('durata non valida: nessun orario', () => {
    expect(slots({ durationMinutes: 0 })).toEqual([])
  })
})

describe('unionSlots', () => {
  it('unisce gli operatori e non ripete gli orari in comune', () => {
    const morning = slotsFor({ operatorHours: MORNING, salonHours: SALON, busy: [], durationMinutes: 30 })
    const afternoon = slotsFor({
      operatorHours: [{ start: t('14:00'), end: t('18:00') }],
      salonHours: SALON, busy: [], durationMinutes: 30,
    })
    const union = asTimes(unionSlots([morning, afternoon]))

    expect(union).toContain('09:00')
    expect(union).toContain('14:00')
    expect(union.length).toBe(new Set(union).size)
    // Due volte lo stesso operatore non raddoppia niente.
    expect(asTimes(unionSlots([morning, morning]))).toEqual(asTimes(morning))
  })
})

describe('canPlace', () => {
  const base = { operatorHours: MORNING, salonHours: SALON, busy: [] as Range[], durationMinutes: 30 }

  it('lo spostamento a mano ignora la griglia: le 09:45 vanno bene', () => {
    expect(canPlace({ ...base, start: t('09:45') })).toBe(true)
  })

  it('rifiuta un orario che sborda dal turno', () => {
    // 12:45 + 30 minuti finisce alle 13:15, fuori dalla fascia.
    expect(canPlace({ ...base, start: t('12:45') })).toBe(false)
    expect(canPlace({ ...base, start: t('08:30') })).toBe(false)
  })

  it('rifiuta un orario sopra un altro appuntamento', () => {
    const busy = [{ start: t('10:00'), end: t('10:45') }]
    expect(canPlace({ ...base, busy, start: t('10:15') })).toBe(false)
    expect(canPlace({ ...base, busy, start: t('10:45') })).toBe(true)
  })

  it('rifiuta se l\'operatore è in ferie', () => {
    expect(canPlace({ ...base, onHoliday: true, start: t('09:45') })).toBe(false)
  })
})

describe('intervalli', () => {
  it('due fasce che si toccano non si sovrappongono', () => {
    expect(overlaps({ start: 540, end: 600 }, { start: 600, end: 660 })).toBe(false)
    expect(overlaps({ start: 540, end: 601 }, { start: 600, end: 660 })).toBe(true)
  })

  it('l\'intersezione vuota vale null', () => {
    expect(intersect({ start: 540, end: 600 }, { start: 600, end: 660 })).toBeNull()
    expect(intersect({ start: 540, end: 660 }, { start: 600, end: 700 })).toEqual({ start: 600, end: 660 })
  })

  it('workRanges taglia il turno sull\'apertura e ordina le fasce', () => {
    const ranges = workRanges(
      [{ start: t('14:00'), end: t('20:00') }, { start: t('07:00'), end: t('12:00') }],
      SALON,
    )
    expect(ranges).toEqual([
      { start: t('09:00'), end: t('12:00') },
      { start: t('14:00'), end: t('19:00') },
    ])
  })
})
