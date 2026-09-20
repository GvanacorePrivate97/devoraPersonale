import pg from 'pg'
import { env } from '../config/env.js'

/**
 * Un solo pool per processo. I `date` tornano come stringa `YYYY-MM-DD` e i
 * `bigint` come stringa: li convertiamo qui, così il resto del codice vede
 * sempre gli stessi tipi.
 */
const { Pool, types } = pg

types.setTypeParser(types.builtins.DATE, (v) => v)
types.setTypeParser(types.builtins.INT8, (v) => Number(v))
types.setTypeParser(types.builtins.NUMERIC, (v) => Number(v))

export const pool = new Pool({
  connectionString: env.DATABASE_URL,
  max: env.isTest ? 4 : 10,
  idleTimeoutMillis: 30_000,
  application_name: 'mencare-api',
})

export type Db = pg.Pool | pg.PoolClient

export async function query<T extends pg.QueryResultRow = pg.QueryResultRow>(
  db: Db,
  text: string,
  params: unknown[] = [],
): Promise<T[]> {
  const result = await db.query<T>(text, params as never[])
  return result.rows
}

export async function queryOne<T extends pg.QueryResultRow = pg.QueryResultRow>(
  db: Db,
  text: string,
  params: unknown[] = [],
): Promise<T | null> {
  const rows = await query<T>(db, text, params)
  return rows[0] ?? null
}

/**
 * Esegue il blocco dentro una transazione. `isolation` sale a SERIALIZABLE
 * dove la lettura decide la scrittura (la prenotazione).
 */
export async function transaction<T>(
  fn: (db: pg.PoolClient) => Promise<T>,
  isolation: 'READ COMMITTED' | 'SERIALIZABLE' = 'READ COMMITTED',
): Promise<T> {
  const client = await pool.connect()
  try {
    await client.query(`begin isolation level ${isolation}`)
    const result = await fn(client)
    await client.query('commit')
    return result
  } catch (error) {
    await client.query('rollback').catch(() => {})
    throw error
  } finally {
    client.release()
  }
}

/** Codici Postgres che ci interessa riconoscere al volo. */
export const PG = {
  uniqueViolation: '23505',
  exclusionViolation: '23P01',
  checkViolation: '23514',
  foreignKeyViolation: '23503',
  serializationFailure: '40001',
} as const

export function pgCode(error: unknown): string | undefined {
  return typeof error === 'object' && error !== null && 'code' in error
    ? String((error as { code: unknown }).code)
    : undefined
}

export async function closePool() {
  await pool.end()
}
