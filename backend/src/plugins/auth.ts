import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify'
import fp from 'fastify-plugin'
import { verifyAccessToken, type AccessClaims, type Role } from '../lib/auth.js'
import { forbidden, unauthorized } from '../lib/errors.js'

declare module 'fastify' {
  interface FastifyRequest {
    /** Presente su ogni rotta protetta; il controllo lo fa `requireRole`. */
    auth?: AccessClaims
  }
  interface FastifyInstance {
    /** Pre-handler: esige un accesso valido e, se elencati, uno di quei ruoli. */
    requireRole: (...roles: Role[]) => (req: FastifyRequest, reply: FastifyReply) => Promise<void>
  }
}

/**
 * Controllo degli accessi. Ogni rotta protetta dichiara i ruoli ammessi: il
 * server non si fida mai della schermata da cui arriva la richiesta.
 */
export const authPlugin = fp(async (app: FastifyInstance) => {
  app.decorate('requireRole', (...roles: Role[]) => async (req: FastifyRequest, _reply: FastifyReply) => {
    const header = req.headers.authorization
    if (!header?.startsWith('Bearer ')) throw unauthorized('Accesso richiesto')
    const claims = await verifyAccessToken(header.slice('Bearer '.length))
    req.auth = claims
    if (roles.length > 0 && !roles.includes(claims.role)) {
      throw forbidden('Il tuo ruolo non può eseguire questa operazione')
    }
  })
})

/** Claims di una richiesta protetta: usarlo solo dopo `requireRole`. */
export function auth(req: FastifyRequest): AccessClaims {
  if (!req.auth) throw unauthorized('Accesso richiesto')
  return req.auth
}

/** L'operatore collegato all'account, per le rotte che vivono sulla propria agenda. */
export function ownOperatorId(req: FastifyRequest): string {
  const claims = auth(req)
  if (!claims.operatorId) throw forbidden('Account senza profilo operatore')
  return claims.operatorId
}

/** La scheda cliente collegata all'account. */
export function ownClientId(req: FastifyRequest): string {
  const claims = auth(req)
  if (!claims.clientId) throw forbidden('Account senza scheda cliente')
  return claims.clientId
}
