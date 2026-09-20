import type { FastifyBaseLogger } from 'fastify'
import { randomBytes } from 'node:crypto'
import { mkdir, unlink, writeFile } from 'node:fs/promises'
import { basename, join, resolve } from 'node:path'
import { env } from '../../config/env.js'
import type { Db } from '../../db/pool.js'
import { pool, query, queryOne, transaction, pgCode, PG } from '../../db/pool.js'
import type { UserDto } from '../../db/mappers.js'
import {
  accessTokenExpiresInSeconds, hashPassword, newOpaqueToken, passwordResetExpiryDate,
  refreshExpiryDate, sha256, signAccessToken, verifyPassword, type Role,
} from '../../lib/auth.js'
import { ApiError, conflict, emailTaken, forbidden, invalidCredentials, notFound, unauthorized, validation } from '../../lib/errors.js'
import type { ClientPrefs } from '../../services/notifications.js'

/**
 * Account e sessioni. Due regole non si negoziano: la password esiste solo
 * come hash argon2id e il refresh token solo come SHA-256, così nemmeno un
 * dump del database restituisce una credenziale utilizzabile. La seconda è
 * non far trapelare chi è registrato: password sbagliata ed email inesistente
 * danno la stessa risposta, e il reset password risponde sempre allo stesso
 * modo.
 */

export type AuthTokens = {
  accessToken: string
  refreshToken: string
  /** Secondi di vita dell'access token: le app rinnovano prima della scadenza. */
  expiresIn: number
}

export type AuthResult = { user: UserDto; tokens: AuthTokens }

// ------------------------------------------------------------------ utente --

const USER_SELECT = `
  select u.id, u.first_name, u.last_name, u.email::text as email, u.phone, u.role,
         u.member_since, u.avatar_url, u.client_id, u.operator_id, u.password_hash, u.disabled_at,
         -- Le visite sono un conteggio, non un contatore salvato: la vista le
         -- ricava dagli appuntamenti conclusi (0 per staff e titolare).
         coalesce(s.visit_count, 0)::int as visit_count
    from users u
    left join client_stats s on s.client_id = u.client_id`

type UserRow = {
  id: string; first_name: string; last_name: string; email: string; phone: string; role: Role
  member_since: string; avatar_url: string | null; client_id: string | null; operator_id: string | null
  password_hash: string; disabled_at: Date | null; visit_count: number
}

function toDto(row: UserRow): UserDto {
  return {
    id: row.id,
    firstName: row.first_name,
    lastName: row.last_name,
    email: row.email,
    phone: row.phone,
    role: row.role,
    memberSince: row.member_since,
    visitCount: row.visit_count,
    avatarUrl: row.avatar_url,
    clientId: row.client_id,
    operatorId: row.operator_id,
  }
}

async function userRowById(db: Db, id: string): Promise<UserRow | null> {
  return queryOne<UserRow>(db, `${USER_SELECT} where u.id = $1`, [id])
}

async function userRowByEmail(db: Db, email: string): Promise<UserRow | null> {
  return queryOne<UserRow>(db, `${USER_SELECT} where u.email = $1::citext`, [email])
}

export async function currentUser(userId: string): Promise<UserDto> {
  const row = await userRowById(pool, userId)
  if (!row) throw notFound('Utente')
  return toDto(row)
}

// ------------------------------------------------------------------ sessioni --

/**
 * Nuova coppia di token. Il refresh nasce già come riga in tabella: è lì che
 * vive la sessione, ed è lì che la si revoca.
 */
async function issueTokens(db: Db, row: UserRow, userAgent?: string | null): Promise<AuthTokens> {
  const accessToken = await signAccessToken({
    sub: row.id,
    role: row.role,
    clientId: row.client_id ?? undefined,
    operatorId: row.operator_id ?? undefined,
  })
  const refresh = newOpaqueToken()
  await query(
    db,
    `insert into refresh_tokens (user_id, token_hash, expires_at, user_agent) values ($1, $2, $3, $4)`,
    [row.id, refresh.hash, refreshExpiryDate(), userAgent?.slice(0, 200) ?? null],
  )
  return { accessToken, refreshToken: refresh.token, expiresIn: accessTokenExpiresInSeconds() }
}

export type RegisterInput = {
  firstName: string
  lastName: string
  phone: string
  email: string
  password: string
}

/**
 * Registrazione: scheda CRM e account nascono insieme, dentro una sola
 * transazione — un account senza scheda non potrebbe prenotare nulla.
 * Se il salone aveva già aperto una scheda per quel numero (prenotazione
 * telefonica, cliente entrato e basta) la si riusa invece di crearne una
 * seconda: così lo storico delle visite non si spezza al primo accesso.
 */
export async function register(input: RegisterInput, userAgent?: string | null): Promise<AuthResult> {
  const passwordHash = await hashPassword(input.password)

  const row = await transaction(async (db) => {
    const existingUser = await userRowByEmail(db, input.email)
    if (existingUser) throw emailTaken()

    const card = await queryOne<{ id: string; user_id: string | null }>(
      db,
      `select c.id, u.id as user_id
         from clients c
         left join users u on u.client_id = c.id
        where c.phone = $1 or (c.email is not null and c.email = $2::citext)
        limit 1`,
      [input.phone, input.email],
    )
    // Scheda già collegata a un altro account: il numero è di qualcun altro.
    if (card?.user_id) throw conflict('Questo numero è già collegato a un account')

    let clientId: string
    try {
      if (card) {
        clientId = card.id
        await query(
          db,
          'update clients set first_name = $2, last_name = $3, phone = $4, email = $5 where id = $1',
          [clientId, input.firstName, input.lastName, input.phone, input.email],
        )
      } else {
        const inserted = await queryOne<{ id: string }>(
          db,
          `insert into clients (first_name, last_name, phone, email) values ($1, $2, $3, $4) returning id`,
          [input.firstName, input.lastName, input.phone, input.email],
        )
        clientId = inserted!.id
      }

      const user = await queryOne<{ id: string }>(
        db,
        `insert into users (first_name, last_name, email, phone, role, password_hash, client_id)
         values ($1, $2, $3, $4, 'CLIENT', $5, $6)
         returning id`,
        [input.firstName, input.lastName, input.email, input.phone, passwordHash, clientId],
      )
      // Preferenze di default: promemoria e lista d'attesa accesi, marketing spento.
      await query(db, 'insert into client_notification_prefs (user_id) values ($1)', [user!.id])
      return (await userRowById(db, user!.id))!
    } catch (error) {
      // Due registrazioni contemporanee con la stessa email: l'indice unico è
      // l'ultima parola, e la risposta resta quella che l'app si aspetta.
      throw asRegistrationError(error)
    }
  })

  const tokens = await issueTokens(pool, row, userAgent)
  return { user: toDto(row), tokens }
}

function asRegistrationError(error: unknown): unknown {
  if (pgCode(error) !== PG.uniqueViolation) return error
  const constraint = String((error as { constraint?: unknown }).constraint ?? '')
  if (constraint.includes('phone')) return conflict('Questo numero è già collegato a un account')
  return emailTaken()
}

export async function login(email: string, password: string, userAgent?: string | null): Promise<AuthResult> {
  const row = await userRowByEmail(pool, email)
  // Email inesistente: si verifica comunque una password finta, così la
  // risposta non diventa più veloce e non si può indovinare chi è iscritto.
  const ok = row
    ? await verifyPassword(row.password_hash, password)
    : await verifyPassword(await dummyHash(), password)
  if (!row || !ok || row.disabled_at) throw invalidCredentials()

  const tokens = await issueTokens(pool, row, userAgent)
  return { user: toDto(row), tokens }
}

/**
 * Hash di una password casuale, calcolato una volta sola all'avvio. Serve per
 * far costare uguale il confronto anche quando l'email non esiste: senza,
 * la risposta immediata direbbe "questo indirizzo non e' registrato".
 */
let dummyHashPromise: Promise<string> | null = null
function dummyHash(): Promise<string> {
  dummyHashPromise ??= hashPassword(randomBytes(24).toString('hex'))
  return dummyHashPromise
}

export type SocialProvider = 'GOOGLE' | 'APPLE'

/**
 * Innesto della verifica social: qui andrà il controllo vero della firma del
 * token (Google: chiavi di `https://www.googleapis.com/oauth2/v3/certs`,
 * `aud` uguale al client id dell'app; Apple: chiavi di
 * `https://appleid.apple.com/auth/keys`, `iss` `https://appleid.apple.com`).
 * Finché quel controllo non c'è, in produzione si rifiuta: un token non
 * verificato è chiunque dica di essere chiunque. In sviluppo si legge la sola
 * rivendicazione `email` per poter provare il pulsante con l'account demo.
 */
async function verifySocialToken(provider: SocialProvider, idToken: string): Promise<{ email: string }> {
  if (env.isProduction) {
    throw forbidden(`L'accesso con ${provider === 'APPLE' ? 'Apple' : 'Google'} non è ancora attivo`)
  }
  const email = emailClaimOf(idToken)
  if (!email) throw validation('idToken', 'Token social non leggibile')
  return { email }
}

/** Legge `email` dal corpo del JWT senza verificarne la firma: solo in sviluppo. */
function emailClaimOf(idToken: string): string | null {
  const value = idToken.trim()
  // In sviluppo si accetta anche la sola email, per provare dal simulatore.
  if (value.includes('@') && !value.includes(' ')) return value.toLowerCase()
  const parts = value.split('.')
  if (parts.length !== 3 || !/^[A-Za-z0-9_-]+$/.test(parts[1]!)) return null
  try {
    const payload = JSON.parse(Buffer.from(parts[1]!, 'base64url').toString('utf8')) as { email?: unknown }
    return typeof payload.email === 'string' ? payload.email.trim().toLowerCase() : null
  } catch {
    return null
  }
}

/**
 * Accesso con Google o Apple. Non crea account: l'identità social si collega
 * solo a un'email già registrata, perché senza numero di telefono verificato
 * la scheda CRM resterebbe a metà.
 */
export async function socialLogin(
  provider: SocialProvider,
  idToken: string,
  userAgent?: string | null,
): Promise<AuthResult> {
  const claims = await verifySocialToken(provider, idToken)
  const row = await userRowByEmail(pool, claims.email)
  if (!row || row.disabled_at) throw invalidCredentials()

  const tokens = await issueTokens(pool, row, userAgent)
  return { user: toDto(row), tokens }
}

type RefreshRow = { id: string; user_id: string; expires_at: Date; revoked_at: Date | null }

/** O la coppia nuova, o l'utente le cui sessioni vanno chiuse per riuso del token. */
type RefreshOutcome = { reusedBy: string } | { row: UserRow; tokens: AuthTokens }

/**
 * Rotazione del refresh token: quello presentato muore qui e ne nasce uno
 * nuovo, agganciato al precedente. Se arriva un token già revocato vuol dire
 * che qualcuno l'ha copiato e lo sta riusando: si buttano giù tutte le
 * sessioni di quell'utente, non solo quella.
 */
export async function refresh(presentedToken: string, userAgent?: string | null): Promise<AuthResult> {
  const hash = sha256(presentedToken)

  const outcome = await transaction<RefreshOutcome>(async (db) => {
    const current = await queryOne<RefreshRow>(
      db,
      'select id, user_id, expires_at, revoked_at from refresh_tokens where token_hash = $1 for update',
      [hash],
    )
    if (!current) throw unauthorized('Sessione non valida')
    // Il taglio delle sessioni va fatto fuori da qui: se lo facessimo dentro,
    // l'eccezione annullerebbe la transazione e con essa la revoca.
    if (current.revoked_at) return { reusedBy: current.user_id }
    if (current.expires_at.getTime() <= Date.now()) throw unauthorized('Sessione scaduta')

    const user = await userRowById(db, current.user_id)
    if (!user || user.disabled_at) throw unauthorized('Sessione non valida')

    const accessToken = await signAccessToken({
      sub: user.id,
      role: user.role,
      clientId: user.client_id ?? undefined,
      operatorId: user.operator_id ?? undefined,
    })
    const next = newOpaqueToken()
    const inserted = await queryOne<{ id: string }>(
      db,
      `insert into refresh_tokens (user_id, token_hash, expires_at, user_agent) values ($1, $2, $3, $4) returning id`,
      [user.id, next.hash, refreshExpiryDate(), userAgent?.slice(0, 200) ?? null],
    )
    await query(db, 'update refresh_tokens set revoked_at = now(), replaced_by = $2 where id = $1', [
      current.id, inserted!.id,
    ])
    return {
      row: user,
      tokens: { accessToken, refreshToken: next.token, expiresIn: accessTokenExpiresInSeconds() },
    }
  })

  if ('reusedBy' in outcome) {
    await revokeAllSessions(pool, outcome.reusedBy)
    throw unauthorized('Sessione non valida')
  }
  return { user: toDto(outcome.row), tokens: outcome.tokens }
}

/** Chiude la sessione del token presentato. Silenziosa: non dice se esisteva. */
export async function logout(presentedToken: string): Promise<void> {
  await query(pool, 'update refresh_tokens set revoked_at = now() where token_hash = $1 and revoked_at is null', [
    sha256(presentedToken),
  ])
}

async function revokeAllSessions(db: Db, userId: string): Promise<void> {
  await query(db, 'update refresh_tokens set revoked_at = now() where user_id = $1 and revoked_at is null', [userId])
}

// -------------------------------------------------------------------- profilo --

export type ProfileInput = {
  firstName: string
  lastName: string
  email: string
  phone: string
}

/**
 * Dati anagrafici dell'account. Per un cliente la scheda CRM è la stessa
 * persona: si aggiornano insieme, altrimenti il salone vedrebbe il vecchio
 * numero sulla prenotazione di domani.
 */
export async function updateProfile(userId: string, input: ProfileInput): Promise<UserDto> {
  return transaction(async (db) => {
    const current = await userRowById(db, userId)
    if (!current) throw notFound('Utente')

    const taken = await queryOne<{ id: string }>(
      db, 'select id from users where email = $1::citext and id <> $2', [input.email, userId],
    )
    if (taken) throw emailTaken()

    try {
      await query(
        db,
        'update users set first_name = $2, last_name = $3, email = $4, phone = $5 where id = $1',
        [userId, input.firstName, input.lastName, input.email, input.phone],
      )
      if (current.client_id) {
        await query(
          db,
          'update clients set first_name = $2, last_name = $3, email = $4, phone = $5 where id = $1',
          [current.client_id, input.firstName, input.lastName, input.email, input.phone],
        )
      }
    } catch (error) {
      throw asRegistrationError(error)
    }
    return toDto((await userRowById(db, userId))!)
  })
}

/**
 * Cambio password. Si chiude ogni altra sessione — una password si cambia
 * anche perché la vecchia è finita in giro — e si consegna subito una coppia
 * nuova, così chi sta usando l'app non viene buttato fuori.
 */
export async function changePassword(
  userId: string,
  currentPassword: string,
  newPassword: string,
  userAgent?: string | null,
): Promise<AuthTokens> {
  const row = await userRowById(pool, userId)
  if (!row) throw notFound('Utente')
  if (!(await verifyPassword(row.password_hash, currentPassword))) {
    throw new ApiError('INVALID_CREDENTIALS', 'La password attuale non è corretta', 'currentPassword')
  }
  const hash = await hashPassword(newPassword)

  return transaction(async (db) => {
    await query(db, 'update users set password_hash = $2 where id = $1', [userId, hash])
    await revokeAllSessions(db, userId)
    return issueTokens(db, row, userAgent)
  })
}

// -------------------------------------------------------- reset password ----

/**
 * Richiesta di reset. Risponde sempre allo stesso modo, email registrata o no:
 * è l'unico modo per non trasformare questo endpoint in un elenco di clienti.
 * Finché non c'è un servizio di invio, in sviluppo il link finisce nel log.
 */
export async function requestPasswordReset(email: string, log?: FastifyBaseLogger): Promise<void> {
  const row = await userRowByEmail(pool, email)
  if (!row || row.disabled_at) return

  const reset = newOpaqueToken()
  await query(
    pool,
    'insert into password_resets (user_id, token_hash, expires_at) values ($1, $2, $3)',
    [row.id, reset.hash, passwordResetExpiryDate()],
  )
  if (!env.isProduction) {
    // Nessun dato personale: solo il link, e solo fuori dalla produzione.
    log?.info({ resetUrl: `${env.PUBLIC_BASE_URL}/reset-password?token=${reset.token}` }, 'link di reset password')
  }
}

/** Il token vale una volta sola e per 30 minuti; usarlo chiude ogni sessione. */
export async function resetPassword(token: string, newPassword: string): Promise<void> {
  const hash = await hashPassword(newPassword)
  await transaction(async (db) => {
    const row = await queryOne<{ id: string; user_id: string; expires_at: Date; used_at: Date | null }>(
      db,
      'select id, user_id, expires_at, used_at from password_resets where token_hash = $1 for update',
      [sha256(token)],
    )
    if (!row || row.used_at || row.expires_at.getTime() <= Date.now()) {
      throw new ApiError('UNAUTHORIZED', 'Link di reset non valido o scaduto', 'token')
    }
    await query(db, 'update password_resets set used_at = now() where id = $1', [row.id])
    await query(db, 'update users set password_hash = $2 where id = $1', [row.user_id, hash])
    await revokeAllSessions(db, row.user_id)
  })
}

// ------------------------------------------------------- preferenze e device --

export async function notificationPrefs(userId: string): Promise<ClientPrefs> {
  const row = await queryOne<{ appointment_reminder: boolean; waitlist_alerts: boolean; marketing: boolean }>(
    pool,
    'select appointment_reminder, waitlist_alerts, marketing from client_notification_prefs where user_id = $1',
    [userId],
  )
  // Gli stessi valori di default di `services/notifications.ts`: chi non ha mai
  // toccato gli interruttori riceve promemoria e avvisi, ma niente marketing.
  return {
    appointmentReminder: row?.appointment_reminder ?? true,
    waitlistAlerts: row?.waitlist_alerts ?? true,
    marketing: row?.marketing ?? false,
  }
}

export async function saveNotificationPrefs(userId: string, prefs: ClientPrefs): Promise<ClientPrefs> {
  await query(
    pool,
    `insert into client_notification_prefs (user_id, appointment_reminder, waitlist_alerts, marketing)
     values ($1, $2, $3, $4)
     on conflict (user_id) do update
        set appointment_reminder = excluded.appointment_reminder,
            waitlist_alerts      = excluded.waitlist_alerts,
            marketing            = excluded.marketing,
            updated_at           = now()`,
    [userId, prefs.appointmentReminder, prefs.waitlistAlerts, prefs.marketing],
  )
  // Il consenso commerciale vive anche sulla scheda CRM: è da lì che le
  // campagne pescano i destinatari.
  await query(
    pool,
    'update clients set marketing_opt_in = $2 where id = (select client_id from users where id = $1)',
    [userId, prefs.marketing],
  )
  return prefs
}

export type DevicePlatform = 'ANDROID' | 'IOS'

/**
 * Token FCM del dispositivo. Lo stesso token può passare da un account
 * all'altro sullo stesso telefono: in quel caso cambia proprietario, così le
 * push non finiscono a chi si è disconnesso.
 */
export async function registerDevice(userId: string, token: string, platform: DevicePlatform): Promise<void> {
  await query(
    pool,
    `insert into device_tokens (user_id, token, platform) values ($1, $2, $3)
     on conflict (token) do update
        set user_id      = excluded.user_id,
            platform     = excluded.platform,
            last_seen_at = now()`,
    [userId, token, platform],
  )
}

export async function forgetDevice(userId: string, token: string): Promise<void> {
  await query(pool, 'delete from device_tokens where token = $1 and user_id = $2', [token, userId])
}

// ------------------------------------------------------------------ avatar --

const AVATAR_EXTENSIONS: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
}

export const AVATAR_MIME_TYPES = Object.keys(AVATAR_EXTENSIONS)
export const AVATAR_MAX_BYTES = 5 * 1024 * 1024

const UPLOADS_PREFIX = `${env.PUBLIC_BASE_URL.replace(/\/+$/, '')}/uploads/`

/**
 * Salva l'immagine di profilo su disco e tiene solo l'indirizzo pubblico nel
 * database. Il file precedente si cancella subito: le foto vecchie resterebbero
 * raggiungibili da chi ne conosce il nome.
 */
export async function saveAvatar(
  userId: string,
  file: { buffer: Buffer; mimetype: string },
  log?: FastifyBaseLogger,
): Promise<string> {
  const extension = AVATAR_EXTENSIONS[file.mimetype]
  if (!extension) throw validation('file', 'Sono ammesse solo immagini JPEG, PNG o WebP')
  if (file.buffer.length === 0) throw validation('file', 'File vuoto')
  if (file.buffer.length > AVATAR_MAX_BYTES) throw validation('file', 'Immagine troppo grande (massimo 5 MB)')

  const row = await userRowById(pool, userId)
  if (!row) throw notFound('Utente')

  const directory = resolve(env.UPLOADS_DIR)
  await mkdir(directory, { recursive: true })
  const name = `${userId}-${Date.now()}.${extension}`
  await writeFile(join(directory, name), file.buffer)

  const previous = row.avatar_url
  await query(pool, 'update users set avatar_url = $2 where id = $1', [userId, `${UPLOADS_PREFIX}${name}`])
  await removeStoredAvatar(previous, log)

  return `${UPLOADS_PREFIX}${name}`
}

export async function clearAvatar(userId: string, log?: FastifyBaseLogger): Promise<void> {
  const row = await userRowById(pool, userId)
  if (!row) throw notFound('Utente')
  await query(pool, 'update users set avatar_url = null where id = $1', [userId])
  await removeStoredAvatar(row.avatar_url, log)
}

/** Cancella solo i file che abbiamo scritto noi: un indirizzo esterno si ignora. */
async function removeStoredAvatar(avatarUrl: string | null, log?: FastifyBaseLogger): Promise<void> {
  if (!avatarUrl?.startsWith(UPLOADS_PREFIX)) return
  const name = basename(avatarUrl.slice(UPLOADS_PREFIX.length))
  if (!/^[A-Za-z0-9._-]+$/.test(name)) return
  try {
    await unlink(join(resolve(env.UPLOADS_DIR), name))
  } catch (error) {
    // Il file poteva già non esserci: non è un motivo per far fallire la richiesta.
    if ((error as { code?: string }).code !== 'ENOENT') log?.warn({ err: error }, 'avatar precedente non rimosso')
  }
}
