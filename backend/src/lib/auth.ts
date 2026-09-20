import { createHash, randomBytes } from 'node:crypto'
import argon2 from 'argon2'
import { SignJWT, jwtVerify } from 'jose'
import { env } from '../config/env.js'
import { unauthorized } from './errors.js'

/**
 * Password e token. Le password si salvano solo come hash argon2id; i refresh
 * token nemmeno: nel database va il loro SHA-256, così un dump rubato non
 * permette di entrare.
 */

const ARGON_OPTIONS = {
  type: argon2.argon2id,
  memoryCost: 19_456,
  timeCost: 2,
  parallelism: 1,
} as const

export function hashPassword(password: string): Promise<string> {
  return argon2.hash(password, ARGON_OPTIONS)
}

export async function verifyPassword(hash: string, password: string): Promise<boolean> {
  try {
    return await argon2.verify(hash, password)
  } catch {
    return false
  }
}

export type Role = 'CLIENT' | 'STAFF' | 'OWNER'

export type AccessClaims = {
  sub: string
  role: Role
  /** Scheda CRM collegata, solo per i clienti. */
  clientId?: string
  /** Profilo operatore collegato, per staff e titolare. */
  operatorId?: string
}

const accessKey = new TextEncoder().encode(env.JWT_ACCESS_SECRET)
const ISSUER = 'mencare-api'
const AUDIENCE = 'mencare-app'

export async function signAccessToken(claims: AccessClaims): Promise<string> {
  return new SignJWT({ role: claims.role, clientId: claims.clientId, operatorId: claims.operatorId })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(claims.sub)
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setIssuedAt()
    .setExpirationTime(`${env.ACCESS_TOKEN_TTL_MINUTES}m`)
    .sign(accessKey)
}

export async function verifyAccessToken(token: string): Promise<AccessClaims> {
  try {
    const { payload } = await jwtVerify(token, accessKey, { issuer: ISSUER, audience: AUDIENCE })
    return {
      sub: String(payload.sub),
      role: payload.role as Role,
      clientId: payload.clientId as string | undefined,
      operatorId: payload.operatorId as string | undefined,
    }
  } catch {
    throw unauthorized('Token non valido o scaduto')
  }
}

/** Il refresh token è un segreto casuale: nel database ne resta solo l'hash. */
export function newOpaqueToken(): { token: string; hash: string } {
  const token = randomBytes(48).toString('base64url')
  return { token, hash: sha256(token) }
}

export function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex')
}

export function accessTokenExpiresInSeconds(): number {
  return env.ACCESS_TOKEN_TTL_MINUTES * 60
}

export function refreshExpiryDate(from: Date = new Date()): Date {
  return new Date(from.getTime() + env.REFRESH_TOKEN_TTL_DAYS * 86_400_000)
}

export function passwordResetExpiryDate(from: Date = new Date()): Date {
  return new Date(from.getTime() + env.PASSWORD_RESET_TTL_MINUTES * 60_000)
}
