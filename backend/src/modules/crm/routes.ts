import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { auth } from '../../plugins/auth.js'
import { emailSchema, idSchema, nameSchema, phoneSchema, toValidationError } from '../../lib/validation.js'
import { pool } from '../../db/pool.js'
import * as service from './service.js'
import { CLIENT_SEGMENTS } from './service.js'

/**
 * Rubrica clienti: la usano sia l'operatore sia il titolare, perché la scheda
 * cliente è la stessa in tutte e due le aree. L'unica differenza è il denaro,
 * e la decide il server guardando il ruolo — `includeMoney` — non la schermata.
 */

/** Messaggio italiano al posto di quello inglese di zod per gli enum. */
const italianEnum = (message: string) => ({ errorMap: () => ({ message }) })

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

/** Il totale speso e i prezzi dello storico li vede solo il titolare. */
const seesMoney = (role: string) => role === 'OWNER'

export async function crmRoutes(app: FastifyInstance) {
  const staffOrOwner = app.requireRole('STAFF', 'OWNER')

  app.get('/crm/clients', { preHandler: staffOrOwner }, async (req) => {
    const q = parse(
      z.object({
        query: z.string().max(100).optional(),
        segment: z.enum(CLIENT_SEGMENTS, italianEnum('Segmento non valido')).optional(),
        limit: z.coerce.number().int().min(1).max(100).optional(),
        cursor: z.string().max(400).optional(),
      }),
      req.query,
    )
    return service.listClients(pool, {
      query: q.query ?? null,
      segment: q.segment ?? 'TUTTI',
      limit: q.limit,
      cursor: q.cursor ?? null,
      includeMoney: seesMoney(auth(req).role),
    })
  })

  app.get('/crm/clients/:id', { preHandler: staffOrOwner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    return service.clientDetail(pool, id, seesMoney(auth(req).role))
  })

  app.post('/crm/clients', { preHandler: staffOrOwner }, async (req, reply) => {
    const body = parse(
      z.object({
        firstName: nameSchema,
        lastName: nameSchema,
        phone: phoneSchema,
        email: emailSchema.nullish(),
      }),
      req.body,
    )
    const client = await service.createClient(
      { firstName: body.firstName, lastName: body.lastName, phone: body.phone, email: body.email ?? null },
      seesMoney(auth(req).role),
    )
    return reply.status(201).send(client)
  })

  app.patch('/crm/clients/:id', { preHandler: staffOrOwner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(
      z.object({
        firstName: nameSchema.optional(),
        lastName: nameSchema.optional(),
        phone: phoneSchema.optional(),
        // `null` toglie l'email, `undefined` la lascia com'è.
        email: emailSchema.nullable().optional(),
        marketingOptIn: z.boolean().optional(),
        preferredOperatorId: idSchema.nullable().optional(),
        preferredServiceIds: z.array(idSchema).max(20).optional(),
      }),
      req.body,
    )
    return service.updateClient(id, body, seesMoney(auth(req).role))
  })
}
