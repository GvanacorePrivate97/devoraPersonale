import type { FastifyInstance } from 'fastify'
import { authRoutes } from './auth/routes.js'
import { catalogRoutes } from './catalog/routes.js'
import { bookingRoutes } from './booking/routes.js'
import { blocksRoutes } from './blocks/routes.js'
import { crmRoutes } from './crm/routes.js'
import { adminRoutes } from './admin/routes.js'
import { notificationRoutes } from './notifications/routes.js'

/** Tutte le rotte vivono sotto /v1. */
export async function registerRoutes(app: FastifyInstance) {
  await app.register(authRoutes)
  await app.register(catalogRoutes)
  await app.register(bookingRoutes)
  await app.register(blocksRoutes)
  await app.register(crmRoutes)
  await app.register(adminRoutes)
  await app.register(notificationRoutes)
}
