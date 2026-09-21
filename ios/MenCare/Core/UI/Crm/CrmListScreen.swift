import SwiftUI
import Observation

@MainActor
@Observable
final class CrmListViewModel {

    private let crm: CrmRepository

    var query = "" {
        didSet { scheduleReload() }
    }

    var segment: ClientSegment = .tutti {
        didSet { scheduleReload() }
    }

    let state = LoadState()
    private(set) var clients: [ClientRecord] = []

    /// La ricerca la fa il server: si aspetta che l'utente smetta di scrivere,
    /// altrimenti si manderebbe una richiesta per ogni tasto.
    @ObservationIgnored private var reloadTask: Task<Void, Never>?

    init(crm: CrmRepository) {
        self.crm = crm
    }

    func load() async {
        await state.run {
            clients = try await crm.clients(query: query, segment: segment)
        }
    }

    private func scheduleReload() {
        reloadTask?.cancel()
        reloadTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            await load()
        }
    }
}

struct CrmListScreen: View {
    @Bindable var viewModel: CrmListViewModel
    let onClient: (String) -> Void

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 10, leading: 20, bottom: 16, trailing: 20)) {
                Text(L("crm_title"))
                    .font(Typo.cormorant(30))
                    .foregroundStyle(Color.bone)
                FilledTextField(
                    label: "",
                    text: $viewModel.query,
                    placeholder: L("crm_search_hint"),
                    leadingSystemImage: "magnifyingglass"
                )
                .padding(.top, 14)
                // Due filtri che si escludono sono tab, non chip: stesso
                // controllo degli appuntamenti, della dashboard e di Gestione.
                SegmentedTabs(
                    options: [L("crm_seg_all"), L("crm_seg_inactive")],
                    selectedIndex: viewModel.segment == .tutti ? 0 : 1,
                    onSelect: { viewModel.segment = $0 == 0 ? .tutti : .inattivi60 }
                )
                .padding(.top, 12)
            }

            Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                list
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var list: some View {
        VStack(spacing: 0) {
            if viewModel.clients.isEmpty {
                Text(L("crm_empty"))
                    .font(Typo.bodyMedium)
                    .foregroundStyle(Color.textMuted)
                    .padding(.top, 48)
                Spacer()
            } else {
                let grouped = Dictionary(grouping: viewModel.clients) { $0.lastName.first.map(String.init)?.uppercased() ?? "" }
                    .sorted { $0.key < $1.key }
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(grouped, id: \.key) { letter, clients in
                            Text(letter)
                                .font(Typo.jost(11, weight: .medium))
                                .kerning(1.3)
                                .foregroundStyle(Color.textMuted)
                                .padding(.top, 10)
                                .padding(.bottom, 6)
                            ForEach(clients) { client in
                                clientRow(client)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 24)
                    .readableWidth()
                }
            }
        }
    }

    private func clientRow(_ client: ClientRecord) -> some View {
        Button {
            onClient(client.id)
        } label: {
            HStack(spacing: 12) {
                Circle()
                    .fill(Color.oliveWood)
                    .frame(width: 38, height: 38)
                    .overlay(
                        Text(client.initials)
                            .font(Typo.cormorant(13, weight: .regular))
                            .foregroundStyle(Color.bone)
                    )
                VStack(alignment: .leading, spacing: 0) {
                    Text(client.fullName)
                        .font(Typo.jost(15, weight: .medium))
                        .foregroundStyle(Color.bone)
                        .lineLimit(1)
                    Text(
                        [
                            "\(client.visitCount) \(L("crm_visits_suffix"))",
                            client.lastVisit.map { "\(L("crm_last_visit")) \(formatDateShort($0))" },
                        ].compactMap { $0 }.joined(separator: " · ")
                    )
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.onDarkMuted)
                    .lineLimit(1)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.oliveWood)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 9)
    }
}
