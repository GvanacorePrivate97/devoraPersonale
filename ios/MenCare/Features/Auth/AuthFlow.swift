import SwiftUI

private enum AuthRoute: Hashable {
    case register, recover
}

/// Auth area: splash, then login with registration and password recovery —
/// the iOS counterpart of the Android auth nav graph.
struct AuthFlow: View {
    @Environment(\.container) private var container
    let onAuthenticated: (UserRole) -> Void

    @State private var path: [AuthRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            LoginScreen(
                viewModel: LoginViewModel(auth: container.auth),
                onLoggedIn: onAuthenticated,
                onRegister: { path.append(.register) },
                onForgotPassword: { path.append(.recover) }
            )
            .navigationBarHidden(true)
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .register:
                    RegisterScreen(
                        viewModel: RegisterViewModel(auth: container.auth),
                        onRegistered: onAuthenticated,
                        onBack: { path.removeLast() }
                    )
                    .navigationBarHidden(true)
                case .recover:
                    RecoverScreen(
                        viewModel: RecoverViewModel(auth: container.auth),
                        onBack: { path.removeLast() }
                    )
                    .navigationBarHidden(true)
                }
            }
        }
    }
}
