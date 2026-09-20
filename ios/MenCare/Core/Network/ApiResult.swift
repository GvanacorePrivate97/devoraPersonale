import Foundation

/// Ponte fra il client (che lancia) e i contratti dei repository (che per le
/// scritture tornano `AppResult`). Ogni errore passa da `ApiError.appError`,
/// cosi' anche un cavo staccato diventa un `AppError` e non un `NSError` grezzo.
func apiResult<T>(_ work: () async throws -> T) async -> AppResult<T> {
    do {
        return .success(try await work())
    } catch {
        return .failure(ApiError.appError(from: error))
    }
}

/// Stessa normalizzazione per le letture, che invece rilanciano.
func apiThrowing<T>(_ work: () async throws -> T) async throws -> T {
    do {
        return try await work()
    } catch {
        throw ApiError.appError(from: error)
    }
}
