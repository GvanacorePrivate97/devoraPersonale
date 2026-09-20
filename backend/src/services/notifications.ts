import type { FastifyBaseLogger } from 'fastify'
import type { Db } from '../db/pool.js'
import { query, queryOne } from '../db/pool.js'
import { pushProvider } from './push.js'

/**
 * Notifiche in app. Ogni avviso nasce qui: una riga in `notifications` (che le
 * app leggono nella campanella) e, se il dispositivo è registrato, una push.
 * Le preferenze del cliente e gli interruttori del salone si controllano prima
 * di chiamare, nei punti in cui la regola ha un senso.
 */

export type NotificationKind =
  | 'BOOKING_CONFIRMED'
  | 'BOOKING_REMINDER'
  | 'BOOKING_CANCELLED'
  | 'BOOKING_RESCHEDULED'
  | 'BOOKING_NO_SHOW'
  | 'WAITLIST_SLOT'
  | 'CAMPAIGN'
  | 'GENERIC'

export type NewNotification = {
  userId: string
  kind: NotificationKind
  title: string
  body: string
  payload?: Record<string, unknown>
  /** Se false la riga si crea ma non parte nessuna push. */
  push?: boolean
}

export async function notify(db: Db, input: NewNotification, log?: FastifyBaseLogger): Promise<void> {
  await query(
    db,
    `insert into notifications (user_id, kind, title, body, payload)
     values ($1, $2, $3, $4, $5::jsonb)`,
    [input.userId, input.kind, input.title, input.body, JSON.stringify(input.payload ?? {})],
  )
  if (input.push !== false) {
    await pushProvider.send(
      { userId: input.userId, title: input.title, body: input.body, data: stringifyPayload(input.payload) },
      db,
      log,
    )
  }
}

function stringifyPayload(payload?: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(payload ?? {})) out[key] = String(value)
  return out
}

/** Account collegato a una scheda cliente: i walk-in non ne hanno uno. */
export async function userIdForClient(db: Db, clientId: string): Promise<string | null> {
  const row = await queryOne<{ id: string }>(db, 'select id from users where client_id = $1', [clientId])
  return row?.id ?? null
}

/** Account dell'operatore, per avvisarlo di ciò che tocca la sua agenda. */
export async function userIdForOperator(db: Db, operatorId: string): Promise<string | null> {
  const row = await queryOne<{ id: string }>(db, 'select id from users where operator_id = $1', [operatorId])
  return row?.id ?? null
}

/** Account del titolare. */
export async function ownerUserIds(db: Db): Promise<string[]> {
  const rows = await query<{ id: string }>(db, "select id from users where role = 'OWNER'")
  return rows.map((r) => r.id)
}

export type ClientPrefs = { appointmentReminder: boolean; waitlistAlerts: boolean; marketing: boolean }

export async function clientPrefs(db: Db, userId: string): Promise<ClientPrefs> {
  const row = await queryOne<{ appointment_reminder: boolean; waitlist_alerts: boolean; marketing: boolean }>(
    db, 'select appointment_reminder, waitlist_alerts, marketing from client_notification_prefs where user_id = $1',
    [userId],
  )
  return {
    appointmentReminder: row?.appointment_reminder ?? true,
    waitlistAlerts: row?.waitlist_alerts ?? true,
    marketing: row?.marketing ?? false,
  }
}

export type SalonNotificationSettings = {
  bookingConfirmation: boolean
  cancellationAlert: boolean
  lateOperatorAlert: boolean
  emptyDayPromos: boolean
}

export async function salonNotificationSettings(db: Db): Promise<SalonNotificationSettings> {
  const row = await queryOne<{
    booking_confirmation: boolean; cancellation_alert: boolean
    late_operator_alert: boolean; empty_day_promos: boolean
  }>(db, 'select booking_confirmation, cancellation_alert, late_operator_alert, empty_day_promos from notification_settings where id = 1')
  return {
    bookingConfirmation: row?.booking_confirmation ?? true,
    cancellationAlert: row?.cancellation_alert ?? true,
    lateOperatorAlert: row?.late_operator_alert ?? false,
    emptyDayPromos: row?.empty_day_promos ?? false,
  }
}
