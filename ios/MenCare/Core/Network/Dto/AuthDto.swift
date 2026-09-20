import Foundation

// `POST /v1/auth/*` e `GET/PATCH /v1/auth/me` — vedi docs/API.md §Accesso e profilo.

struct UserDto: Decodable {
    let id: String
    let firstName: String
    let lastName: String
    let email: String
    let phone: String
    let role: String
    let memberSince: LocalDate
    let visitCount: Int
    let avatarUrl: String?
    let clientId: String?
    let operatorId: String?

    func toDomain() -> User {
        User(
            id: id,
            firstName: firstName,
            lastName: lastName,
            email: email,
            phone: phone,
            role: WireEnum.role(role),
            memberSince: memberSince,
            visitCount: visitCount,
            // Il campo si chiama `avatarPath` da quando la foto stava sul
            // telefono: ora e' l'URL servito dall'API, ma il significato per la
            // schermata e' lo stesso ("qualcosa da mostrare al posto delle iniziali").
            avatarPath: avatarUrl,
            clientRecordId: clientId,
            operatorId: operatorId
        )
    }
}

struct TokensDto: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int

    func toDomain() -> AuthTokens {
        AuthTokens(accessToken: accessToken, refreshToken: refreshToken, expiresIn: expiresIn)
    }
}

struct AuthResultDto: Decodable {
    let user: UserDto?
    let tokens: TokensDto
}

struct RefreshRequestDto: Encodable {
    let refreshToken: String
}

struct LoginRequestDto: Encodable {
    let email: String
    let password: String
}

struct SocialLoginRequestDto: Encodable {
    let provider: String
    let idToken: String
}

struct RegisterRequestDto: Encodable {
    let firstName: String
    let lastName: String
    let phone: String
    let email: String
    let password: String
}

struct ProfileRequestDto: Encodable {
    let firstName: String
    let lastName: String
    let email: String
    let phone: String
}

struct PasswordResetRequestDto: Encodable {
    let email: String
}

struct ChangePasswordRequestDto: Encodable {
    let currentPassword: String
    let newPassword: String
}

/// Gli stessi tre interruttori in lettura e in scrittura: `GET`/`PUT` hanno
/// forma identica, quindi un solo DTO.
struct NotificationPrefsDto: Codable {
    let appointmentReminder: Bool
    let waitlistAlerts: Bool
    let marketing: Bool

    func toDomain() -> ClientNotificationPrefs {
        ClientNotificationPrefs(
            appointmentReminder: appointmentReminder,
            waitlistAlerts: waitlistAlerts,
            marketing: marketing
        )
    }

    init(_ prefs: ClientNotificationPrefs) {
        appointmentReminder = prefs.appointmentReminder
        waitlistAlerts = prefs.waitlistAlerts
        marketing = prefs.marketing
    }
}

struct AvatarResponseDto: Decodable {
    let avatarUrl: String
}
