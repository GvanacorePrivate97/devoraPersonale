import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { auth, ownOperatorId } from '../../plugins/auth.js'
import { forbidden } from '../../lib/errors.js'
import {
  dateSchema, durationSchema, emailSchema, idSchema, labelSchema, nameSchema, phoneSchema, priceSchema,
  timeSchema, toValidationError,
} from '../../lib/validation.js'
import { minutesOfTime } from '../../lib/time.js'
import { pool } from '../../db/pool.js'
import * as service from './service.js'

/**
 * Rotte del listino e della squadra. Leggere è di tutti i ruoli — cliente
 * compreso, perché il wizard di prenotazione vive su questi dati — mentre
 * scrivere è del solo titolare. Le ferie fanno eccezione: anche l'operatore le
 * gestisce, ma solo le proprie, e l'id che arriva dalla richiesta non conta.
 */

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

const idsSchema = z.array(idSchema)

const timeRangeSchema = z
  .object({ start: timeSchema, end: timeSchema })
  .refine((r) => minutesOfTime(r.start) < minutesOfTime(r.end), 'La fine deve venire dopo l’inizio')

/**
 * Orari settimanali: chiavi "1".."7" (ISO, 1 lunedì). Più fasce nello stesso
 * giorno sono un turno spezzato e vanno tenute tutte; due fasce che si
 * accavallano invece sono un errore di compilazione, non una scelta.
 */
const weeklyHoursSchema = z
  .record(z.string(), z.array(timeRangeSchema))
  .superRefine((value, ctx) => {
    for (const [day, ranges] of Object.entries(value)) {
      if (!/^[1-7]$/.test(day)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: [day], message: 'Giorno della settimana non valido' })
        continue
      }
      const sorted = [...ranges].sort((a, b) => minutesOfTime(a.start) - minutesOfTime(b.start))
      for (let i = 1; i < sorted.length; i++) {
        if (minutesOfTime(sorted[i]!.start) < minutesOfTime(sorted[i - 1]!.end)) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, path: [day], message: 'Due turni dello stesso giorno si sovrappongono' })
          break
        }
      }
    }
  })

const descriptionSchema = z
  .string()
  .max(500, 'Massimo 500 caratteri')
  .transform((v) => (v.trim().length === 0 ? null : v.trim()))
  .nullable()

export async function catalogRoutes(app: FastifyInstance) {
  const anyRole = app.requireRole('CLIENT', 'STAFF', 'OWNER')
  const staffOrOwner = app.requireRole('STAFF', 'OWNER')
  const owner = app.requireRole('OWNER')

  // --- lettura -------------------------------------------------------------

  // Una sola chiamata all'avvio: salone, listino e squadra insieme.
  app.get('/catalog', { preHandler: anyRole }, async () => service.catalog(pool))

  app.get('/catalog/services', { preHandler: anyRole }, async () => ({
    services: await service.services(pool),
  }))

  app.get('/catalog/operators', { preHandler: anyRole }, async () => ({
    operators: await service.operators(pool),
  }))

  // --- listino -------------------------------------------------------------

  app.post('/catalog/services', { preHandler: owner }, async (req, reply) => {
    const body = parse(
      z.object({
        // Un servizio non è una persona: "Shampoo + taglio" deve passare.
        name: labelSchema,
        // Il prezzo viaggia in centesimi interi: mai un decimale, mai un float.
        durationMinutes: durationSchema,
        priceCents: priceSchema,
        description: descriptionSchema.optional(),
        featured: z.boolean().optional(),
        active: z.boolean().optional(),
        operatorIds: idsSchema.optional(),
      }),
      req.body,
    )
    return reply.status(201).send(await service.createService(body))
  })

  app.patch('/catalog/services/:id', { preHandler: owner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(
      z.object({
        name: labelSchema.optional(),
        durationMinutes: durationSchema.optional(),
        priceCents: priceSchema.optional(),
        description: descriptionSchema.optional(),
        featured: z.boolean().optional(),
        active: z.boolean().optional(),
      }),
      req.body,
    )
    return service.updateService(id, body)
  })

  app.patch('/catalog/services/:id/operators', { preHandler: owner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(z.object({ operatorIds: idsSchema }), req.body)
    return service.setServiceOperators(id, body.operatorIds)
  })

  // --- squadra -------------------------------------------------------------

  app.post('/catalog/operators', { preHandler: owner }, async (req, reply) => {
    const body = parse(
      z.object({
        name: nameSchema,
        email: emailSchema,
        phone: phoneSchema,
        title: z.string().trim().max(50, 'Massimo 50 caratteri').optional(),
        bio: z.string().max(500, 'Massimo 500 caratteri').optional(),
        specialties: z.array(z.string().trim().min(1).max(40)).max(10, 'Al massimo 10 specialità').optional(),
        weeklyHours: weeklyHoursSchema.optional(),
        serviceIds: idsSchema.optional(),
      }),
      req.body,
    )
    // La password provvisoria torna qui e solo qui: non è recuperabile dopo.
    return reply.status(201).send(await service.createOperator(body))
  })

  app.patch('/catalog/operators/:id', { preHandler: owner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(
      z.object({
        name: nameSchema.optional(),
        title: z.string().trim().max(50, 'Massimo 50 caratteri').optional(),
        bio: z.string().max(500, 'Massimo 500 caratteri').optional(),
        specialties: z.array(z.string().trim().min(1).max(40)).max(10, 'Al massimo 10 specialità').optional(),
        active: z.boolean().optional(),
      }),
      req.body,
    )
    return service.updateOperator(id, body)
  })

  app.put('/catalog/operators/:id/hours', { preHandler: owner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(z.object({ weeklyHours: weeklyHoursSchema }), req.body)
    return service.setOperatorHours(id, body.weeklyHours)
  })

  app.put('/catalog/operators/:id/services', { preHandler: owner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(z.object({ serviceIds: idsSchema }), req.body)
    return service.setOperatorServices(id, body.serviceIds)
  })

  // --- salone --------------------------------------------------------------

  app.put('/catalog/salon', { preHandler: owner }, async (req) => {
    const body = parse(
      z.object({
        name: z.string().trim().min(2, 'Nome troppo corto').max(80, 'Massimo 80 caratteri'),
        address: z.string().trim().min(3, 'Indirizzo troppo corto').max(120, 'Massimo 120 caratteri'),
        city: z.string().trim().min(2, 'Città troppo corta').max(80, 'Massimo 80 caratteri'),
        phone: phoneSchema.nullish(),
        weeklyHours: weeklyHoursSchema,
      }),
      req.body,
    )
    return service.updateSalon({
      name: body.name,
      address: body.address,
      city: body.city,
      phone: body.phone ?? null,
      weeklyHours: body.weeklyHours,
    })
  })

  // --- ferie ---------------------------------------------------------------

  /** L'operatore vive solo sulle proprie ferie: l'id nell'URL non lo aggira. */
  const holidayOperatorId = (req: FastifyRequest, requested: string): string => {
    if (auth(req).role === 'OWNER') return requested
    const own = ownOperatorId(req)
    if (own !== requested) throw forbidden('Puoi gestire solo le tue ferie')
    return own
  }

  app.get('/catalog/operators/:id/holidays', { preHandler: staffOrOwner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    return { holidays: await service.holidays(pool, holidayOperatorId(req, id)) }
  })

  app.post('/catalog/operators/:id/holidays', { preHandler: staffOrOwner }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(
      z.object({ from: dateSchema, to: dateSchema, label: labelSchema.optional() }),
      req.body,
    )
    const holiday = await service.addHoliday({
      operatorId: holidayOperatorId(req, id),
      from: body.from,
      to: body.to,
      label: body.label,
    })
    return reply.status(201).send(holiday)
  })

  app.delete('/catalog/holidays/:id', { preHandler: staffOrOwner }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    // Al titolare nessun vincolo; all'operatore solo le proprie ferie.
    await service.deleteHoliday(id, auth(req).role === 'OWNER' ? undefined : ownOperatorId(req))
    return reply.status(204).send()
  })
}
