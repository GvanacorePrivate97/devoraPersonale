import SwiftUI
import Observation

@MainActor
@Observable
final class CrmDetailViewModel {

    private let crm: CrmRepository
    private let catalog: CatalogRepository
    private let clientId: String

    init(crm: CrmRepository, catalog: CatalogRepository, clientId: String) {
        self.crm = crm
        self.catalog = catalog
        self.clientId = clientId
    }

    let state = LoadState()

    private(set) var client: ClientRecord?
    /// Lo storico arriva con la scheda: una chiamata sola, e gli importi li
    /// filtra il server secondo il ruolo.
    private(set) var history: [Appointment] = []
    private(set) var services: [String: Service] = [:]
    private(set) var operators: [String: Operator] = [:]

    func load() async {
        await state.run {
            let snapshot = try await catalog.catalog()
            services = Dictionary(uniqueKeysWithValues: snapshot.services.map { ($0.id, $0) })
            operators = Dictionary(uniqueKeysWithValues: snapshot.operators.map { ($0.id, $0) })

            let detail = try await crm.clientDetail(clientId)
            client = detail.client
            history = detail.history
        }
    }
}

struct CrmDetailScreen: View {
    @State var viewModel: CrmDetailViewModel
    /// Spesa totale e importi delle visite: solo il titolare vede le cifre.
    let showEconomics: Bool
    let onBack: () -> Void
    let onNewBooking: () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        Group {
            if let client = viewModel.client {
                content(client)
            } else if let error = viewModel.state.error {
                BrandErrorRetry(error: error, retry: { Task { await viewModel.load() } })
                    .frame(maxHeight: .infinity)
            } else {
                BrandLoading().frame(maxHeight: .infinity)
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
    }

    /// Abitudini calcolate dal server: con chi viene più spesso e ogni quanto
    /// torna. Compare solo quando c'è qualcosa da dire.
    @ViewBuilder
    private func habits(_ client: ClientRecord) -> some View {
        let favorite = client.favoriteOperatorId.flatMap { viewModel.operators[$0]?.name }
        if favorite != nil || client.averageDaysBetweenVisits != nil {
            BrandSectionLabel(text: L("crm_habits"))
            VStack(spacing: 0) {
                if let favorite {
                    StoneKeyValueRow(label: L("crm_favorite_operator"), value: favorite)
                }
                if favorite != nil && client.averageDaysBetweenVisits != nil {
                    Rectangle().fill(Color.stoneBorder).frame(height: 1)
                }
                if let days = client.averageDaysBetweenVisits {
                    StoneKeyValueRow(label: L("crm_cadence"), value: L("crm_cadence_value", days))
                }
            }
            .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md)
                    .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
            )
            .padding(.bottom, 8)
        }
    }

    private func content(_ client: ClientRecord) -> some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 18, trailing: 0)) {
                BrandTopBar(
                    title: L("crm_detail_title"),
                    onBack: onBack,
                    backLabel: L("crm_title")
                ) {
                    BarAction(text: L("crm_edit"), action: onBack, color: .oliveLight)
                }
                HStack(spacing: 13) {
                    // Sulla banda scura l'iniziale è oro su un tondo appena
                    // sollevato: il quadrato d'oliva era l'unico posto in cui
                    // l'accento chiaro finiva sul nero.
                    Circle()
                        .fill(Color.inkRaised)
                        .frame(width: 60, height: 60)
                        .overlay(Circle().strokeBorder(Color.inkBorder, lineWidth: 1))
                        .overlay(
                            Text(client.initials)
                                .font(Typo.cormorant(22, weight: .regular))
                                .foregroundStyle(Color.oliveLight)
                        )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(client.fullName)
                            .font(Typo.cormorant(25))
                            .foregroundStyle(Color.bone)
                            .lineLimit(1)
                        Text("\(client.phone) · \(L("crm_since", client.customerSince.year))")
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.bone)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 6)
                HStack(spacing: 10) {
                    StatTile(
                        label: L("crm_stats_visits"), value: "\(client.visitCount)",
                        container: .bone.opacity(0.08), contentColor: .bone
                    )
                    if showEconomics {
                        StatTile(
                            label: L("crm_stats_spend"), value: formatPriceCompact(client.lifetimeSpendCents),
                            container: .bone.opacity(0.08), contentColor: .bone
                        )
                    }
                    StatTile(
                        label: L("crm_stats_noshow"), value: "\(client.noShowCount)",
                        container: .bone.opacity(0.08), contentColor: .bone
                    )
                }
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 20)
                .padding(.top, 16)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    habits(client)
                    BrandSectionLabel(text: L("crm_history"))
                    ForEach(viewModel.history) { appointment in
                        HStack(spacing: 11) {
                            Circle().fill(Color.oliveWood).frame(width: 6, height: 6)
                            VStack(alignment: .leading, spacing: 0) {
                                Text(
                                    "\(formatDateShort(appointment.date)) · " +
                                        appointment.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + ")
                                )
                                .font(Typo.jost(14))
                                .foregroundStyle(Color.bone)
                                .lineLimit(1)
                                Text(viewModel.operators[appointment.operatorId]?.name ?? "")
                                    .font(Typo.jost(12))
                                    .foregroundStyle(Color.onDarkMuted)
                            }
                            Spacer()
                            if showEconomics {
                                Text(formatPriceCompact(appointment.totalPriceCents))
                                    .font(Typo.titleSmall)
                                    .foregroundStyle(Color.oliveWood)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 13)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 16)
                .readableWidth()
            }

            // "Chiama" apre il tastierino con il numero del cliente.
            HStack(spacing: 10) {
                Button {
                    if let url = phoneDialURL(client.phone) { openURL(url) }
                } label: {
                    Text(L("crm_call"))
                        .font(Typo.titleMedium)
                        .foregroundStyle(Color.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.md)
                                .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
                        )
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
                AccentButton(text: L("crm_new_booking"), action: onNewBooking, height: 54, corner: 16)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 14)
            .readableWidth()
            // Barra fissa in fondo: fondo pieno, filo e ombra sopra, così le azioni
            // restano sopra lo storico che scorre invece di confondersi con le card.
            .frame(maxWidth: .infinity)
            .background(Color.bone.shadow(.drop(color: .black.opacity(0.08), radius: 10, y: -3)))
            .overlay(alignment: .top) {
                Rectangle().fill(Color.stoneBorder).frame(height: 1)
            }
        }
        .background(Color.bone)
    }
}
