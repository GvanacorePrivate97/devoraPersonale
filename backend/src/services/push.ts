import type { FastifyBaseLogger } from 'fastify'
import { env } from '../config/env.js'
import type { Db } from '../db/pool.js'
import { query } from '../db/pool.js'

/**
 * Invio push. Oggi gira il provider "log": scrive nel log e la notifica resta
 * comunque in app (la riga in `notifications` la crea il chiamante). Passare a
 * Firebase significa riempire `FcmPushProvider` e mettere PUSH_PROVIDER=fcm:
 * nient'altro nel progetto cambia.
 */

export type PushMessage = {
  userId: string
  title: string
  body: string
  data?: Record<string, string>
}

export interface PushProvider {
  send(message: PushMessage, db: Db, log?: FastifyBaseLogger): Promise<void>
}

class LogPushProvider implements PushProvider {
  async send(message: PushMessage, db: Db, log?: FastifyBaseLogger) {
    const tokens = await query<{ token: string; platform: string }>(
      db, 'select token, platform from device_tokens where user_id = $1', [message.userId],
    )
    log?.info(
      { userId: message.userId, devices: tokens.length, title: message.title },
      'push simulata (PUSH_PROVIDER=log)',
    )
  }
}

class FcmPushProvider implements PushProvider {
  async send(message: PushMessage, db: Db, log?: FastifyBaseLogger) {
    const tokens = await query<{ token: string }>(
      db, 'select token from device_tokens where user_id = $1', [message.userId],
    )
    if (tokens.length === 0) return
    // Da completare quando ci sono le credenziali Firebase: qui va la chiamata
    // a FCM HTTP v1 con il service account di FCM_SERVICE_ACCOUNT_JSON, più la
    // rimozione dei token che tornano UNREGISTERED.
    log?.warn({ userId: message.userId }, 'PUSH_PROVIDER=fcm ma l\'invio non è ancora collegato')
  }
}

export const pushProvider: PushProvider =
  env.PUSH_PROVIDER === 'fcm' ? new FcmPushProvider() : new LogPushProvider()
