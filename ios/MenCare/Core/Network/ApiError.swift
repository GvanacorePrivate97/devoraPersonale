import Foundation

/// Corpo d'errore dell'API: `{ "error": { "code", "message", "field"? } }`.
/// I codici sono quelli di `backend/src/lib/errors.ts` e combaciano uno a uno
/// con `AppError`, cosi' il client non deve mai leggere il messaggio per
/// decidere cosa fare.
struct ApiErrorBody: Decodable {
    struct Payload: Decodable {
        let code: String
        let message: String?
        let field: String?
    }

    let error: Payload
}

enum ApiError {

    /// Traduce un errore dell'API in `AppError`. Il messaggio del server viene
    /// conservato solo dentro `.unknown`: gli altri casi hanno gia' un testo
    /// loro nelle schermate, in italiano e nel tono dell'app.
    static func appError(from body: ApiErrorBody, status: Int) -> AppError {
        switch body.error.code {
        case "INVALID_CREDENTIALS": return .invalidCredentials
        case "EMAIL_ALREADY_REGISTERED": return .emailAlreadyRegistered
        case "SLOT_NO_LONGER_AVAILABLE": return .slotNoLongerAvailable
        case "NOT_FOUND": return .notFound
        // `field` accompagna sempre VALIDATION: e' il campo del form da marcare.
        case "VALIDATION": return .validation(field: body.error.field ?? "")
        case "UNAUTHORIZED": return .unauthorized
        case "FORBIDDEN": return .forbidden
        case "CONFLICT": return .conflict(message: body.error.message)
        case "RATE_LIMITED": return .rateLimited
        default: return .unknown(message: body.error.message ?? "HTTP \(status)")
        }
    }

    /// Risposta di errore senza corpo leggibile (proxy, 502, HTML): si ripiega
    /// sullo stato HTTP, che almeno distingue "non autorizzato" da "rotto".
    static func appError(status: Int) -> AppError {
        switch status {
        case 401: return .unauthorized
        case 403: return .forbidden
        case 404: return .notFound
        case 409: return .conflict(message: nil)
        case 429: return .rateLimited
        default: return .unknown(message: "HTTP \(status)")
        }
    }

    /// Errori di trasporto: niente rete, server spento, timeout. Non sono colpa
    /// dell'utente e vanno distinti da un 500.
    static func appError(from error: Error) -> AppError {
        if let appError = error as? AppError { return appError }
        let nsError = error as NSError
        guard nsError.domain == NSURLErrorDomain else {
            return .unknown(message: error.localizedDescription)
        }
        switch nsError.code {
        case NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost,
             NSURLErrorCannotConnectToHost, NSURLErrorCannotFindHost,
             NSURLErrorTimedOut, NSURLErrorDataNotAllowed:
            return .offline
        default:
            return .unknown(message: nsError.localizedDescription)
        }
    }
}
