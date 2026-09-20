/**
 * Migrazioni: file .sql numerati in `migrations/`, applicati una volta sola e
 * ognuno dentro una transazione. `--reset` ributta giù lo schema e riparte
 * (solo fuori produzione).
 */
import { readdir, readFile } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { env } from '../src/config/env.js'
import { pool, closePool } from '../src/db/pool.js'

const migrationsDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'migrations')

async function main() {
  const reset = process.argv.includes('--reset')
  if (reset) {
    if (env.isProduction) throw new Error('--reset non è ammesso in produzione')
    await pool.query('drop schema public cascade; create schema public;')
    console.log('schema azzerato')
  }

  await pool.query(`
    create table if not exists schema_migrations (
      name       text primary key,
      applied_at timestamptz not null default now()
    )
  `)

  const applied = new Set(
    (await pool.query<{ name: string }>('select name from schema_migrations')).rows.map((r) => r.name),
  )
  const files = (await readdir(migrationsDir)).filter((f) => f.endsWith('.sql')).sort()

  let count = 0
  for (const file of files) {
    if (applied.has(file)) continue
    const sql = await readFile(join(migrationsDir, file), 'utf8')
    const client = await pool.connect()
    try {
      await client.query('begin')
      await client.query(sql)
      await client.query('insert into schema_migrations (name) values ($1)', [file])
      await client.query('commit')
      console.log(`applicata ${file}`)
      count++
    } catch (error) {
      await client.query('rollback')
      throw new Error(`migrazione ${file} fallita: ${(error as Error).message}`)
    } finally {
      client.release()
    }
  }
  console.log(count === 0 ? 'nessuna migrazione da applicare' : `${count} migrazioni applicate`)
}

main()
  .then(closePool)
  .catch(async (error) => {
    console.error(error.message)
    await closePool()
    process.exit(1)
  })
