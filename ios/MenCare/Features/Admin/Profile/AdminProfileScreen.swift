import SwiftUI
import PhotosUI
import Observation

@MainActor
@Observable
final class AdminProfileViewModel {

    private let auth: AuthRepository
    private let avatar: AvatarRepository
    private let catalog: CatalogRepository

    init(auth: AuthRepository, avatar: AvatarRepository, catalog: CatalogRepository) {
        self.auth = auth
        self.avatar = avatar
        self.catalog = catalog
    }

    let state = LoadState()
    private(set) var user: User?
    private(set) var salon = Salon(name: "", address: "", city: "")
    var actionError: String?

    func load() async {
        await state.run {
            user = try await auth.currentUser()
            salon = try await catalog.catalog().salon
        }
    }

    func onPhotoPicked(_ data: Data) {
        Task {
            actionError = nil
            if case .failure(let error) = await avatar.save(imageData: data) {
                actionError = error.displayMessage
            }
            await load()
        }
    }

    func onPhotoRemoved() {
        Task {
            actionError = nil
            if case .failure(let error) = await avatar.clear() {
                actionError = error.displayMessage
            }
            await load()
        }
    }

    func logout(onDone: @escaping () -> Void) {
        Task {
            await auth.logout()
            onDone()
        }
    }
}

/// Owner's own corner of the app: who is signed in, which salon they run, and
/// the way out of the account.
struct AdminProfileScreen: View {
    @State var viewModel: AdminProfileViewModel
    let onLoggedOut: () -> Void

    @State private var pickedItem: PhotosPickerItem?

    var body: some View {
        // Il selettore foto vuole una chiusura `Sendable`: i dati dell'utente si
        // leggono prima, qui, dove siamo ancora sul main actor.
        let initials = viewModel.user?.initials ?? ""
        let photoPath = viewModel.user?.avatarPath
        return VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 12, leading: 20, bottom: 20, trailing: 20)) {
                HStack(spacing: 14) {
                    PhotosPicker(selection: $pickedItem, matching: .images) {
                        ProfileAvatar(initials: initials, photoPath: photoPath, editable: true)
                    }
                    .buttonStyle(.plain)
                    Text(viewModel.user?.fullName ?? "")
                        .font(Typo.cormorant(26))
                        .foregroundStyle(Color.bone)
                    Spacer()
                }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .bottom) {
                        BrandSectionLabel(text: L("admin_profile_account"))
                        Spacer()
                        if viewModel.user?.avatarPath != nil {
                            Button {
                                viewModel.onPhotoRemoved()
                            } label: {
                                Text(L("admin_profile_photo_remove"))
                                    .font(Typo.jost(12, weight: .medium))
                                    .foregroundStyle(Color.oliveWood)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    VStack(spacing: 0) {
                        StoneKeyValueRow(label: L("admin_profile_email"), value: viewModel.user?.email ?? "")
                        Rectangle().fill(Color.stoneBorder).frame(height: 1)
                        StoneKeyValueRow(label: L("admin_profile_phone"), value: viewModel.user?.phone ?? "")
                    }
                    .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.md)
                            .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    BrandSectionLabel(text: L("admin_profile_salon"))
                        .padding(.top, 6)
                    VStack(spacing: 0) {
                        StoneKeyValueRow(label: L("admin_profile_salon_name"), value: viewModel.salon.name)
                        Rectangle().fill(Color.stoneBorder).frame(height: 1)
                        StoneKeyValueRow(label: L("admin_profile_salon_address"), value: viewModel.salon.address)
                    }
                    .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.md)
                            .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    Button {
                        viewModel.logout(onDone: onLoggedOut)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                .font(.system(size: 15))
                                .foregroundStyle(Color.errorRed)
                            Text(L("admin_logout"))
                                .font(Typo.titleMedium)
                                .foregroundStyle(Color.errorRed)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .overlay(RoundedRectangle(cornerRadius: Radii.md).strokeBorder(Color.errorRed, lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 6)
                    FormErrorBanner(message: viewModel.actionError)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 24)
                .readableWidth()
            }
        }
        .task { await viewModel.load() }
        .background(Color.bone)
        .onChange(of: pickedItem) {
            guard let pickedItem else { return }
            Task {
                if let data = try? await pickedItem.loadTransferable(type: Data.self) {
                    viewModel.onPhotoPicked(data)
                }
                self.pickedItem = nil
            }
        }
    }
}
