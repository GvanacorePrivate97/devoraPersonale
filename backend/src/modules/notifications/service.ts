import type { Db } from '../../db/pool.js'
import { query, queryOne } from '../../db/pool.js'
import type { NotificationDto } from '../../db/mappers.js'
import { notFound, validation } from '../../lib/errors.js'

/**
 * La campanella. È la stessa pagina per i tre ruoli (§5 di FEATURES): cliente,
 * operatore e titolare leggono la stessa tabella, ognuno solo le proprie righe.
 * L'utente non arriva mai dalla richiesta ma dai claims del token: è l'unico
 * modo per essere certi che nessuno legga la posta di un altro cambiando un
 * parametro nell'URL.
 *
 * Le righe qui non si creano: nascono in `services/notifications.ts` (notify),
 * che è anche il punto da cui parte la push. Questo modulo le legge e basta.
 */

export const DEFAULT_LIMIT = 30
export const MAX_LIMIT = 100

type NotificationRow = {
  id: string
  kind: string
  title: string
  body: string
  payload: Record<string, unknown> | null
  created_at: Date
  read_at: Date | null
}

function toDto(row: NotificationRow): NotificationDto {
  return {
    id: row.id,
    title: row.title,
    body: row.body,
    kind: row.kind,
    payload: row.payload ?? {},
    at: row.created_at.toISOString(),
    // "Letta" non è una colonna a sé: è `read_at` valorizzato. Le app ricevono
    // il booleano già pronto e non devono ragionare sulla data.
    read: row.read_at !== null,
  }
}

/**
 * Paginazione a cursore (keyset), non a offset: la lista cresce dall'alto e con
 * `offset` una notifica arrivata nel frattempo farebbe scivolare la pagina
 * successiva, mostrando due volte la stessa riga. La chiave è la coppia
 * (created_at, id): l'id spezza il pareggio quando due notifiche nascono nello
 * stesso istante, cosa che succede sul serio (una prenotazione ne crea due).
 */
export type Cursor = { at: Date; id: string }

export function encodeCursor(row: NotificationDto): string {
  return Buffer.from(`${row.at}|${row.id}`, 'utf8').toString('base64url')
}

export function decodeCursor(raw: string): Cursor {
  const decoded = Buffer.from(raw, 'base64url').toString('utf8')
  const separator = decoded.lastIndexOf('|')
  const at = separator > 0 ? new Date(decoded.slice(0, separator)) : new Date(Number.NaN)
  const id = separator > 0 ? decoded.slice(separator + 1) : ''
  // Un cursore storpiato è un errore di forma, non un 500: le app lo buttano e
  // ricominciano dalla prima pagina.
  if (Number.isNaN(at.getTime()) || id.length === 0) throw validation('cursor', 'Cursore non valido')
  return { at, id }
}

export type NotificationPage = {
  notifications: NotificationDto[]
  unreadCount: number
  /** `null` quando non c'è altro da caricare. */
  nextCursor: string | null
}

export type ListOptions = { limit?: number | null; cursor?: string | null }

export async function listForUser(db: Db, userId: string, options: ListOptions = {}): Promise<NotificationPage> {
  const limit = Math.min(Math.max(options.limit ?? DEFAULT_LIMIT, 1), MAX_LIMIT)
  const cursor = options.cursor ? decodeCursor(options.cursor) : null

  // Una riga in più del richiesto: serve solo a sapere se esiste una pagina
  // successiva, senza un secondo conteggio.
  const rows = await query<NotificationRow>(
    db,
    `select id, kind, title, body, payload, created_at, read_at
       from notifications
      where user_id = $1
        and ($2::timestamptz is null or (created_at, id) < ($2::timestamptz, $3::uuid))
      order by created_at desc, id desc
      limit $4`,
    [userId, cursor?.at ?? null, cursor?.id ?? null, limit + 1],
  )

  const page = rows.slice(0, limit).map(toDto)
  const last = page[page.length - 1]
  return {
    notifications: page,
    unreadCount: await unreadCount(db, userId),
    nextCursor: rows.length > limit && last ? encodeCursor(last) : null,
  }
}

export async function unreadCount(db: Db, userId: string): Promise<number> {
  const row = await queryOne<{ count: number }>(
    db,
    'select count(*)::int as count from notifications where user_id = $1 and read_at is null',
    [userId],
  )
  return row?.count ?? 0
}

/** Apertura della pagina: il pallino sulla campanella si spegne (§5). */
export async function markAllRead(db: Db, userId: string): Promise<number> {
  const rows = await query<{ id: string }>(
    db,
    'update notifications set read_at = now() where user_id = $1 and read_at is null returning id',
    [userId],
  )
  return rows.length
}

/**
 * Singola riga. Le app oggi segnano tutto letto aprendo la pagina, ma il
 * contatore della campanella ha bisogno di poter scendere di uno quando si apre
 * una notifica sola: costa niente averlo già pronto.
 *
 * Il filtro su `user_id` sta nella UPDATE, non in un controllo a parte: una
 * notifica di un altro account e una inesistente danno lo stesso 404, così la
 * risposta non rivela che quell'id esiste.
 */
export async function markRead(db: Db, userId: string, notificationId: string): Promise<void> {
  const row = await queryOne<{ id: string }>(
    db,
    `update notifications
        set read_at = coalesce(read_at, now())
      where id = $1 and user_id = $2
      returning id`,
    [notificationId, userId],
  )
  if (!row) throw notFound('Notifica')
}
