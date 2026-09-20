import Foundation
import Observation

@MainActor
@Observable
final class LoginViewModel {

    private let auth: AuthRepository

    var email = ""
    var password = ""
    var loading = false
    /// Messaggio d'errore gia' pronto da mostrare: con il server vero un
    /// accesso puo' fallire per credenziali sbagliate, ma anche per mancanza di
    /// rete o per troppi tentativi, e le tre cose non si dicono allo stesso modo.
    var errorMessage: String?
    /// Errori di formato, con lo stesso messaggio della registrazione: prima il
    /// login accettava qualsiasi cosa e faceva fare un giro al server per nulla.
    var emailError: String?
    var passwordError: String?

    @ObservationIgnored private var submitted = false

    init(auth: AuthRepository) {
        self.auth = auth
    }

    func clearError() {
        errorMessage = nil
        if submitted { validate() }
    }

    /// La password si controlla solo come "obbligatoria": le regole di
    /// composizione valgono per chi la sceglie, non per chi ha già un account
    /// creato quando la regola era un'altra.
    @discardableResult
    private func validate() -> Bool {
        emailError = validateEmail(email).message
        passwordError = password.isEmpty ? L("validation_required") : nil
        return emailError == nil && passwordError == nil
    }

    func login(onLoggedIn: @escaping (UserRole) -> Void) {
        guard !loading else { return }
        submitted = true
        guard validate() else { return }
        loading = true
        errorMessage = nil
        Task {
            let result = await auth.login(email: normalizeEmail(email), password: password)
            loading = false
            switch result {
            case .success(let user):
                onLoggedIn(user.role)
            case .failure(let error):
                errorMessage = error.displayMessage
            }
        }
    }

    func loginWithProvider(_ provider: SocialProvider, onLoggedIn: @escaping (UserRole) -> Void) {
        guard !loading else { return }
        loading = true
        errorMessage = nil
        Task {
            let result = await auth.loginWithProvider(provider)
            loading = false
            switch result {
            case .success(let user):
                onLoggedIn(user.role)
            case .failure(let error):
                errorMessage = error.displayMessage
            }
        }
    }
}
