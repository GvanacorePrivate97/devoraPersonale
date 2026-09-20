import { describe, expect, it } from 'vitest'
import {
  durationSchema, emailSchema, nameSchema, normalizePhone, noteSchema,
  passwordSchema, passwordStrength, phoneSchema, priceSchema,
} from '../src/lib/validation.js'

/**
 * Le regole scritte qui sono le stesse che le app applicano nei form: se il
 * server ne cambiasse una, il cliente vedrebbe un campo valido rifiutato (o il
 * contrario). Il numero di telefono conta il doppio, perché finisce in una
 * colonna con un check E.164 e un indice unico.
 */

describe('normalizePhone', () => {
  const cases: Array<[string, string | null, string]> = [
    ['+39 347 812 4490', '+393478124490', 'cellulare italiano con spazi'],
    ['3478124490', '+393478124490', 'cellulare senza prefisso internazionale'],
    ['347.812.4490', '+393478124490', 'punti e trattini si buttano via'],
    ['(+39) 347-812-4490', '+393478124490', 'parentesi e trattini'],
    ['081 555 0180', '+390815550180', 'fisso: lo zero iniziale resta'],
    ['0039 081 555 0180', '+390815550180', '00 vale come +'],
    ['+44 20 7946 0958', '+442079460958', 'numero straniero'],
    ['+1 (202) 555-0147', '+12025550147', 'numero statunitense'],
    ['', null, 'stringa vuota'],
    ['   ', null, 'solo spazi'],
    ['pippo', null, 'testo'],
    ['+39 347', null, 'troppo corto per l\'Italia'],
    ['+39 347 812 4490 12', null, 'troppo lungo per l\'Italia'],
    ['+0123456789', null, 'nessun paese inizia per zero'],
    ['12345', null, 'troppo corto in assoluto'],
  ]

  for (const [input, expected, why] of cases) {
    it(`${why}: ${JSON.stringify(input)} → ${expected ?? 'null'}`, () => {
      expect(normalizePhone(input)).toBe(expected)
    })
  }

  it('tutto ciò che passa soddisfa il check della tabella', () => {
    const e164 = /^\+[1-9][0-9]{7,14}$/
    for (const [input] of cases) {
      const result = normalizePhone(input)
      if (result !== null) expect(result).toMatch(e164)
    }
  })

  it('phoneSchema normalizza e rifiuta', () => {
    expect(phoneSchema.parse(' 347 812 4490 ')).toBe('+393478124490')
    expect(phoneSchema.safeParse('pippo').success).toBe(false)
  })
})

describe('email', () => {
  it('normalizza spazi e maiuscole', () => {
    expect(emailSchema.parse('  Marco.Esposito@Gmail.COM ')).toBe('marco.esposito@gmail.com')
  })

  for (const invalid of ['marco', 'marco@', '@gmail.com', 'marco@gmail', 'mar co@gmail.com', 'marco@gmail.c']) {
    it(`rifiuta ${JSON.stringify(invalid)}`, () => {
      expect(emailSchema.safeParse(invalid).success).toBe(false)
    })
  }

  it('accetta gli indirizzi del seed', () => {
    for (const valid of ['antonio@mencare.it', 'luca.ferrante@mencare.it', 'marco.esposito@gmail.com']) {
      expect(emailSchema.parse(valid)).toBe(valid)
    }
  })
})

describe('password', () => {
  it('accetta la password della demo', () => {
    expect(passwordSchema.parse('mencare2026')).toBe('mencare2026')
  })

  it('vuole almeno otto caratteri, una lettera e un numero', () => {
    expect(passwordSchema.safeParse('men26').success).toBe(false)
    expect(passwordSchema.safeParse('mencareapp').success).toBe(false) // nessun numero
    expect(passwordSchema.safeParse('12345678').success).toBe(false) // nessuna lettera
    expect(passwordSchema.safeParse('a'.repeat(72) + '1').success).toBe(false) // oltre il limite di argon2
  })

  it('il misuratore di forza segue le stesse soglie del form', () => {
    expect(passwordStrength('abc')).toBe('DEBOLE')
    expect(passwordStrength('mencare2026')).toBe('DEBOLE')
    expect(passwordStrength('Mencare2026')).toBe('MEDIA')
    expect(passwordStrength('Mencare2026!')).toBe('FORTE')
  })
})

describe('nomi, note e listino', () => {
  it('i nomi con apostrofi e spazi passano', () => {
    expect(nameSchema.parse(' De Vito ')).toBe('De Vito')
    expect(nameSchema.parse("D'Amico")).toBe("D'Amico")
  })

  it('un nome di una lettera o con cifre non passa', () => {
    expect(nameSchema.safeParse('A').success).toBe(false)
    expect(nameSchema.safeParse('Marco1').success).toBe(false)
    expect(nameSchema.safeParse('').success).toBe(false)
  })

  it('la nota vuota diventa null e oltre 200 caratteri non passa', () => {
    expect(noteSchema.parse('   ')).toBeNull()
    expect(noteSchema.parse(' sfumatura media ')).toBe('sfumatura media')
    expect(noteSchema.safeParse('x'.repeat(201)).success).toBe(false)
  })

  it('le durate vanno a passi di cinque minuti', () => {
    expect(durationSchema.parse(45)).toBe(45)
    expect(durationSchema.safeParse(47).success).toBe(false)
    expect(durationSchema.safeParse(0).success).toBe(false)
    expect(durationSchema.safeParse(600).success).toBe(false)
  })

  it('i prezzi sono centesimi interi non negativi', () => {
    expect(priceSchema.parse(1500)).toBe(1500)
    expect(priceSchema.safeParse(-1).success).toBe(false)
    expect(priceSchema.safeParse(15.5).success).toBe(false)
  })
})
