import SwiftUI

private let fieldHeight: CGFloat = 48

struct RegisterScreen: View {
    @State var viewModel: RegisterViewModel
    let onRegistered: (UserRole) -> Void
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 20, trailing: 0)) {
                BrandTopBar(title: L("auth_register_topbar"), onBack: onBack)
                VStack(alignment: .leading, spacing: 4) {
                    Text(L("auth_register_title"))
                        .font(Typo.cormorant(34))
                        .foregroundStyle(Color.bone)
                    Text(L("auth_register_subtitle"))
                        .font(Typo.jost(13, weight: .medium))
                        .foregroundStyle(Color.bone)
                }
                .padding(.horizontal, 26)
                .padding(.top, 6)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 11) {
                    HStack(alignment: .top, spacing: 10) {
                        NameField(
                            label: L("auth_first_name"), text: $viewModel.firstName,
                            error: viewModel.fieldErrors["firstName"], height: fieldHeight
                        )
                        NameField(
                            label: L("auth_last_name"), text: $viewModel.lastName,
                            error: viewModel.fieldErrors["lastName"], height: fieldHeight
                        )
                    }
                    PhoneField(
                        label: L("auth_phone"), text: $viewModel.phone,
                        error: viewModel.fieldErrors["phone"], height: fieldHeight
                    )
                    EmailField(
                        label: L("auth_email"), text: $viewModel.email,
                        error: viewModel.fieldErrors["email"], height: fieldHeight
                    )
                    PasswordField(
                        label: L("auth_password"), text: $viewModel.password,
                        error: viewModel.fieldErrors["password"],
                        showStrength: true, height: fieldHeight
                    )
                    PasswordField(
                        label: L("auth_password_confirm"), text: $viewModel.passwordConfirm,
                        error: viewModel.fieldErrors["passwordConfirm"], height: fieldHeight
                    )
                    HStack(alignment: .top, spacing: 11) {
                        BrandCheckbox(checked: $viewModel.termsAccepted)
                            .onChange(of: viewModel.termsAccepted) { viewModel.termsChanged() }
                        VStack(alignment: .leading, spacing: 4) {
                            (
                                Text(L("auth_terms")).font(Typo.jost(12)).foregroundColor(.ink) +
                                    Text(" " + L("auth_terms_link")).font(Typo.jost(12, weight: .medium)).foregroundColor(.oliveWood)
                            )
                            if viewModel.termsError {
                                Text(L("auth_error_terms"))
                                    .font(Typo.bodySmall)
                                    .foregroundStyle(Color.errorRed)
                            }
                        }
                    }
                    .padding(.top, 2)
                    FormErrorBanner(message: viewModel.genericError)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .readableWidth()
            }
            .onChange(of: viewModel.firstName) { viewModel.fieldChanged() }
            .onChange(of: viewModel.lastName) { viewModel.fieldChanged() }
            .onChange(of: viewModel.phone) { viewModel.fieldChanged() }
            .onChange(of: viewModel.email) { viewModel.fieldChanged() }
            .onChange(of: viewModel.password) { viewModel.fieldChanged() }
            .onChange(of: viewModel.passwordConfirm) { viewModel.fieldChanged() }

            BottomActionBar {
                AccentButton(
                    text: L("auth_register_cta"),
                    action: { viewModel.register(onRegistered: onRegistered) },
                    loading: viewModel.loading,
                    height: 56,
                    corner: 16
                )
                .readableWidth()
                HStack(spacing: 6) {
                    Text(L("auth_have_account"))
                        .font(Typo.jost(13))
                        .foregroundStyle(Color.ink)
                    Button(action: onBack) {
                        Text(L("auth_login_link"))
                            .font(Typo.jost(13, weight: .medium))
                            .foregroundStyle(Color.oliveWood)
                    }
                    .buttonStyle(.plain)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 10)
            }
        }
        .background(Color.bone)
    }
}
