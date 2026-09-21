import SwiftUI
import Observation

@MainActor
@Observable
final class ProfileViewModel {

    private let auth: AuthRepository
    private let catalog: CatalogRepository

    var firstName = ""
    var lastName = ""
    var email = ""
    var phone = ""
    var dirty = false
    var saved = false
    var saving = false
    /// Chiave del campo → messaggio: stesse regole della registrazione, che qui
    /// prima non c'erano affatto.
    var fieldErrors: [String: String] = [:]
    /// Esito vero del salvataggio: prima `saved` diventava true comunque, anche
    /// quando la chiamata falliva.
    var saveError: String?

    let state = LoadState()
    private(set) var user: User?
    private(set) var salon = Salon(name: "", address: "", city: "")
    private(set) var prefs = ClientNotificationPrefs()
    /// Gli interruttori scrivono subito e non hanno un tasto "Salva": se il
    /// server rifiuta va detto li'.
    var prefsError: String?

    @ObservationIgnored private var submitted = false

    init(auth: AuthRepository, catalog: CatalogRepository) {
        self.auth = auth
        self.catalog = catalog
    }

    func load() async {
        await state.run {
            let me = try await auth.currentUser()
            user = me
            // I campi si ricompilano solo se non li si sta modificando: un
            // ricaricamento non deve cancellare quello che si sta scrivendo.
            if !dirty, let me {
                firstName = me.firstName
                lastName = me.lastName
                email = me.email
                phone = me.phone
            }
            salon = try await catalog.catalog().salon
            prefs = try await auth.notificationPrefs()
        }
    }

    func fieldChanged() {
        dirty = true
        saved = false
        saveError = nil
        if submitted { fieldErrors = validate() }
    }

    private func validate() -> [String: String] {
        var errors: [String: String] = [:]
        errors["firstName"] = validateName(firstName).message
        errors["lastName"] = validateName(lastName).message
        errors["email"] = validateEmail(email).message
        errors["phone"] = validatePhone(phone).message
        return errors.compactMapValues { $0 }
    }

    func save() {
        guard !saving else { return }
        submitted = true
        let errors = validate()
        if !errors.isEmpty {
            fieldErrors = errors
            saveError = L("validation_form_invalid")
            return
        }
        fieldErrors = [:]
        saveError = nil
        saving = true
        let phoneToSave = normalizePhone(phone) ?? phone
        Task {
            let result = await auth.updateProfile(
                firstName: firstName.trimmingCharacters(in: .whitespaces),
                lastName: lastName.trimmingCharacters(in: .whitespaces),
                email: normalizeEmail(email),
                phone: phoneToSave
            )
            saving = false
            switch result {
            case .success(let user):
                phone = user.phone
                email = user.email
                dirty = false
                saved = true
            case .failure(let error):
                saved = false
                saveError = error.displayMessage
            }
        }
    }

    func setPrefs(_ updated: ClientNotificationPrefs) {
        let previous = prefs
        // Aggiornamento ottimista: l'interruttore si muove subito e torna
        // indietro se il server dice di no.
        prefs = updated
        prefsError = nil
        Task {
            switch await auth.updateNotificationPrefs(updated) {
            case .success(let stored): prefs = stored
            case .failure(let error):
                prefs = previous
                prefsError = error.displayMessage
            }
        }
    }

    func logout(onLoggedOut: @escaping () -> Void) {
        Task {
            await auth.logout()
            onLoggedOut()
        }
    }
}

struct ProfileScreen: View {
    @Bindable var viewModel: ProfileViewModel
    let onLoggedOut: () -> Void

    @State private var editing = false
    @State private var changingPassword = false
    @Environment(\.container) private var container

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 12, leading: 20, bottom: 20, trailing: 20)) {
                HStack(spacing: 14) {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.oliveWood)
                        .frame(width: 56, height: 56)
                        .overlay(
                            Text((viewModel.user?.initials ?? "").uppercased())
                                .font(Typo.cormorant(19, weight: .regular))
                                .foregroundStyle(Color.bone)
                        )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(viewModel.user?.fullName ?? "")
                            .font(Typo.cormorant(26))
                            .foregroundStyle(Color.bone)
                        Text(L(
                            "profile_since",
                            viewModel.user.map { formatMonthYear($0.memberSince) } ?? "",
                            viewModel.user?.visitCount ?? 0
                        ))
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.bone)
                    }
                    Spacer()
                    BarAction(
                        text: L(editing ? "profile_save" : "profile_edit"),
                        action: {
                            guard editing else {
                                editing = true
                                return
                            }
                            if viewModel.dirty {
                                viewModel.save()
                                // Si esce dalla modifica solo se i campi reggono:
                                // altrimenti l'errore resterebbe invisibile.
                                editing = !viewModel.fieldErrors.isEmpty
                            } else {
                                editing = false
                            }
                        },
                        color: .oliveLight
                    )
                }
            }

            if viewModel.state.isLoading, !viewModel.state.hasLoadedOnce {
                BrandLoading()
                Spacer()
            } else if let error = viewModel.state.error, !viewModel.state.hasLoadedOnce {
                BrandErrorRetry(error: error, retry: { Task { await viewModel.load() } })
                Spacer()
            } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if let error = viewModel.state.error {
                        InlineErrorBar(message: error.displayMessage, retry: { Task { await viewModel.load() } })
                    }
                    BrandSectionLabel(text: L("profile_personal_data"))
                    if editing {
                        NameField(
                            label: L("profile_first_name"), text: $viewModel.firstName,
                            error: viewModel.fieldErrors["firstName"]
                        )
                        NameField(
                            label: L("profile_last_name"), text: $viewModel.lastName,
                            error: viewModel.fieldErrors["lastName"]
                        )
                        EmailField(
                            label: L("profile_email"), text: $viewModel.email,
                            error: viewModel.fieldErrors["email"]
                        )
                        PhoneField(
                            label: L("profile_phone"), text: $viewModel.phone,
                            error: viewModel.fieldErrors["phone"]
                        )
                        FormErrorBanner(message: viewModel.saveError)
                    } else {
                        VStack(spacing: 0) {
                            StoneKeyValueRow(label: L("profile_first_name"), value: "\(viewModel.firstName) \(viewModel.lastName)")
                            Rectangle().fill(Color.stoneBorder).frame(height: 1)
                            StoneKeyValueRow(label: L("profile_email"), value: viewModel.email)
                            Rectangle().fill(Color.stoneBorder).frame(height: 1)
                            StoneKeyValueRow(label: L("profile_phone"), value: viewModel.phone)
                        }
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }

                    // The salon's own opening hours, as the owner sets them in Gestione.
                    salonHours

                    BrandSectionLabel(text: L("profile_account_management"))
                        .padding(.top, 6)
                    StoneKeyValueRow(
                        label: L("profile_change_password"),
                        value: "••••••••",
                        valueColor: .oliveWood,
                        onTap: { changingPassword = true }
                    )
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))

                    BrandSectionLabel(text: L("profile_notifications"))
                        .padding(.top, 6)
                    prefRow(L("profile_pref_reminder"), hint: L("profile_pref_reminder_hint"), keyPath: \.appointmentReminder)
                    prefRow(L("profile_pref_waitlist"), hint: nil, keyPath: \.waitlistAlerts)
                    prefRow(L("profile_pref_marketing"), hint: nil, keyPath: \.marketing)
                    FormErrorBanner(message: viewModel.prefsError)

                    Button {
                        viewModel.logout(onLoggedOut: onLoggedOut)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                .font(.system(size: 15))
                                .foregroundStyle(Color.errorRed)
                            Text(L("profile_logout"))
                                .font(Typo.titleMedium)
                                .foregroundStyle(Color.ink)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.stoneBorder, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)

                    if editing && viewModel.dirty {
                        AccentButton(
                            text: L("profile_save"),
                            action: {
                                viewModel.save()
                                editing = !viewModel.fieldErrors.isEmpty
                            },
                            loading: viewModel.saving,
                            height: 54,
                            corner: 16
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 24)
                .readableWidth()
            }
            .onChange(of: viewModel.firstName) { viewModel.fieldChanged() }
            .onChange(of: viewModel.lastName) { viewModel.fieldChanged() }
            .onChange(of: viewModel.email) { viewModel.fieldChanged() }
            .onChange(of: viewModel.phone) { viewModel.fieldChanged() }
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .sheet(isPresented: $changingPassword) {
            ChangePasswordScreen(
                viewModel: ChangePasswordViewModel(auth: container.auth),
                onDismiss: { changingPassword = false }
            )
        }
    }

    @ViewBuilder
    private var salonHours: some View {
        let groups = viewModel.salon.weeklyHours.groupConsecutiveDays()
        if groups.contains(where: { $0.range != nil }) {
            BrandSectionLabel(text: L("profile_salon_hours"))
                .padding(.top, 6)
            VStack(spacing: 8) {
                ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                    let closed = group.range == nil
                    StoneKeyValueRow(
                        label: formatDayGroup(group.days),
                        value: group.range.map { "\(formatTime($0.start)) — \(formatTime($0.end))" }
                            ?? L("profile_salon_closed"),
                        valueColor: closed ? .textMuted : .ink
                    )
                    .background(
                        closed
                            ? AnyView(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.stoneBorder, lineWidth: 1))
                            : AnyView(RoundedRectangle(cornerRadius: 10).fill(Color.stone))
                    )
                }
            }
            Text(viewModel.salon.address)
                .font(Typo.jost(12))
                .foregroundStyle(Color.textMuted)
                .padding(.leading, 4)
                .padding(.top, 2)
        }
    }

    private func prefRow(_ title: String, hint: String?, keyPath: WritableKeyPath<ClientNotificationPrefs, Bool>) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(Typo.bodyLarge).foregroundStyle(Color.ink)
                if let hint {
                    Text(hint).font(Typo.jost(12)).foregroundStyle(Color.textMuted)
                }
            }
            Spacer()
            BrandSwitch(isOn: Binding(
                get: { viewModel.prefs[keyPath: keyPath] },
                set: { newValue in
                    var prefs = viewModel.prefs
                    prefs[keyPath: keyPath] = newValue
                    viewModel.setPrefs(prefs)
                }
            ))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
    }
}
