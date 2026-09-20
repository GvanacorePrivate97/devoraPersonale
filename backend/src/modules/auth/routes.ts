import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { auth } from '../../plugins/auth.js'
import { validation } from '../../lib/errors.js'
import {
  emailSchema, nameSchema, passwordSchema, phoneSchema, toValidationError,
} from '../../lib/validation.js'
import * as service from './service.js'

/**
 * Rotte di accesso e profilo. Le uniche pubbliche di tutta l'API sono quelle
 * prima del login: registrazione, accesso, rinnovo, uscita e reset password.
 * Tutto il resto pretende un access token valido, e ogni rotta lavora
 * sull'utente del token — mai su un id che arriva dalla richiesta.
 */

/** Freno sulle rotte che si prestano a essere provate a raffica. */
const sensitive = { config: { rateLimit: { max: 10, timeWindow: '1 minute' } } }

const parse = <T extends z.ZodTypeAny>(schema: T, value: unknown): z.infer<T> => {
  const result = schema.safeParse(value)
  if (!result.success) throw toValidationError(result.error)
  return result.data
}

/** Il refresh token viaggia nel corpo: mai in query string, finirebbe nei log. */
const refreshTokenSchema = z.object({ refreshToken: z.string().min(1, 'Token mancante') })

const prefsSchema = z.object({
  appointmentReminder: z.boolean(),
  waitlistAlerts: z.boolean(),
  marketing: z.boolean(),
})

/** L'app dichiara da quale telefono arriva: serve a spegnere le push giuste. */
const deviceSchema = z.object({
  token: z.string().min(10, 'Token del dispositivo non valido').max(4096),
  platform: z.enum(['ANDROID', 'IOS']),
})

const userAgentOf = (req: FastifyRequest): string | null => req.headers['user-agent'] ?? null

export async function authRoutes(app: FastifyInstance) {
  const anyRole = app.requireRole()

  // --- accesso -------------------------------------------------------------

  app.post('/auth/register', sensitive, async (req, reply) => {
    const body = parse(
      z.object({
        firstName: nameSchema,
        lastName: nameSchema,
        phone: phoneSchema,
        email: emailSchema,
        password: passwordSchema,
      }),
      req.body,
    )
    const result = await service.register(body, userAgentOf(req))
    return reply.status(201).send(result)
  })

  app.post('/auth/login', sensitive, async (req) => {
    // In accesso l'email si normalizza ma non si giudica: un indirizzo
    // malformato è semplicemente uno che non esiste, stessa risposta di sempre.
    const body = parse(
      z.object({ email: z.string().min(1).transform((v) => v.trim().toLowerCase()), password: z.string().min(1) }),
      req.body,
    )
    return service.login(body.email, body.password, userAgentOf(req))
  })

  app.post('/auth/social', sensitive, async (req) => {
    const body = parse(
      z.object({ provider: z.enum(['GOOGLE', 'APPLE']), idToken: z.string().min(1, 'Token mancante') }),
      req.body,
    )
    return service.socialLogin(body.provider, body.idToken, userAgentOf(req))
  })

  app.post('/auth/refresh', async (req) => {
    const body = parse(refreshTokenSchema, req.body)
    return service.refresh(body.refreshToken, userAgentOf(req))
  })

  app.post('/auth/logout', async (req, reply) => {
    const body = parse(refreshTokenSchema, req.body)
    await service.logout(body.refreshToken)
    return reply.status(204).send()
  })

  // --- password ------------------------------------------------------------

  app.post('/auth/password/reset-request', sensitive, async (req, reply) => {
    const body = parse(z.object({ email: emailSchema }), req.body)
    await service.requestPasswordReset(body.email, req.log)
    // Sempre 204, anche se quell'email non esiste: la risposta non deve dire
    // chi è registrato.
    return reply.status(204).send()
  })

  app.post('/auth/password/reset', sensitive, async (req, reply) => {
    const body = parse(z.object({ token: z.string().min(1, 'Token mancante'), newPassword: passwordSchema }), req.body)
    await service.resetPassword(body.token, body.newPassword)
    return reply.status(204).send()
  })

  app.post('/auth/me/password', { ...sensitive, preHandler: anyRole }, async (req) => {
    const body = parse(
      z.object({ currentPassword: z.string().min(1, 'Inserisci la password attuale'), newPassword: passwordSchema }),
      req.body,
    )
    const tokens = await service.changePassword(
      auth(req).sub, body.currentPassword, body.newPassword, userAgentOf(req),
    )
    // Le altre sessioni sono cadute: qui torna la coppia nuova di questa.
    return { tokens }
  })

  // --- profilo -------------------------------------------------------------

  app.get('/auth/me', { preHandler: anyRole }, async (req) => service.currentUser(auth(req).sub))

  app.patch('/auth/me', { preHandler: anyRole }, async (req) => {
    const body = parse(
      z.object({ firstName: nameSchema, lastName: nameSchema, email: emailSchema, phone: phoneSchema }),
      req.body,
    )
    return service.updateProfile(auth(req).sub, body)
  })

  app.get('/auth/me/notification-prefs', { preHandler: anyRole }, async (req) =>
    service.notificationPrefs(auth(req).sub),
  )

  app.put('/auth/me/notification-prefs', { preHandler: anyRole }, async (req) => {
    const body = parse(prefsSchema, req.body)
    return service.saveNotificationPrefs(auth(req).sub, body)
  })

  // --- dispositivi ---------------------------------------------------------

  app.post('/auth/me/devices', { preHandler: anyRole }, async (req, reply) => {
    const body = parse(deviceSchema, req.body)
    await service.registerDevice(auth(req).sub, body.token, body.platform)
    return reply.status(204).send()
  })

  app.delete('/auth/me/devices/:token', { preHandler: anyRole }, async (req, reply) => {
    const params = parse(z.object({ token: z.string().min(1) }), req.params)
    await service.forgetDevice(auth(req).sub, params.token)
    return reply.status(204).send()
  })

  // --- immagine di profilo -------------------------------------------------

  app.post('/auth/me/avatar', { preHandler: anyRole }, async (req) => {
    const file = await req.file().catch(asUploadError)
    if (!file) throw validation('file', 'Nessun file ricevuto')
    if (file.fieldname !== 'file') throw validation('file', 'Il file va inviato nel campo "file"')
    if (!service.AVATAR_MIME_TYPES.includes(file.mimetype)) {
      throw validation('file', 'Sono ammesse solo immagini JPEG, PNG o WebP')
    }
    // `toBuffer` solleva se si supera il limite di 5 MB dichiarato in app.ts.
    const buffer = await file.toBuffer().catch(asUploadError)
    const avatarUrl = await service.saveAvatar(auth(req).sub, { buffer, mimetype: file.mimetype }, req.log)
    return { avatarUrl }
  })

  app.delete('/auth/me/avatar', { preHandler: anyRole }, async (req, reply) => {
    await service.clearAvatar(auth(req).sub, req.log)
    return reply.status(204).send()
  })
}

/** Il file troppo grande arriva come errore del parser: diventa un errore di campo. */
function asUploadError(error: unknown): never {
  const code = String((error as { code?: unknown }).code ?? '')
  if (code === 'FST_REQ_FILE_TOO_LARGE') throw validation('file', 'Immagine troppo grande (massimo 5 MB)')
  if (code === 'FST_INVALID_MULTIPART_CONTENT_TYPE') throw validation('file', 'Invia il file come multipart/form-data')
  throw error
}
