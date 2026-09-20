import { existsSync } from 'node:fs'
import { z } from 'zod'

// Il file .env si carica qui, una volta sola: così `npm run migrate`, il seed,
// i test e il server vedono tutti la stessa configurazione senza --env-file.
if (existsSync('.env')) {
  try {
    process.loadEnvFile('.env')
  } catch {
    // Variabili già presenti nell'ambiente: va bene così.
  }
}

/**
 * Configurazione letta dall'ambiente. Il processo non parte se manca o è
 * malformata: meglio un errore all'avvio che una chiave vuota in produzione.
 */
const schema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  HOST: z.string().default('0.0.0.0'),
  DATABASE_URL: z.string().min(1),
  JWT_ACCESS_SECRET: z.string().min(32),
  JWT_REFRESH_SECRET: z.string().min(32),
  ACCESS_TOKEN_TTL_MINUTES: z.coerce.number().int().positive().default(15),
  REFRESH_TOKEN_TTL_DAYS: z.coerce.number().int().positive().default(30),
  PASSWORD_RESET_TTL_MINUTES: z.coerce.number().int().positive().default(30),
  CORS_ORIGINS: z.string().default(''),
  PUSH_PROVIDER: z.enum(['log', 'fcm']).default('log'),
  FCM_SERVICE_ACCOUNT_JSON: z.string().optional(),
  UPLOADS_DIR: z.string().default('./uploads'),
  PUBLIC_BASE_URL: z.string().default('http://localhost:3000'),
  SALON_TIMEZONE: z.string().default('Europe/Rome'),
})

const parsed = schema.safeParse(process.env)
if (!parsed.success) {
  const details = parsed.error.issues.map((i) => `  ${i.path.join('.')}: ${i.message}`).join('\n')
  throw new Error(`Configurazione non valida:\n${details}`)
}

export const env = {
  ...parsed.data,
  corsOrigins: parsed.data.CORS_ORIGINS.split(',').map((o) => o.trim()).filter(Boolean),
  isProduction: parsed.data.NODE_ENV === 'production',
  isTest: parsed.data.NODE_ENV === 'test',
}

export type Env = typeof env
