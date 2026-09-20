import SwiftUI

@main
struct MenCareApp: App {
    @State private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(\.appContainer, container)
        }
    }
}

/// Lo smistamento per ruolo avviene qui: ogni ruolo ha la sua radice e una
/// sessione cliente non puo' raggiungere le schermate di staff o titolare —
/// quelle radici esistono solo per il ruolo autenticato, e ogni uscita torna
/// all'accesso.
struct RootView: View {
    @Environment(\.container) private var container

    /// Allo splash si controlla se i token in portachiavi valgono ancora: se
    /// si', si entra direttamente nell'area giusta senza rifare l'accesso.
    @State private var restoring = true
    @State private var role: UserRole?

    var body: some View {
        Group {
            if restoring {
                SplashScreen()
            } else {
                switch role {
                case nil:
                    AuthFlow(onAuthenticated: { role = $0 })
                case .client:
                    ClientRoot(onLoggedOut: { role = nil })
                case .staff:
                    StaffRoot(onLoggedOut: { role = nil })
                case .owner:
                    AdminRoot(onLoggedOut: { role = nil })
                }
            }
        }
        .tint(Color.oliveWood)
        .preferredColorScheme(.light)
        .task { await restore() }
        // La sessione puo' cadere in qualsiasi momento (rinnovo rifiutato, token
        // revocato dal server): si torna all'accesso ovunque ci si trovi.
        .onChange(of: container.session.expiredCount) { role = nil }
    }

    private func restore() async {
        guard restoring else { return }
        // Lo splash ha una durata minima: senza, su rete veloce, comparirebbe e
        // sparirebbe come un lampo.
        async let minimumDelay: () = Task.sleep(for: .milliseconds(1400))
        let restored = try? await container.auth.currentUser()
        try? await minimumDelay
        role = restored?.role
        restoring = false
    }
}
