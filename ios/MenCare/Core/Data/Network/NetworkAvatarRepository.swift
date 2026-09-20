import Foundation

@MainActor
final class NetworkAvatarRepository: AvatarRepository {

    private let client: ApiClient
    private let session: AppSession

    init(client: ApiClient, session: AppSession) {
        self.client = client
        self.session = session
    }

    func save(imageData: Data) async -> AppResult<String> {
        await apiResult {
            let response = try await client.upload(
                path: AuthEndpoint.avatarPath,
                fileData: imageData,
                fileName: "avatar.jpg",
                mimeType: "image/jpeg",
                as: AvatarResponseDto.self
            )
            // La sessione porta l'URL nuovo subito: il profilo mostra la foto
            // senza aspettare un altro giro su `/auth/me`.
            apply(response.avatarUrl)
            return response.avatarUrl
        }
    }

    func clear() async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(AuthEndpoint.deleteAvatar)
            apply(nil)
        }
    }

    private func apply(_ url: String?) {
        guard var user = session.user else { return }
        user.avatarPath = url
        session.set(user)
    }
}
