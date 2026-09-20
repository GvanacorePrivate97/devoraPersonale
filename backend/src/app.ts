import Fastify from 'fastify'
import cors from '@fastify/cors'
import helmet from '@fastify/helmet'
import rateLimit from '@fastify/rate-limit'
import multipart from '@fastify/multipart'
import fastifyStatic from '@fastify/static'
import { resolve } from 'node:path'
import { mkdir } from 'node:fs/promises'
import { env } from './config/env.js'
import { authPlugin } from './plugins/auth.js'
import { errorsPlugin } from './plugins/errors.js'
import { registerRoutes } from './modules/index.js'

export async function buildApp() {
  const app = Fastify({
    logger: env.isTest
      ? false
      : {
          level: env.isProduction ? 'info' : 'debug',
          // Niente dati personali nei log: né token, né password, né numeri.
          redact: {
            paths: [
              'req.headers.authorization',
              'req.body.password',
              'req.body.newPassword',
              'req.body.currentPassword',
              'req.body.phone',
              'req.body.email',
            ],
            censor: '[omesso]',
          },
          transport: env.isProduction ? undefined : { target: 'pino-pretty', options: { translateTime: 'HH:MM:ss' } },
        },
    trustProxy: true,
    bodyLimit: 1_048_576,
  })

  await app.register(helmet, { contentSecurityPolicy: false })
  await app.register(cors, {
    origin: env.corsOrigins.length > 0 ? env.corsOrigins : false,
    credentials: true,
  })
  await app.register(rateLimit, {
    max: env.isProduction ? 120 : 1000,
    timeWindow: '1 minute',
    keyGenerator: (req) => `${req.ip}:${req.auth?.sub ?? 'anon'}`,
  })
  await app.register(multipart, { limits: { fileSize: 5 * 1024 * 1024, files: 1 } })

  const uploadsDir = resolve(env.UPLOADS_DIR)
  await mkdir(uploadsDir, { recursive: true })
  await app.register(fastifyStatic, { root: uploadsDir, prefix: '/uploads/', decorateReply: false })

  await app.register(errorsPlugin)
  await app.register(authPlugin)

  app.get('/health', async () => ({ status: 'ok' }))

  await app.register(registerRoutes, { prefix: '/v1' })

  return app
}
