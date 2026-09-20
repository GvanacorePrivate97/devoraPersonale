import SwiftUI

/// Stato di una schermata che legge dalla rete. Prima le letture erano
/// sincrone e non potevano fallire: adesso ogni schermata ha tre momenti —
/// sto caricando, e' andata male, ecco i dati — e vanno mostrati tutti e tre.
@MainActor
@Observable
final class LoadState {
    var isLoading = false
    var error: AppError?

    /// Vero solo al primo caricamento: un aggiornamento successivo non deve
    /// far sparire quello che c'e' gia' a schermo.
    var hasLoadedOnce = false

    var failed: Bool { error != nil }

    /// Avvolge una lettura: accende il caricamento, cattura l'errore, e lascia
    /// i dati precedenti al loro posto se va male.
    @discardableResult
    func run<T>(_ work: () async throws -> T) async -> T? {
        isLoading = true
        error = nil
        defer {
            isLoading = false
            hasLoadedOnce = true
        }
        do {
            return try await work()
        } catch {
            self.error = ApiError.appError(from: error)
            return nil
        }
    }
}

/// Rotella di caricamento nel tono dell'app.
struct BrandLoading: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .tint(Color.oliveWood)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 32)
    }
}

/// Messaggio di errore con il riprova. Il testo arriva da `AppError`, cosi' un
/// telefono senza rete dice "senza connessione" e non "qualcosa e' andato storto".
struct BrandErrorRetry: View {
    let error: AppError
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 26))
                .foregroundStyle(Color.oliveWood)
            Text(error.displayMessage)
                .font(Typo.bodyMedium)
                .foregroundStyle(Color.ink)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: retry) {
                Text(L("action_retry"))
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.bone)
                    .padding(.horizontal, 22)
                    .frame(height: 44)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 28)
        .padding(.vertical, 36)
    }
}

/// Contenuto di una schermata piu' i suoi due stati di servizio. Il contenuto
/// resta visibile durante gli aggiornamenti: solo il primo caricamento lo
/// sostituisce con la rotella.
struct Loadable<Content: View>: View {
    let state: LoadState
    let retry: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        if state.isLoading, !state.hasLoadedOnce {
            BrandLoading()
        } else if let error = state.error, !state.hasLoadedOnce {
            BrandErrorRetry(error: error, retry: retry)
        } else {
            VStack(spacing: 0) {
                // Un aggiornamento fallito su dati gia' a schermo si dice con una
                // riga sottile: buttare via la schermata sarebbe peggio del guasto.
                if let error = state.error {
                    InlineErrorBar(message: error.displayMessage, retry: retry)
                }
                content
            }
        }
    }
}

/// Riga d'errore sopra dati gia' visibili.
struct InlineErrorBar: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 13))
                .foregroundStyle(Color.errorRed)
            Text(message)
                .font(Typo.bodySmall)
                .foregroundStyle(Color.errorRed)
                .lineLimit(2)
            Spacer(minLength: 8)
            Button(action: retry) {
                Text(L("action_retry"))
                    .font(Typo.jost(12, weight: .medium))
                    .foregroundStyle(Color.oliveWood)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.errorRed.opacity(0.08))
    }
}
