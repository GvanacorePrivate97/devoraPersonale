import SwiftUI
import Observation

@MainActor
@Observable
final class RecoverViewModel {

    private let auth: AuthRepository

    var email = ""
    var loading = false
    var sent = false
    /// Stessa regola e stesso messaggio della registrazione: qui prima bastava
    /// una chiocciola e un punto, così "a.@b" passava di qua e non di là.
    var emailError: String?
    var genericError: String?

    init(auth: AuthRepository) {
        self.auth = auth
    }

    func emailChanged() {
        emailError = nil
        genericError = nil
    }

    func send() {
        guard !loading else { return }
        let check = validateEmail(email)
        guard check.isValid else {
            emailError = check.message
            return
        }
        emailError = nil
        genericError = nil
        loading = true
        Task {
            let result = await auth.requestPasswordReset(email: normalizeEmail(email))
            loading = false
            // L'esito non si butta più via: prima `sent` diventava true comunque.
            switch result {
            case .success:
                sent = true
            case .failure(let error):
                sent = false
                genericError = error.displayMessage
            }
        }
    }
}

struct RecoverScreen: View {
    @State var viewModel: RecoverViewModel
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 22, trailing: 0)) {
                BrandTopBar(title: L("auth_recover_topbar"), onBack: onBack)
                VStack(alignment: .leading, spacing: 0) {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.oliveLight.opacity(0.22))
                        .frame(width: 54, height: 54)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(Color.oliveLight.opacity(0.45), lineWidth: 1)
                        )
                        .overlay(
                            Image(systemName: "lock")
                                .font(.system(size: 21))
                                // Sulla banda nera l'accento è l'oro, non l'oliva.
                                .foregroundStyle(Color.oliveLight)
                        )
                    Text(L("auth_recover_title"))
                        .font(Typo.cormorant(36))
                        .foregroundStyle(Color.bone)
                        .padding(.top, 16)
                    Text(L("auth_recover_subtitle"))
                        .font(Typo.jost(13.5, weight: .medium))
                        .foregroundStyle(Color.bone)
                        .padding(.top, 7)
                }
                .padding(.horizontal, 26)
                .padding(.top, 18)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    EmailField(
                        label: L("auth_email_account"),
                        text: $viewModel.email,
                        leadingSystemImage: "envelope",
                        error: viewModel.emailError,
                        outlined: true
                    )
                    .onChange(of: viewModel.email) { viewModel.emailChanged() }
                    AccentButton(
                        text: L("auth_recover_cta"),
                        action: viewModel.send,
                        loading: viewModel.loading,
                        height: 56,
                        corner: 16,
                        leadingSystemImage: "paperplane"
                    )
                    FormErrorBanner(message: viewModel.genericError)
                    if viewModel.sent {
                        StoneCard(corner: 16, container: .oliveWood.opacity(0.14)) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L("auth_recover_sent_title"))
                                    .font(Typo.titleSmall)
                                    .foregroundStyle(Color.ink)
                                Text(L("auth_recover_sent_body", viewModel.email))
                                    .font(Typo.bodySmall)
                                    .foregroundStyle(Color.ink)
                            }
                            .padding(14)
                        }
                    }
                    Text(L("auth_recover_note"))
                        .font(Typo.jost(12.5))
                        .foregroundStyle(viewModel.emailError != nil ? Color.errorRed : Color.ink)
                        .padding(.horizontal, 4)
                    Rectangle().fill(Color.stone).frame(height: 1).padding(.vertical, 4)
                    BrandSectionLabel(text: L("auth_recover_other_ways"))
                    NavigationRow(
                        text: L("auth_recover_sms", "+39 347 •• 4490"),
                        action: viewModel.send,
                        leadingSystemImage: "phone"
                    )
                    NavigationRow(
                        text: L("auth_recover_whatsapp"),
                        action: viewModel.send,
                        leadingSystemImage: "paperplane"
                    )
                }
                .padding(.horizontal, 24)
                .padding(.top, 22)
                .readableWidth()
            }

            Button(action: onBack) {
                Text(L("auth_back_to_login"))
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.oliveWood)
            }
            .buttonStyle(.plain)
            .frame(height: 48)
        }
        .background(Color.bone)
    }
}
