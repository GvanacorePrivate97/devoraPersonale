import SwiftUI

struct LoginScreen: View {
    @State var viewModel: LoginViewModel
    let onLoggedIn: (UserRole) -> Void
    let onRegister: () -> Void
    let onForgotPassword: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Il logo completo (monogramma, nome e "Men Care") al centro della banda:
            // porta già il nome del salone, e il resto dell'header lo segue centrato.
            DarkHeader(contentPadding: EdgeInsets(top: 18, leading: 26, bottom: 28, trailing: 26)) {
                VStack(spacing: 0) {
                    Image("logo-lockup")
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 210)
                        .accessibilityHidden(true)
                    Text(L("auth_login_title"))
                        .font(Typo.displayMedium)
                        .foregroundStyle(Color.bone)
                        .padding(.top, 18)
                    Text(L("auth_login_subtitle"))
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.bone)
                        .padding(.top, 4)
                }
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    EmailField(
                        label: L("auth_email"),
                        text: $viewModel.email,
                        leadingSystemImage: "envelope",
                        error: viewModel.emailError
                    )
                    .onChange(of: viewModel.email) { viewModel.clearError() }
                    .padding(.bottom, 12)
                    PasswordField(
                        label: L("auth_password"),
                        text: $viewModel.password,
                        error: viewModel.passwordError ?? errorText
                    )
                    .onChange(of: viewModel.password) { viewModel.clearError() }
                    .padding(.bottom, 12)
                    Button(action: onForgotPassword) {
                        Text(L("auth_forgot_password"))
                            .font(Typo.jost(13))
                            .foregroundStyle(Color.ink)
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.bottom, 12)
                    // L'errore compare anche sotto il tasto: un guasto di rete
                    // non riguarda il campo password, e li' si perderebbe.
                    FormErrorBanner(message: viewModel.passwordError == nil ? viewModel.errorMessage : nil)
                    AccentButton(
                        text: L("auth_login_cta"),
                        action: { viewModel.login(onLoggedIn: onLoggedIn) },
                        loading: viewModel.loading,
                        height: 56,
                        corner: 16
                    )
                    divider
                    // Linea guida App Store 4.8: con un login social di terze
                    // parti l'app iOS deve offrire anche Sign in with Apple.
                    SocialButton(
                        text: L("auth_social_apple"),
                        icon: Image(systemName: "apple.logo")
                    ) {
                        viewModel.loginWithProvider(.apple, onLoggedIn: onLoggedIn)
                    }
                    .padding(.bottom, 10)
                    SocialButton(
                        text: L("auth_social_google"),
                        icon: Image("ic-google")
                    ) {
                        viewModel.loginWithProvider(.google, onLoggedIn: onLoggedIn)
                    }
                    HStack(spacing: 6) {
                        Text(L("auth_no_account"))
                            .font(Typo.bodyMedium)
                            .foregroundStyle(Color.ink)
                        Button(action: onRegister) {
                            Text(L("auth_register_link"))
                                .font(Typo.titleSmall)
                                .foregroundStyle(Color.oliveWood)
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 28)
                    Text(L("auth_demo_hint"))
                        .font(Typo.bodySmall)
                        .foregroundStyle(Color.textMuted)
                        .padding(.top, 20)
                        .padding(.bottom, 16)
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .readableWidth()
            }
        }
        .background(Color.bone)
    }

    private var errorText: String? { viewModel.errorMessage }

    private var divider: some View {
        HStack(spacing: 12) {
            Rectangle().fill(Color.stone).frame(height: 1)
            Text(L("auth_social_divider").uppercased())
                .font(Typo.jost(11))
                .kerning(1.5)
                .foregroundStyle(Color.ink)
            Rectangle().fill(Color.stone).frame(height: 1)
        }
        .padding(.top, 20)
        .padding(.bottom, 14)
    }
}
