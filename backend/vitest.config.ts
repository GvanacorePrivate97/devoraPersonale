import { defineConfig } from 'vitest/config'

/**
 * I test girano contro il database locale vero: è l'unico modo per mettere
 * alla prova il vincolo di non sovrapposizione e le transazioni, che sono
 * proprio ciò che protegge la prenotazione. Per questo niente parallelismo fra
 * file — ognuno svuota le tabelle prima di ogni test.
 */

// `config/env` pretende DATABASE_URL e i segreti JWT: li leggiamo da .env qui,
// nel processo principale, e li passiamo ai worker con `test.env`.
try {
  process.loadEnvFile(new URL('./.env', import.meta.url))
} catch {
  // Nessun .env: valgono le variabili già presenti nell'ambiente (CI).
}

const DEV_FALLBACKS: Record<string, string> = {
  DATABASE_URL: 'postgres://postgres@localhost:55432/mencare',
  JWT_ACCESS_SECRET: 'test-access-secret-0123456789-0123456789',
  JWT_REFRESH_SECRET: 'test-refresh-secret-0123456789-0123456789',
  SALON_TIMEZONE: 'Europe/Rome',
  PUSH_PROVIDER: 'log',
}

const testEnv: Record<string, string> = { NODE_ENV: 'test' }
for (const [key, fallback] of Object.entries(DEV_FALLBACKS)) {
  // TEST_DATABASE_URL permette di puntare a un database separato senza
  // toccare .env, ma il valore predefinito resta quello di sviluppo.
  testEnv[key] = process.env[key === 'DATABASE_URL' ? 'TEST_DATABASE_URL' : key] ?? process.env[key] ?? fallback
}

export default defineConfig({
  test: {
    include: ['test/**/*.test.ts'],
    environment: 'node',
    env: testEnv,
    fileParallelism: false,
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
})
