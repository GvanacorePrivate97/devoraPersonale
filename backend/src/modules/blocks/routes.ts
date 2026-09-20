import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { auth, ownOperatorId } from '../../plugins/auth.js'
import { dateSchema, idSchema, timeSchema, toValidationError } from '../../lib/validation.js'
import { startOfWeek, todayInSalon } from '../../lib/time.js'
import { pool } from '../../db/pool.js'
import * as service from './service.js'

/**
 * Rotte dei blocchi. Il titolare lavora su chiunque, l'operatore solo sulla
 * propria poltrona: l'`operatorId` che arriva nella richiesta di uno STAFF non
 * viene mai usato, si prende quello del token.
 */

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

/** Chi guarda: il titolare l'operatore chiesto (o tutti), lo staff solo sé stesso. */
function scopedOperatorId(req: FastifyRequest, requested?: string | null): string | null {
  if (auth(req).role === 'OWNER') return requested ?? null
  return ownOperatorId(req)
}

/** Chi scrive: un blocco ha sempre un padrone, e per lo staff è sé stesso. */
function targetOperatorId(req: FastifyRequest, requested?: string | null): string {
  if (auth(req).role === 'OWNER') return requested ?? ownOperatorId(req)
  return ownOperatorId(req)
}

export async function blocksRoutes(app: FastifyInstance) {
  const staffOrOwner = app.requireRole('STAFF', 'OWNER')

  app.get('/blocks', { preHandler: staffOrOwner }, async (req) => {
    const q = parse(z.object({ operatorId: idSchema.nullish(), date: dateSchema }), req.query)
    return { blocks: await service.blocksForDay(pool, scopedOperatorId(req, q.operatorId), q.date) }
  })

  app.get('/blocks/week', { preHandler: staffOrOwner }, async (req) => {
    const q = parse(
      z.object({ operatorId: idSchema.nullish(), weekStart: dateSchema.optional() }),
      req.query,
    )
    const from = q.weekStart ?? startOfWeek(todayInSalon())
    return { blocks: await service.blocksForWeek(pool, scopedOperatorId(req, q.operatorId), from) }
  })

  // Anteprima prima di bloccare: quali appuntamenti verrebbero travolti.
  app.get('/blocks/conflicts', { preHandler: staffOrOwner }, async (req) => {
    const q = parse(
      z.object({ operatorId: idSchema.nullish(), date: dateSchema, start: timeSchema, end: timeSchema }),
      req.query,
    )
    const operatorId = targetOperatorId(req, q.operatorId)
    return { appointments: await service.conflicts(pool, operatorId, q.date, q.start, q.end) }
  })

  app.post('/blocks', { preHandler: staffOrOwner }, async (req, reply) => {
    const body = parse(
      z.object({
        operatorId: idSchema.nullish(),
        reason: z.enum(['PERMESSO', 'PAUSA', 'FERIE', 'CORSO']),
        date: dateSchema,
        start: timeSchema,
        end: timeSchema,
        label: z.string().trim().max(60, 'Massimo 60 caratteri').nullish(),
      }),
      req.body,
    )
    const block = await service.createBlock({
      operatorId: targetOperatorId(req, body.operatorId),
      reason: body.reason,
      date: body.date,
      start: body.start,
      end: body.end,
      label: body.label && body.label.length > 0 ? body.label : null,
      createdByUserId: auth(req).sub,
    })
    return reply.status(201).send(block)
  })

  app.delete('/blocks/:id', { preHandler: staffOrOwner }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    await service.deleteBlock(
      id,
      auth(req).role === 'OWNER' ? undefined : ownOperatorId(req),
      req.log,
    )
    return reply.status(204).send()
  })
}
