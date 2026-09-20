import Foundation

private let avatarFolder = "avatars"

@MainActor
final class FakeAvatarRepository: AvatarRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func save(imageData: Data) async -> AppResult<String> {
        guard let current = store.currentUser else { return .failure(.notFound) }
        do {
            let folder = URL.documentsDirectory.appending(path: avatarFolder)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            // Nome nuovo a ogni salvataggio: così la cache immagini non
            // continua a mostrare la foto precedente.
            let target = folder.appending(path: "\(current.id)-\(Int(Date().timeIntervalSince1970 * 1000)).jpg")
            try imageData.write(to: target)
            deleteFile(current.avatarPath)
            apply(userId: current.id, path: target.path)
            return .success(target.path)
        } catch {
            return .failure(.unknown(message: error.localizedDescription))
        }
    }

    func clear() async -> AppResult<Void> {
        guard let current = store.currentUser else { return .failure(.notFound) }
        deleteFile(current.avatarPath)
        apply(userId: current.id, path: nil)
        return .success(())
    }

    private func deleteFile(_ path: String?) {
        guard let path else { return }
        try? FileManager.default.removeItem(atPath: path)
    }

    private func apply(userId: String, path: String?) {
        store.users = store.users.map {
            guard $0.id == userId else { return $0 }
            var user = $0
            user.avatarPath = path
            return user
        }
        if var current = store.currentUser, current.id == userId {
            current.avatarPath = path
            store.currentUser = current
        }
    }
}
