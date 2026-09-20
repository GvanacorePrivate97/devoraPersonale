import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { auth } from '../../plugins/auth.js'
import { idSchema, toValidationError } from '../../lib/validation.js'
import { pool } from '../../db/pool.js'
import * as service from './service.js'

/**
 * Rotte della campanella. Valgono per tutti e tre i ruoli, ma l'account non si
 * sceglie: viene dai claims verificati da `requireRole`. Nessuna di queste
 * rotte accetta un userId, altrimenti basterebbe cambiarlo per leggere le
 * notifiche di un altro.
 */

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

export async function notificationRoutes(app: FastifyInstance) {
  const anyRole = app.requireRole('CLIENT', 'STAFF', 'OWNER')

  app.get('/notifications', { preHandler: anyRole }, async (req) => {
    const q = parse(
      z.object({
        limit: z.coerce.number().int().min(1, 'Valore non valido').max(service.MAX_LIMIT).optional(),
        cursor: z.string().min(1).optional(),
      }),
      req.query,
    )
    return service.listForUser(pool, auth(req).sub, { limit: q.limit ?? null, cursor: q.cursor ?? null })
  })

  // Le app tornano il contatore aggiornato invece di 204: la campanella deve
  // ridisegnarsi subito e così evita una seconda chiamata.
  app.post('/notifications/read-all', { preHandler: anyRole }, async (req) => {
    const userId = auth(req).sub
    const marked = await service.markAllRead(pool, userId)
    return { marked, unreadCount: await service.unreadCount(pool, userId) }
  })

  app.post('/notifications/:id/read', { preHandler: anyRole }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const userId = auth(req).sub
    await service.markRead(pool, userId, id)
    return { marked: 1, unreadCount: await service.unreadCount(pool, userId) }
  })
}
