import Foundation

enum AppError: Error, Hashable {
    case invalidCredentials
    case emailAlreadyRegistered
    case slotNoLongerAvailable
    case notFound
    case validation(field: String)
    /// Sessione scaduta o assente: chi la riceve viene riportato all'accesso.
    case unauthorized
    /// Autenticato ma senza diritto su quella risorsa (lo decide il server).
    case forbidden
    case conflict(message: String? = nil)
    case rateLimited
    /// Il telefono non ha raggiunto il server: e' un problema di rete, non un
    /// errore dell'API, e va detto in modo diverso.
    case offline
    case unknown(message: String? = nil)

    /// Testo da mostrare accanto a un riprova. I casi legati a un campo del
    /// form li scrive la schermata, che sa a cosa si riferiscono.
    var displayMessage: String {
        switch self {
        case .invalidCredentials: L("auth_error_credentials")
        case .emailAlreadyRegistered: L("auth_error_email_taken")
        case .slotNoLongerAvailable: L("error_slot_taken")
        case .notFound: L("error_not_found")
        case .validation: L("validation_form_invalid")
        case .unauthorized: L("error_session_expired")
        case .forbidden: L("error_forbidden")
        case .conflict(let message): message ?? L("error_generic")
        case .rateLimited: L("error_rate_limited")
        case .offline: L("error_offline")
        case .unknown: L("error_generic")
        }
    }
}

enum AppResult<T> {
    case success(T)
    case failure(AppError)

    func map<R>(_ transform: (T) -> R) -> AppResult<R> {
        switch self {
        case .success(let data): return .success(transform(data))
        case .failure(let error): return .failure(error)
        }
    }

    @discardableResult
    func onSuccess(_ action: (T) -> Void) -> AppResult<T> {
        if case .success(let data) = self { action(data) }
        return self
    }

    @discardableResult
    func onFailure(_ action: (AppError) -> Void) -> AppResult<T> {
        if case .failure(let error) = self { action(error) }
        return self
    }

    var value: T? {
        if case .success(let data) = self { return data }
        return nil
    }

    var error: AppError? {
        if case .failure(let error) = self { return error }
        return nil
    }
}
