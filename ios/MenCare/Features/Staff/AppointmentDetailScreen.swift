import SwiftUI
import Observation

@MainActor
@Observable
final class AppointmentDetailViewModel {

    private let booking: BookingRepository
    private let crm: CrmRepository
    private let catalog: CatalogRepository
    private let appointmentId: String

    let state = LoadState()

    private(set) var appointment: Appointment?
    private(set) var client: ClientRecord?
    private(set) var services: [String: Service] = [:]
    private(set) var history: [Appointment] = []
    var actionError: String?

    init(booking: BookingRepository, crm: CrmRepository, catalog: CatalogRepository, appointmentId: String) {
        self.booking = booking
        self.crm = crm
        self.catalog = catalog
        self.appointmentId = appointmentId
    }

    func load() async {
        await state.run {
            let catalogue = try await catalog.services()
            services = Dictionary(uniqueKeysWithValues: catalogue.map { ($0.id, $0) })

            let found = try await booking.appointment(appointmentId)
            appointment = found
            guard let found else {
                client = nil
                history = []
                return
            }
            // Scheda e storico arrivano insieme da `GET /crm/clients/:id`: e'
            // l'unica rotta che uno STAFF puo' usare per vedere il passato di
            // un cliente, e gli importi li nasconde il server.
            let detail = try await crm.clientDetail(found.clientId)
            client = detail.client
            history = detail.history.filter { $0.status == .completed && $0.id != appointmentId }
        }
    }

    func markCompleted() {
        Task {
            actionError = nil
            switch await booking.markCompleted(appointmentId) {
            case .success: await load()
            case .failure(let error): actionError = error.displayMessage
            }
        }
    }

    /// "Non si è presentato": il cliente riceve una notifica; `markCompleted` lo corregge.
    func markNoShow() {
        Task {
            actionError = nil
            switch await booking.markNoShow(appointmentId) {
            case .success: await load()
            case .failure(let error): actionError = error.displayMessage
            }
        }
    }

    /// Annullato da dietro la poltrona: l'attore e' il salone, non il cliente.
    func cancel(onDone: @escaping () -> Void) {
        Task {
            actionError = nil
            switch await booking.cancel(appointmentId, by: .salon) {
            case .success: onDone()
            case .failure(let error): actionError = error.displayMessage
            }
        }
    }
}

struct AppointmentDetailScreen: View {
    @State var viewModel: AppointmentDetailViewModel
    let onBack: () -> Void

    @State private var confirmCancel = false
    @State private var confirmNoShow = false
    @State private var editSheet: StaffBookingViewModel?
    @Environment(\.container) private var container
    @Environment(\.openURL) private var openURL

    var body: some View {
        Group {
            if let apt = viewModel.appointment {
                content(apt)
            } else if viewModel.state.isLoading {
                BrandLoading().frame(maxHeight: .infinity)
            } else if let error = viewModel.state.error {
                BrandErrorRetry(error: error, retry: { Task { await viewModel.load() } })
                    .frame(maxHeight: .infinity)
            } else {
                Color.bone
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
    }

    private func content(_ apt: Appointment) -> some View {
        let client = viewModel.client
        let completed = apt.status == .completed
        return VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 18, trailing: 0)) {
                BrandTopBar(
                    title: L("apt_detail_title_short"),
                    onBack: onBack,
                    backLabel: L("staff_tab_agenda")
                ) {
                    // Si modifica solo ciò che è ancora in programma.
                    if apt.isActive {
                        BarAction(text: L("apt_detail_edit"), action: { openEdit(apt) }, color: .oliveWood)
                    }
                }
                HStack(spacing: 12) {
                    // Iniziali oro su nero, come nelle schede del titolare; il filo
                    // chiaro stacca il riquadro dalla banda scura.
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.ink)
                        .frame(width: 52, height: 52)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(Color.bone.opacity(0.14), lineWidth: 1)
                        )
                        .overlay(
                            Text(client?.initials ?? "")
                                .font(Typo.cormorant(17, weight: .regular))
                                .foregroundStyle(Color.oliveWood)
                        )
                    VStack(alignment: .leading, spacing: 6) {
                        Text(client?.fullName ?? "")
                            .font(Typo.cormorant(24))
                            .foregroundStyle(Color.bone)
                        HStack(spacing: 7) {
                            headerPill(statusLabel(apt.status), accent: true)
                            headerPill(L("apt_detail_visits", client?.visitCount ?? 0), accent: false)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 6)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        StatTile(
                            label: L("apt_detail_time"),
                            value: "\(formatTime(apt.time)) — \(formatTime(apt.end.time))"
                        )
                        // La durata al posto del totale: le cifre le vede solo il titolare.
                        StatTile(label: L("apt_detail_duration"), value: formatDuration(apt.durationMinutes))
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        BrandSectionLabel(text: L("apt_detail_services"))
                        HStack(spacing: 8) {
                            ForEach(apt.serviceIds.compactMap { viewModel.services[$0] }) { service in
                                Text("\(service.name) · \(service.durationMinutes)′")
                                    .font(Typo.titleSmall)
                                    .foregroundStyle(Color.bone)
                                    .lineLimit(1)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 11)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
                            }
                        }
                    }

                    // La nota scritta dal cliente nel riepilogo della prenotazione.
                    if let note = apt.noteForOperator, !note.trimmingCharacters(in: .whitespaces).isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            BrandSectionLabel(text: L("apt_detail_client_note"))
                            Text(note)
                                .font(Typo.bodyMedium)
                                .foregroundStyle(Color.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 15)
                                .padding(.vertical, 14)
                                .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
                        }
                    }

                    if !viewModel.history.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            BrandSectionLabel(text: L("apt_detail_history"))
                            ForEach(viewModel.history) { past in
                                HStack {
                                    Text(
                                        "\(formatDateShort(past.date)) · " +
                                            past.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + ")
                                    )
                                    .font(Typo.bodyMedium)
                                    .foregroundStyle(Color.ink)
                                    .lineLimit(1)
                                    Spacer()
                                }
                                .padding(.horizontal, 15)
                                .padding(.vertical, 14)
                                .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 16)
                .readableWidth()
            }

            // Stato a mano: il no-show, da orario d'inizio passato.
            if apt.canMarkNoShow() {
                Button {
                    confirmNoShow = true
                } label: {
                    Text(L("apt_detail_mark_no_show"))
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.ink)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
            if apt.isActive {
                Button {
                    confirmCancel = true
                } label: {
                    Text(L("apt_detail_cancel"))
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.errorRed)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            }
            if let actionError = viewModel.actionError {
                Text(actionError)
                    .font(Typo.bodySmall)
                    .foregroundStyle(Color.errorRed)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 6)
            }
            HStack(spacing: 10) {
                Button {
                    // Su un iPad senza telefonia openURL non fa nulla.
                    if let url = phoneDialURL(viewModel.client?.phone) { openURL(url) }
                } label: {
                    Text(L("apt_detail_call"))
                        .font(Typo.titleMedium)
                        .foregroundStyle(Color.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
                }
                .buttonStyle(.plain)
                // Lo stato si chiude da solo a fine servizio (§6.2): il
                // pulsante resta solo dove serve davvero, cioè per rimettere a
                // posto un no-show segnato per sbaglio.
                if apt.canRevertNoShow {
                    AccentButton(
                        text: L("apt_detail_complete"),
                        action: viewModel.markCompleted,
                        height: 54,
                        corner: 16
                    )
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
            .readableWidth()
        }
        .background(Color.bone)
        .alert(L("apt_detail_cancel_title"), isPresented: $confirmCancel) {
            Button(L("apt_detail_cancel_yes"), role: .destructive) {
                viewModel.cancel(onDone: onBack)
            }
            Button(L("apt_detail_cancel_no"), role: .cancel) {}
        } message: {
            Text(L("apt_detail_cancel_body"))
        }
        .alert(L("apt_detail_no_show_title"), isPresented: $confirmNoShow) {
            Button(L("apt_detail_no_show_yes"), role: .destructive) { viewModel.markNoShow() }
            Button(L("apt_detail_cancel_no"), role: .cancel) {}
        } message: {
            Text(L("apt_detail_no_show_body"))
        }
        .sheet(item: $editSheet) { sheetViewModel in
            StaffBookingScreen(
                viewModel: sheetViewModel,
                onDismiss: {
                    let saved = sheetViewModel.done
                    editSheet = nil
                    // Salvata la modifica, questo appuntamento è stato sostituito:
                    // si torna all'agenda, dove c'è quello nuovo.
                    if saved { onBack() }
                }
            )
        }
    }

    private func openEdit(_ appointment: Appointment) {
        editSheet = StaffBookingViewModel(
            auth: container.auth, booking: container.booking,
            catalog: container.catalog, crm: container.crm,
            editAppointment: appointment, editClient: viewModel.client
        )
    }

    private func statusLabel(_ status: AppointmentStatus) -> String {
        switch status {
        case .inProgress: L("staff_in_progress")
        case .completed: L("apt_detail_completed")
        case .noShow: L("apt_detail_no_show")
        default: L("apts_status_confirmed_staff")
        }
    }

    private func headerPill(_ text: String, accent: Bool) -> some View {
        Text(text.uppercased())
            .font(Typo.jost(9, weight: .medium))
            .kerning(1.3)
            .foregroundStyle(Color.bone)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(accent ? Color.oliveWood : Color.bone.opacity(0.12))
            )
    }
}
