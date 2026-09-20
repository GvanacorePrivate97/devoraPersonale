import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { CAMPAIGN_BODY_MAX, idSchema, toValidationError } from '../../lib/validation.js'
import { zonedToInstant } from '../../lib/time.js'
import { pool } from '../../db/pool.js'
import * as service from './service.js'
import { CAMPAIGN_SEGMENTS, CAMPAIGN_STATUSES, DASHBOARD_PERIODS } from './service.js'
import { CLIENT_SEGMENTS } from '../crm/service.js'

/**
 * Area titolare. Ogni rotta qui dentro è `OWNER` e basta: i numeri del salone,
 * le regole delle notifiche e le campagne non sono affari dell'operatore.
 */

/** Messaggio italiano al posto di quello inglese di zod per gli enum. */
const italianEnum = (message: string) => ({ errorMap: () => ({ message }) })

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

/**
 * Data e ora dell'invio programmato. Le app mandano l'orario che il titolare ha
 * scelto sul calendario (`2026-10-02T09:00`), che è orario da muro del salone:
 * lo si porta a istante con le stesse regole dell'agenda. Se invece arriva un
 * ISO completo (con Z o con scarto) si prende per quello che è.
 */
const LOCAL_DATE_TIME = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::\d{2})?$/

const scheduledAtSchema = z.string().transform((value, ctx) => {
  const trimmed = value.trim()
  const local = LOCAL_DATE_TIME.exec(trimmed)
  if (local) return zonedToInstant(local[1]!, local[2]!)
  const instant = new Date(trimmed)
  if (Number.isNaN(instant.getTime())) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Data e ora non valide' })
    return z.NEVER
  }
  return instant
})

const campaignNameSchema = z.string().trim().min(2, 'Almeno 2 caratteri').max(60, 'Massimo 60 caratteri')
const campaignTitleSchema = z.string().trim().min(2, 'Almeno 2 caratteri').max(60, 'Massimo 60 caratteri')
/** Il corpo della push sta in 140 caratteri: oltre, il telefono lo taglia. */
const campaignBodySchema = z.string().trim().min(1, 'Scrivi il messaggio').max(CAMPAIGN_BODY_MAX, `Massimo ${CAMPAIGN_BODY_MAX} caratteri`)
const sendCapSchema = z.number().int('Valore non valido').min(1, 'Almeno 1 invio').max(100_000, 'Valore troppo alto')

export async function adminRoutes(app: FastifyInstance) {
  const owner = app.requireRole('OWNER')

  // --- cruscotto -----------------------------------------------------------

  app.get('/admin/dashboard', { preHandler: owner }, async (req) => {
    const q = parse(z.object({ period: z.enum(DASHBOARD_PERIODS, italianEnum('Periodo non valido')).optional() }), req.query)
    return service.dashboard(q.period ?? 'WEEK')
  })

  // --- notifiche del salone ------------------------------------------------

  app.get('/admin/notification-settings', { preHandler: owner }, async () => service.notificationSettings())

  app.put('/admin/notification-settings', { preHandler: owner }, async (req) => {
    // PUT: i quattro interruttori si scrivono tutti insieme, come li mostra la
    // schermata, così non resta mai metà impostazione vecchia.
    const body = parse(
      z.object({
        bookingConfirmation: z.boolean(),
        cancellationAlert: z.boolean(),
        lateOperatorAlert: z.boolean(),
        emptyDayPromos: z.boolean(),
      }),
      req.body,
    )
    return service.updateNotificationSettings(body)
  })

  app.get('/admin/reminder-rules', { preHandler: owner }, async () => ({ rules: await service.reminderRules() }))

  app.post('/admin/reminder-rules', { preHandler: owner }, async (req, reply) => {
    const body = parse(
      z.object({
        hoursBefore: z.coerce
          .number()
          .int('Valore non valido')
          .min(1, 'Almeno 1 ora prima')
          .max(168, 'Al massimo una settimana prima'),
      }),
      req.body,
    )
    return reply.status(201).send(await service.addReminderRule(body.hoursBefore))
  })

  app.delete('/admin/reminder-rules/:id', { preHandler: owner }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    await service.removeReminderRule(id)
    return reply.status(204).send()
  })

  // --- campagne push -------------------------------------------------------

  app.get('/admin/campaigns', { preHandler: owner }, async () => ({ campaigns: await service.campaigns() }))

  // Prima di /admin/campaigns/:id, altrimenti "reach" finirebbe per sembrare un id.
  app.get('/admin/campaigns/reach', { preHandler: owner }, async (req) => {
    const q = parse(z.object({ segment: z.enum(CLIENT_SEGMENTS, italianEnum('Segmento non valido')).optional() }), req.query)
    return service.campaignReach(pool, q.segment ?? 'TUTTI')
  })

  app.post('/admin/campaigns', { preHandler: owner }, async (req, reply) => {
    const body = parse(
      z.object({
        name: campaignNameSchema,
        segment: z.enum(CAMPAIGN_SEGMENTS, italianEnum('Questo segmento non è disponibile per le campagne')),
        title: campaignTitleSchema,
        body: campaignBodySchema,
        scheduledAt: scheduledAtSchema.nullish(),
        repeatWeekly: z.boolean().optional(),
        sendCap: sendCapSchema.nullish(),
        status: z.enum(CAMPAIGN_STATUSES, italianEnum('Stato non valido: bozza o programmata')).optional(),
      }),
      req.body,
    )
    const campaign = await service.createCampaign({
      name: body.name,
      segment: body.segment,
      title: body.title,
      body: body.body,
      scheduledAt: body.scheduledAt ?? null,
      repeatWeekly: body.repeatWeekly ?? false,
      sendCap: body.sendCap ?? null,
      status: body.status ?? 'DRAFT',
    })
    return reply.status(201).send(campaign)
  })

  app.patch('/admin/campaigns/:id', { preHandler: owner }, async (req) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    const body = parse(
      z.object({
        name: campaignNameSchema.optional(),
        segment: z.enum(CAMPAIGN_SEGMENTS, italianEnum('Questo segmento non è disponibile per le campagne')).optional(),
        title: campaignTitleSchema.optional(),
        body: campaignBodySchema.optional(),
        // `null` toglie la programmazione, `undefined` non la tocca.
        scheduledAt: scheduledAtSchema.nullable().optional(),
        repeatWeekly: z.boolean().optional(),
        sendCap: sendCapSchema.nullable().optional(),
        status: z.enum(CAMPAIGN_STATUSES, italianEnum('Stato non valido: bozza o programmata')).optional(),
      }),
      req.body,
    )
    return service.updateCampaign(id, body)
  })

  app.post('/admin/campaigns/:id/send', { preHandler: owner }, async (req, reply) => {
    const { id } = parse(z.object({ id: idSchema }), req.params)
    // 202: presa in carico. L'invio vero lo fa il job, non questa richiesta.
    return reply.status(202).send(await service.sendCampaign(id, req.log))
  })
}
