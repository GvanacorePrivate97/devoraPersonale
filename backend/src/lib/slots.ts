/**
 * Motore degli slot: porta a termine, con le stesse regole, ciò che le app
 * calcolavano da sole (`SlotEngine` su Android e iOS). Resta puro — niente
 * database qui dentro — così i test lo verificano a tavolino e le due app
 * ottengono le stesse risposte.
 *
 * Gli orari sono minuti dalla mezzanotte, nel fuso del salone.
 */

/** Passo della griglia mostrata al cliente. */
export const GRID_MINUTES = 30
/** Preavviso minimo per prenotare oggi. */
export const LEAD_MINUTES = 30

export type Range = { start: number; end: number }

export function overlaps(a: Range, b: Range): boolean {
  return a.start < b.end && b.start < a.end
}

export function intersect(a: Range, b: Range): Range | null {
  const start = Math.max(a.start, b.start)
  const end = Math.min(a.end, b.end)
  return start < end ? { start, end } : null
}

/**
 * Fasce in cui l'operatore può davvero lavorare: i suoi turni intersecati con
 * l'apertura del salone. Fuori dall'apertura non si prenota mai, nemmeno se
 * l'operatore ha orari più larghi.
 */
export function workRanges(operatorHours: Range[], salonHours: Range[]): Range[] {
  const out: Range[] = []
  for (const shift of operatorHours) {
    for (const open of salonHours) {
      const slice = intersect(shift, open)
      if (slice) out.push(slice)
    }
  }
  return out.sort((a, b) => a.start - b.start)
}

export type SlotInput = {
  /** Turni dell'operatore quel giorno (già filtrati sul giorno della settimana). */
  operatorHours: Range[]
  /** Apertura del salone quel giorno; vuota = chiuso, e vince su tutto. */
  salonHours: Range[]
  /** Appuntamenti attivi e blocchi dell'operatore, come intervalli occupati. */
  busy: Range[]
  /** Somma delle durate dei servizi scelti. */
  durationMinutes: number
  /** L'operatore è in ferie quel giorno. */
  onHoliday?: boolean
  /** Il giorno è già passato. */
  isPast?: boolean
  /** Minuti da mezzanotte di "adesso", solo se il giorno è oggi. */
  nowMinutes?: number | null
}

/**
 * Orari proponibili al cliente. La griglia riparte dall'inizio di ogni fascia:
 * un turno 14:00–19:00 offre 14:00, 14:30… anche se la mattina finiva alle 13:00.
 */
export function slotsFor(input: SlotInput): number[] {
  const { operatorHours, salonHours, busy, durationMinutes } = input
  if (input.isPast) return []
  if (input.onHoliday) return []
  if (salonHours.length === 0 || operatorHours.length === 0) return []
  if (durationMinutes <= 0) return []

  const ranges = workRanges(operatorHours, salonHours)
  if (ranges.length === 0) return []

  const earliest = input.nowMinutes != null ? input.nowMinutes + LEAD_MINUTES : 0
  const slots: number[] = []
  for (const range of ranges) {
    for (let t = range.start; t + durationMinutes <= range.end; t += GRID_MINUTES) {
      if (t < earliest) continue
      const candidate = { start: t, end: t + durationMinutes }
      if (busy.some((b) => overlaps(candidate, b))) continue
      slots.push(t)
    }
  }
  return [...new Set(slots)].sort((a, b) => a - b)
}

/**
 * Regola dello spostamento a mano (drag & drop del titolare, modifica in
 * agenda): niente griglia e niente preavviso, ma l'appuntamento deve stare
 * tutto dentro una fascia di lavoro e non toccare nulla di occupato.
 */
export function canPlace(input: Omit<SlotInput, 'nowMinutes' | 'isPast'> & { start: number }): boolean {
  const { operatorHours, salonHours, busy, durationMinutes, start } = input
  if (input.onHoliday) return false
  if (durationMinutes <= 0) return false
  const end = start + durationMinutes
  if (end > 24 * 60) return false

  const ranges = workRanges(operatorHours, salonHours)
  const fits = ranges.some((r) => start >= r.start && end <= r.end)
  if (!fits) return false

  const candidate = { start, end }
  return !busy.some((b) => overlaps(candidate, b))
}

/**
 * "Qualsiasi operatore" è l'unione, non l'intersezione: un orario compare se
 * almeno un operatore abilitato è libero, e chi prenota decide poi con chi.
 */
export function unionSlots(perOperator: number[][]): number[] {
  return [...new Set(perOperator.flat())].sort((a, b) => a - b)
}
