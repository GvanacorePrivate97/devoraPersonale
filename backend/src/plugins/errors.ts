import type { FastifyInstance } from 'fastify'
import fp from 'fastify-plugin'
import { ZodError } from 'zod'
import { ApiError } from '../lib/errors.js'
import { toValidationError } from '../lib/validation.js'
import { pgCode, PG } from '../db/pool.js'

/**
 * Tutte le risposte d'errore hanno la stessa forma `{ error: { code, message,
 * field? } }`: le app leggono `code` e non devono interpretare il testo.
 */
export const errorsPlugin = fp(async (app: FastifyInstance) => {
  app.setErrorHandler((error, request, reply) => {
    if (error instanceof ApiError) {
      if (error.status >= 500) request.log.error({ err: error }, 'errore applicativo')
      return reply.status(error.status).send(error.toBody())
    }

    if (error instanceof ZodError) {
      const api = toValidationError(error)
      return reply.status(api.status).send(api.toBody())
    }

    // Il vincolo di non sovrapposizione è la rete di sicurezza della
    // prenotazione: se scatta, l'orario se l'è preso qualcun altro.
    if (pgCode(error) === PG.exclusionViolation) {
      const api = new ApiError('SLOT_NO_LONGER_AVAILABLE', 'Questo orario è appena stato preso')
      return reply.status(api.status).send(api.toBody())
    }
    if (pgCode(error) === PG.serializationFailure) {
      const api = new ApiError('SLOT_NO_LONGER_AVAILABLE', 'Riprova: qualcuno stava prenotando lo stesso orario')
      return reply.status(api.status).send(api.toBody())
    }

    const status = (error as { statusCode?: number }).statusCode ?? 500
    if (status === 429) {
      return reply.status(429).send({ error: { code: 'RATE_LIMITED', message: 'Troppe richieste, riprova tra poco' } })
    }
    if (status >= 400 && status < 500) {
      const message = error instanceof Error ? error.message : 'Richiesta non valida'
      return reply.status(status).send({ error: { code: 'VALIDATION', message } })
    }

    request.log.error({ err: error }, 'errore non gestito')
    return reply.status(500).send({ error: { code: 'UNKNOWN', message: 'Errore imprevisto' } })
  })

  app.setNotFoundHandler((_request, reply) =>
    reply.status(404).send({ error: { code: 'NOT_FOUND', message: 'Endpoint inesistente' } }),
  )
})
