import Foundation

/// Demo auth: any seeded e-mail + any password of 6+ characters signs in.
/// Social buttons sign in as the demo client.
@MainActor
final class FakeAuthRepository: AuthRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func currentUser() async throws -> User? { store.currentUser }

    func login(email: String, password: String) async -> AppResult<User> {
        try? await Task.sleep(for: .milliseconds(600))
        if password.count < 6 { return .failure(.invalidCredentials) }
        let trimmed = email.trimmingCharacters(in: .whitespaces).lowercased()
        guard let user = store.users.first(where: { $0.email.lowercased() == trimmed }) else {
            return .failure(.invalidCredentials)
        }
        store.currentUser = user
        return .success(user)
    }

    func loginWithProvider(_ provider: SocialProvider) async -> AppResult<User> {
        try? await Task.sleep(for: .milliseconds(600))
        let user = store.users.first { $0.id == DemoSeed.userClient }!
        store.currentUser = user
        return .success(user)
    }

    func register(firstName: String, lastName: String, phone: String, email: String, password: String) async -> AppResult<User> {
        try? await Task.sleep(for: .milliseconds(800))
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        if store.users.contains(where: { $0.email.lowercased() == trimmedEmail.lowercased() }) {
            return .failure(.emailAlreadyRegistered)
        }
        let record = ClientRecord(
            id: store.newId("cli"),
            firstName: firstName.trimmingCharacters(in: .whitespaces),
            lastName: lastName.trimmingCharacters(in: .whitespaces),
            phone: phone.trimmingCharacters(in: .whitespaces),
            email: trimmedEmail,
            customerSince: .today(),
            visitCount: 0,
            lifetimeSpendCents: 0,
            noShowCount: 0,
            lastVisit: nil
        )
        store.clients.append(record)
        let user = User(
            id: store.newId("user"),
            firstName: record.firstName,
            lastName: record.lastName,
            email: trimmedEmail,
            phone: record.phone,
            role: .client,
            memberSince: .today(),
            visitCount: 0,
            clientRecordId: record.id
        )
        store.users.append(user)
        store.currentUser = user
        return .success(user)
    }

    func requestPasswordReset(email: String) async -> AppResult<Void> {
        try? await Task.sleep(for: .milliseconds(600))
        return .success(())
    }

    func updateProfile(firstName: String, lastName: String, email: String, phone: String) async -> AppResult<User> {
        guard var updated = store.currentUser else { return .failure(.notFound) }
        updated.firstName = firstName.trimmingCharacters(in: .whitespaces)
        updated.lastName = lastName.trimmingCharacters(in: .whitespaces)
        updated.email = email.trimmingCharacters(in: .whitespaces)
        updated.phone = phone.trimmingCharacters(in: .whitespaces)
        let id = updated.id
        store.users = store.users.map { $0.id == id ? updated : $0 }
        store.currentUser = updated
        return .success(updated)
    }

    func changePassword(currentPassword: String, newPassword: String) async -> AppResult<Void> {
        try? await Task.sleep(for: .milliseconds(600))
        guard store.currentUser != nil else { return .failure(.notFound) }
        // Demo auth never persists a real password (any 6+ char password logs
        // in), so the "current password" check mirrors login's own rule rather
        // than comparing to a stored value.
        if currentPassword.count < 6 { return .failure(.invalidCredentials) }
        return .success(())
    }

    func notificationPrefs() async throws -> ClientNotificationPrefs { store.clientPrefs }

    func updateNotificationPrefs(_ prefs: ClientNotificationPrefs) async -> AppResult<ClientNotificationPrefs> {
        store.clientPrefs = prefs
        return .success(prefs)
    }

    func logout() async {
        store.currentUser = nil
    }
}
