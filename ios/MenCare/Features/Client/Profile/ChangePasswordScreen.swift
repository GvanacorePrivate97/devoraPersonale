import SwiftUI
import Observation

@MainActor
@Observable
final class ChangePasswordViewModel {

    private let auth: AuthRepository

    var currentPassword = ""
    var newPassword = ""
    var confirmPassword = ""
    /// Chiave del campo → messaggio già localizzato. Le regole e le frasi sono
    /// quelle di `Core/Common/Validation.swift`: prima questa schermata aveva
    /// un suo enum e una sua copia del misuratore di forza.
    var fieldErrors: [String: String] = [:]
    var loading = false
    var genericError: String?
    var done = false

    @ObservationIgnored private var submitted = false

    init(auth: AuthRepository) {
        self.auth = auth
    }

    func fieldChanged() {
        fieldErrors = submitted ? validate() : [:]
        genericError = nil
    }

    private func validate() -> [String: String] {
        var errors: [String: String] = [:]
        // La password attuale si controlla solo come "obbligatoria": è già
        // stata scelta, non la si sta scegliendo adesso.
        errors["current"] = currentPassword.isEmpty ? L("validation_required") : nil
        errors["new"] = validatePassword(newPassword).message
        errors["confirm"] = validatePasswordConfirm(newPassword, confirmPassword).message
        return errors.compactMapValues { $0 }
    }

    func save() {
        guard !loading else { return }
        submitted = true
        let errors = validate()
        if !errors.isEmpty {
            fieldErrors = errors
            return
        }
        loading = true
        Task {
            let result = await auth.changePassword(currentPassword: currentPassword, newPassword: newPassword)
            loading = false
            switch result {
            case .success:
                done = true
            case .failure(let error):
                switch error {
                case .invalidCredentials:
                    fieldErrors = ["current": L("change_password_error_wrong_current")]
                case .validation(let field) where !field.isEmpty:
                    fieldErrors = [field == "newPassword" ? "new" : field: error.displayMessage]
                default:
                    genericError = error.displayMessage
                }
            }
        }
    }
}

/// "Cambia password" sheet: current password + new password (with strength meter) + confirm.
struct ChangePasswordScreen: View {
    @Bindable var viewModel: ChangePasswordViewModel
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BarAction(text: L("change_password_cancel"), action: onDismiss, color: .ink)
                    .frame(width: 72, alignment: .leading)
                Text(L("change_password_title"))
                    .font(Typo.cormorant(21, weight: .regular))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                Color.clear.frame(width: 72, height: 1)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    PasswordField(
                        label: L("change_password_current"),
                        text: $viewModel.currentPassword,
                        error: viewModel.fieldErrors["current"]
                    )
                    PasswordField(
                        label: L("change_password_new"),
                        text: $viewModel.newPassword,
                        error: viewModel.fieldErrors["new"],
                        showStrength: true
                    )
                    PasswordField(
                        label: L("change_password_confirm"),
                        text: $viewModel.confirmPassword,
                        error: viewModel.fieldErrors["confirm"]
                    )
                    FormErrorBanner(message: viewModel.genericError)
                    AccentButton(
                        text: L("change_password_cta"),
                        action: viewModel.save,
                        loading: viewModel.loading,
                        height: 56,
                        corner: 16
                    )
                    .padding(.top, 4)
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 20)
            }
            .onChange(of: viewModel.currentPassword) { viewModel.fieldChanged() }
            .onChange(of: viewModel.newPassword) { viewModel.fieldChanged() }
            .onChange(of: viewModel.confirmPassword) { viewModel.fieldChanged() }
        }
        .background(Color.bone)
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(26)
        .onChange(of: viewModel.done) {
            if viewModel.done { onDismiss() }
        }
    }

}
