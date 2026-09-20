import { buildApp } from './app.js'
import { env } from './config/env.js'
import { closePool } from './db/pool.js'
import { startJobs, stopJobs } from './jobs/index.js'

const app = await buildApp()

try {
  await startJobs(app.log)
  await app.listen({ port: env.PORT, host: env.HOST })
} catch (error) {
  app.log.error(error)
  process.exit(1)
}

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, async () => {
    app.log.info('arresto in corso')
    await app.close()
    await stopJobs()
    await closePool()
    process.exit(0)
  })
}
