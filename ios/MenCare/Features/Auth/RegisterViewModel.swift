import Foundation
import Observation

@MainActor
@Observable
final class RegisterViewModel {

    private let auth: AuthRepository

    var firstName = ""
    var lastName = ""
    var phone = ""
    var email = ""
    var password = ""
    var passwordConfirm = ""
    var termsAccepted = false
    /// Chiave del campo → messaggio già localizzato: le regole stanno in
    /// `Core/Common/Validation.swift`, la schermata si limita a mostrarle.
    var fieldErrors: [String: String] = [:]
    var termsError = false
    var loading = false
    /// Messaggio del server quando non e' colpa di un campo preciso: rete
    /// assente, troppi tentativi, guasto.
    var genericError: String?

    @ObservationIgnored private var submitted = false

    init(auth: AuthRepository) {
        self.auth = auth
    }

    /// Errors are silent until the first submit, then update live per keystroke.
    func fieldChanged() {
        fieldErrors = submitted ? validate() : [:]
        genericError = nil
    }

    func termsChanged() {
        termsError = false
    }

    private func validate() -> [String: String] {
        var errors: [String: String] = [:]
        errors["firstName"] = validateName(firstName).message
        errors["lastName"] = validateName(lastName).message
        errors["phone"] = validatePhone(phone).message
        errors["email"] = validateEmail(email).message
        errors["password"] = validatePassword(password).message
        errors["passwordConfirm"] = validatePasswordConfirm(password, passwordConfirm).message
        return errors.compactMapValues { $0 }
    }

    func register(onRegistered: @escaping (UserRole) -> Void) {
        guard !loading else { return }
        submitted = true
        let errors = validate()
        if !errors.isEmpty || !termsAccepted {
            fieldErrors = errors
            termsError = !termsAccepted
            return
        }
        loading = true
        // Il numero si salva normalizzato in E.164: prima si concatenava "+39 "
        // a mano e chi scriveva già il prefisso finiva con "+39 +39…".
        let normalizedPhone = normalizePhone(phone) ?? phone
        Task {
            let result = await auth.register(
                firstName: firstName.trimmingCharacters(in: .whitespaces),
                lastName: lastName.trimmingCharacters(in: .whitespaces),
                phone: normalizedPhone,
                email: normalizeEmail(email),
                password: password
            )
            loading = false
            switch result {
            case .success:
                onRegistered(.client)
            case .failure(let error):
                switch error {
                case .emailAlreadyRegistered:
                    fieldErrors = ["email": L("auth_error_email_taken")]
                case .validation(let field) where !field.isEmpty:
                    // Il server valida gli stessi campi del form: il suo
                    // messaggio va sul campo giusto, non in fondo alla pagina.
                    fieldErrors = [field: error.displayMessage]
                default:
                    genericError = error.displayMessage
                }
            }
        }
    }
}
