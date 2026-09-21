import SwiftUI
import Observation

@MainActor
@Observable
final class CampaignViewModel {

    private let admin: AdminRepository

    var id = ""
    var name = ""
    var segment: CampaignSegment = .inattivi60
    var title = ""
    var body = ""
    var scheduleLater = false
    var scheduledDate: LocalDate = LocalDate.today().plusDays(1)
    var scheduledTime = LocalTime(10, 30)
    var repeatWeekly = false
    var sendCap = 4
    var reachable = 0
    var segmentSize = 0
    var done = false
    var sending = false
    /// Chiave del campo → messaggio: prima il tasto "Invia" con nome o
    /// messaggio vuoto usciva in silenzio e sembrava rotto.
    var fieldErrors: [String: String] = [:]
    var saveError: String?

    let state = LoadState()

    @ObservationIgnored private var submitted = false
    @ObservationIgnored private var reachTask: Task<Void, Never>?

    init(admin: AdminRepository) {
        self.admin = admin
    }

    func load() async {
        await state.run {
            // Si riprende la bozza aperta, se c'e'.
            if let draft = try await admin.campaigns().first(where: { $0.status == .draft }) {
                id = draft.id
                name = draft.name
                segment = draft.segment
                title = draft.title
                body = draft.body
                repeatWeekly = draft.repeatWeekly
                sendCap = draft.sendCap ?? 4
                scheduleLater = draft.scheduledAt != nil
                scheduledDate = draft.scheduledAt?.date ?? LocalDate.today().plusDays(1)
                scheduledTime = draft.scheduledAt?.time ?? LocalTime(10, 30)
            }
            // La copertura la conta il server: quanti riceverebbero davvero la
            // push, non un numero gonfiato per somigliare al mockup.
            let reach = try await admin.reachFor(segment)
            reachable = reach.reachable
            segmentSize = reach.size
        }
    }

    /// Push preview with merge tokens personalised for the sample client.
    var previewBody: String {
        body
            .replacingOccurrences(of: "{{nome}}", with: "Marco")
            .replacingOccurrences(of: "{{link}}", with: "mencare.app/prenota")
    }

    func refreshReach() {
        reachTask?.cancel()
        reachTask = Task {
            guard let reach = try? await admin.reachFor(segment) else { return }
            reachable = reach.reachable
            segmentSize = reach.size
        }
    }

    func setSegment(_ newSegment: CampaignSegment) {
        segment = newSegment
        refreshReach()
    }

    func fieldChanged() {
        saveError = nil
        if submitted { fieldErrors = validate() }
    }

    func appendToken(_ token: String) {
        body = String((body + token).prefix(campaignBodyLimit))
        fieldChanged()
    }

    private func validate() -> [String: String] {
        var errors: [String: String] = [:]
        errors["name"] = validateRequiredText(name).message
        errors["title"] = validateRequiredText(title).message
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedBody.isEmpty {
            errors["body"] = L("validation_required")
        } else if body.count > campaignBodyLimit {
            errors["body"] = L("validation_note_max", campaignBodyLimit)
        }
        return errors.compactMapValues { $0 }
    }

    func send() {
        guard !sending else { return }
        submitted = true
        let errors = validate()
        if !errors.isEmpty {
            fieldErrors = errors
            saveError = L("validation_form_invalid")
            return
        }
        fieldErrors = [:]
        saveError = nil
        sending = true
        Task {
            let result = await admin.saveCampaign(
                PushCampaign(
                    id: id,
                    name: name,
                    segment: segment,
                    title: title,
                    body: body,
                    scheduledAt: scheduleLater ? scheduledDate.atTime(scheduledTime) : nil,
                    repeatWeekly: repeatWeekly,
                    sendCap: repeatWeekly ? sendCap : nil,
                    reachableCount: reachable,
                    segmentSize: segmentSize,
                    status: scheduleLater ? .scheduled : .sent
                )
            )
            sending = false
            switch result {
            case .success:
                done = true
            case .failure(let error):
                // Il server valida nome, titolo e data d'invio: il suo messaggio
                // dice piu' di un generico "non riuscito".
                saveError = error.displayMessage
            }
        }
    }
}

private enum Picking: Identifiable {
    case date, time
    var id: Self { self }
}

struct CampaignScreen: View {
    @Bindable var viewModel: CampaignViewModel
    let onBack: () -> Void

    @State private var picking: Picking?

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 18, trailing: 0)) {
                BrandTopBar(title: L("camp_title"), onBack: onBack) {
                    BarAction(text: L("camp_draft"), action: onBack, color: .oliveLight)
                }
                HStack {
                    Text(viewModel.name.isEmpty ? L("camp_name") : viewModel.name)
                        .font(Typo.cormorant(28))
                        .foregroundStyle(Color.bone)
                    Spacer()
                    Text(L("camp_reach", viewModel.reachable, viewModel.segmentSize))
                        .font(Typo.jost(11, weight: .medium))
                        .foregroundStyle(Color.bone)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.bone.opacity(0.12)))
                }
                .padding(.horizontal, 22)
                .padding(.top, 6)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    FilledTextField(
                        label: L("camp_name"), text: $viewModel.name,
                        autocapitalization: .sentences,
                        error: viewModel.fieldErrors["name"], outlined: true
                    )
                    .onChange(of: viewModel.name) { viewModel.fieldChanged() }

                    BrandSectionLabel(text: L("camp_segment"))
                    HStack(spacing: 8) {
                        segmentChip(.inattivi60, L("camp_seg_inactive"))
                        segmentChip(.tutti, L("camp_seg_all"))
                        segmentChip(.topSpesa, L("camp_seg_top"))
                    }

                    FilledTextField(
                        label: L("camp_msg_title"), text: $viewModel.title,
                        autocapitalization: .sentences,
                        error: viewModel.fieldErrors["title"]
                    )
                    .onChange(of: viewModel.title) { viewModel.fieldChanged() }

                    bodyEditor

                    FormErrorBanner(message: viewModel.saveError)

                    BrandSectionLabel(text: L("camp_preview"))
                    preview

                    BrandSectionLabel(text: L("camp_scheduling"))
                    SegmentedTabs(
                        options: [L("camp_send_now"), L("camp_schedule")],
                        selectedIndex: viewModel.scheduleLater ? 1 : 0,
                        onSelect: { viewModel.scheduleLater = $0 == 1 },
                        onDark: false
                    )
                    if viewModel.scheduleLater {
                        HStack(spacing: 10) {
                            PickerTile(label: L("camp_date"), value: formatDateShort(viewModel.scheduledDate)) {
                                picking = .date
                            }
                            PickerTile(label: L("camp_time"), value: formatTime(viewModel.scheduledTime)) {
                                picking = .time
                            }
                        }
                        HStack {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(L("camp_repeat_weekly"))
                                    .font(Typo.jost(15, weight: .medium))
                                    .foregroundStyle(Color.ink)
                                Text(L("camp_stop_after", viewModel.sendCap))
                                    .font(Typo.jost(12))
                                    .foregroundStyle(Color.textMuted)
                            }
                            Spacer()
                            BrandSwitch(isOn: $viewModel.repeatWeekly)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
                .readableWidth()
            }

            AccentButton(
                text: L(viewModel.scheduleLater ? "camp_schedule_cta" : "camp_send_now"),
                action: viewModel.send,
                loading: viewModel.sending,
                height: 56,
                corner: 16,
                leadingSystemImage: "paperplane"
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
            .readableWidth()
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .onChange(of: viewModel.done) {
            if viewModel.done { onBack() }
        }
        .sheet(item: $picking) { mode in
            switch mode {
            case .date:
                BrandDatePickerDialog(
                    initial: viewModel.scheduledDate,
                    onDismiss: { picking = nil },
                    onConfirm: { date in
                        viewModel.scheduledDate = date
                        picking = nil
                    }
                )
            case .time:
                BrandTimePickerDialog(
                    initial: viewModel.scheduledTime,
                    onDismiss: { picking = nil },
                    onConfirm: { time in
                        viewModel.scheduledTime = time
                        picking = nil
                    }
                )
            }
        }
    }

    private func segmentChip(_ segment: CampaignSegment, _ label: String) -> some View {
        BrandChip(
            text: label,
            selected: viewModel.segment == segment,
            action: { viewModel.setSegment(segment) },
            fill: true
        )
    }

    private var bodyEditor: some View {
        CounterTextField(
            label: L("camp_msg_body"),
            text: $viewModel.body,
            limit: campaignBodyLimit,
            error: viewModel.fieldErrors["body"]
        ) {
            HStack(spacing: 8) {
                tokenChip(L("camp_token_name"), token: "{{nome}}")
                tokenChip(L("camp_token_link"), token: "{{link}}")
            }
        }
        .onChange(of: viewModel.body) { viewModel.fieldChanged() }
    }

    private func tokenChip(_ label: String, token: String) -> some View {
        Button {
            viewModel.appendToken(token)
        } label: {
            Text(label)
                .font(Typo.jost(12))
                .foregroundStyle(Color.ink)
                .padding(.horizontal, 11)
                .padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.stone))
        }
        .buttonStyle(.plain)
    }

    private var preview: some View {
        HStack(alignment: .top, spacing: 11) {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.bone.opacity(0.14))
                .frame(width: 34, height: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.title.isEmpty ? L("camp_msg_title") : viewModel.title)
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.bone)
                Text(viewModel.previewBody)
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.bone)
            }
            Spacer()
            Text(L("camp_now"))
                .font(Typo.jost(11))
                .foregroundStyle(Color.bone.opacity(0.6))
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
    }
}
