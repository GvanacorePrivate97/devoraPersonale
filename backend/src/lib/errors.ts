/**
 * Tassonomia degli errori. Combacia uno a uno con `AppError` delle due app
 * (Android `core/common/AppResult.kt`, iOS `Core/Common/AppResult.swift`):
 * il client mappa `code` senza dover leggere il messaggio.
 */
export type ErrorCode =
  | 'INVALID_CREDENTIALS'
  | 'EMAIL_ALREADY_REGISTERED'
  | 'SLOT_NO_LONGER_AVAILABLE'
  | 'NOT_FOUND'
  | 'VALIDATION'
  | 'FORBIDDEN'
  | 'UNAUTHORIZED'
  | 'CONFLICT'
  | 'RATE_LIMITED'
  | 'UNKNOWN'

const STATUS: Record<ErrorCode, number> = {
  INVALID_CREDENTIALS: 401,
  EMAIL_ALREADY_REGISTERED: 409,
  SLOT_NO_LONGER_AVAILABLE: 409,
  NOT_FOUND: 404,
  VALIDATION: 422,
  FORBIDDEN: 403,
  UNAUTHORIZED: 401,
  CONFLICT: 409,
  RATE_LIMITED: 429,
  UNKNOWN: 500,
}

export class ApiError extends Error {
  readonly code: ErrorCode
  readonly status: number
  /** Campo del form a cui appartiene l'errore, per `VALIDATION`. */
  readonly field?: string

  constructor(code: ErrorCode, message?: string, field?: string) {
    super(message ?? code)
    this.name = 'ApiError'
    this.code = code
    this.status = STATUS[code]
    this.field = field
  }

  toBody() {
    return { error: { code: this.code, message: this.message, ...(this.field ? { field: this.field } : {}) } }
  }
}

export const invalidCredentials = () => new ApiError('INVALID_CREDENTIALS', 'Email o password non corretti')
export const emailTaken = () => new ApiError('EMAIL_ALREADY_REGISTERED', 'Questa email è già registrata')
export const slotTaken = () =>
  new ApiError('SLOT_NO_LONGER_AVAILABLE', 'Questo orario è appena stato preso')
export const notFound = (what = 'Risorsa') => new ApiError('NOT_FOUND', `${what} non trovata`)
export const validation = (field: string, message: string) => new ApiError('VALIDATION', message, field)
export const forbidden = (message = 'Operazione non consentita') => new ApiError('FORBIDDEN', message)
export const unauthorized = (message = 'Sessione scaduta') => new ApiError('UNAUTHORIZED', message)
export const conflict = (message: string) => new ApiError('CONFLICT', message)
