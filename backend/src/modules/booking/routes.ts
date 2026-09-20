import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { auth, ownClientId } from '../../plugins/auth.js'
import { forbidden, notFound } from '../../lib/errors.js'
import { dateSchema, idSchema, noteSchema, timeSchema, toValidationError } from '../../lib/validation.js'
import { pool } from '../../db/pool.js'
import { startOfWeek, addDays } from '../../lib/time.js'
import * as service from './service.js'

/**
 * Rotte dell'agenda. Ogni rotta dichiara i ruoli ammessi e riverifica a chi
 * appartiene la riga: un cliente vede solo i propri appuntamenti, un operatore
 * solo la propria poltrona, il titolare tutto.
 */

const serviceIdsSchema = z
  .union([z.string(), z.array(z.string())])
  .transform((v) => (Array.isArray(v) ? v : v.split(',')).map((s) => s.trim()).filter(Boolean))
  .pipe(z.array(idSchema).min(1, 'Scegli almeno un servizio'))

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

export async function bookingRoutes(app: FastifyInstance) {
  const anyRole = app.requireRole('CLIENT', 'STAFF', 'OWNER')
  const staffOrOwner = app.requireRole('STAFF', 'OWNER')

  // --- disponibilità -------------------------------------------------------

  app.get('/booking/availability', { preHandler: anyRole }, async (req) => {
    const q = parse(
      z.object({
        operatorId: idSchema.nullish(), serviceIds: serviceIdsSchema, date: dateSchema,
        ignoreAppointmentId: idSchema.optional(),
      }),
      req.query,
    )
    return service.availability(q.operatorId ?? null, q.serviceIds, q.date, q.ignoreAppointmentId)
  })

  app.get('/booking/days', { preHandler: anyRole }, async (req) => {
    const q = parse(
      z.object({
        operatorId: idSchema.nullish(), serviceIds: serviceIdsSchema, from: dateSchema, to: dateSchema,
        ignoreAppointmentId: idSchema.optional(),
      }),
      req.query,
    )
    return service.availabilityRange(q.operatorId ?? null, q.serviceIds, q.from, q.to, q.ignoreAppointmentId)
  })

  app.get('/booking/next-availability', { preHandler: anyRole }, async (req) => {
    const q = parse(z.object({ serviceIds: serviceIdsSchema }), req.query)
    return { operators: await service.nextAvailability(q.serviceIds) }
  })

  // --- appuntamenti --------------------------------------------------------

  app.get('/appointments/me', { preHandler: app.requireRole('CLIENT') }, async (req) => ({
    appointments: await service.appointmentsForClient(pool, ownClientId(req)),
  }))

  app.get('/appointments', { preHandler: staffOrOwner }, async (req) => {
    const claims = auth(req)
    const q = parse(z.object({ operatorId: idSchema.optional(), date: dateSchema }), req.query)
    // L'operatore vede la propria agenda e basta: il parametro non lo aggira.
    const operatorId = claims.role === 'OWNER' ? (q.operatorId ?? claims.operatorId!) : claims.operatorId!
    return { appointments: await service.appointmentsForOperatorDay(pool, operatorId, q.date) }
  })

  app.get('/appointments/week', { preHandler: app.requireRole('OWNER') }, async (req) => {
    const q = parse(z.object({ weekStart: dateSchema.optional() }), req.query)
    const from = q.weekStart ?? startOfWeek(new Date().toISOString().slice(0, 10))
    return { appointments: await service.appointmentsBetween(pool, from, addDays(from, 6)) }
  })

  app.get('/appointments/:id', { preHandler: anyRole }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const appointment = await service.appointmentById(pool, id)
    if (!appointment) throw notFound('Appuntamento')
    const claims = auth(req)
    if (claims.role === 'CLIENT' && appointment.clientId !== claims.clientId) throw forbidden()
    if (claims.role === 'STAFF' && appointment.operatorId !== claims.operatorId) throw forbidden()
    return appointment
  })

  app.post('/appointments', { preHandler: anyRole }, async (req, reply) => {
    const claims = auth(req)
    const body = parse(
      z.object({
        clientId: idSchema.optional(),
        operatorId: idSchema.nullish(),
        serviceIds: serviceIdsSchema,
        date: dateSchema,
        time: timeSchema,
        noteForOperator: noteSchema.optional(),
        channel: z.enum(['APP', 'PHONE', 'WALK_IN']).optional(),
        replacesAppointmentId: idSchema.optional(),
      }),
      req.body,
    )

    // Il cliente prenota solo per sé; staff e titolare per la scheda indicata.
    const clientId = claims.role === 'CLIENT' ? ownClientId(req) : body.clientId
    if (!clientId) throw notFound('Cliente')
    const operatorId = claims.role === 'STAFF' ? claims.operatorId! : (body.operatorId ?? null)

    // Modifica: si sostituisce solo ciò che si potrebbe annullare.
    if (body.replacesAppointmentId) {
      const old = await service.appointmentById(pool, body.replacesAppointmentId)
      if (!old) throw notFound('Appuntamento')
      if (claims.role === 'CLIENT' && old.clientId !== clientId) throw forbidden()
      if (claims.role === 'STAFF' && old.operatorId !== claims.operatorId) throw forbidden()
    }

    const appointment = await service.book(
      {
        clientId,
        operatorId,
        serviceIds: body.serviceIds,
        date: body.date,
        time: body.time,
        noteForOperator: body.noteForOperator ?? null,
        channel: body.channel ?? (claims.role === 'CLIENT' ? 'APP' : 'PHONE'),
        createdByUserId: claims.sub,
        replaces: body.replacesAppointmentId
          ? { appointmentId: body.replacesAppointmentId, by: claims.role === 'CLIENT' ? 'CLIENT' : 'SALON' }
          : null,
      },
      req.log,
    )
    return reply.status(201).send(appointment)
  })

  app.patch('/appointments/:id/schedule', { preHandler: anyRole }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(
      z.object({ date: dateSchema, time: timeSchema, operatorId: idSchema.nullish() }),
      req.body,
    )
    const claims = auth(req)
    const appointment = await service.appointmentById(pool, id)
    if (!appointment) throw notFound('Appuntamento')
    if (claims.role === 'CLIENT' && appointment.clientId !== claims.clientId) throw forbidden()
    if (claims.role === 'STAFF' && appointment.operatorId !== claims.operatorId) throw forbidden()

    return service.reschedule(
      {
        appointmentId: id,
        date: body.date,
        time: body.time,
        // Solo il titolare può spostare un appuntamento su un altro operatore.
        operatorId: claims.role === 'OWNER' ? (body.operatorId ?? null) : null,
        freeForm: claims.role !== 'CLIENT',
      },
      req.log,
    )
  })

  app.post('/appointments/:id/cancel', { preHandler: anyRole }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const claims = auth(req)
    const appointment = await service.appointmentById(pool, id)
    if (!appointment) throw notFound('Appuntamento')
    if (claims.role === 'CLIENT' && appointment.clientId !== claims.clientId) throw forbidden()
    if (claims.role === 'STAFF' && appointment.operatorId !== claims.operatorId) throw forbidden()

    await service.cancel({ appointmentId: id, by: claims.role === 'CLIENT' ? 'CLIENT' : 'SALON' }, req.log)
    return reply.status(204).send()
  })

  app.post('/appointments/:id/status', { preHandler: staffOrOwner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(z.object({ status: z.enum(['IN_PROGRESS', 'COMPLETED', 'NO_SHOW']) }), req.body)
    const claims = auth(req)
    const appointment = await service.appointmentById(pool, id)
    if (!appointment) throw notFound('Appuntamento')
    if (claims.role === 'STAFF' && appointment.operatorId !== claims.operatorId) throw forbidden()
    return service.setStatus(id, body.status, req.log)
  })

  // --- lista d'attesa ------------------------------------------------------

  app.get('/waitlist/me', { preHandler: app.requireRole('CLIENT') }, async (req) => ({
    entries: await service.waitlistForClient(pool, ownClientId(req)),
  }))

  app.post('/waitlist', { preHandler: app.requireRole('CLIENT') }, async (req, reply) => {
    const body = parse(
      z.object({
        date: dateSchema,
        time: timeSchema.nullish(),
        operatorId: idSchema.nullish(),
        serviceIds: serviceIdsSchema,
      }),
      req.body,
    )
    const entry = await service.joinWaitlist({
      clientId: ownClientId(req),
      date: body.date,
      time: body.time ?? null,
      operatorId: body.operatorId ?? null,
      serviceIds: body.serviceIds,
    })
    return reply.status(201).send(entry)
  })

  app.delete('/waitlist/:id', { preHandler: app.requireRole('CLIENT') }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    await service.leaveWaitlist(id, ownClientId(req))
    return reply.status(204).send()
  })
}
