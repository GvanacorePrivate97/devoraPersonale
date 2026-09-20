import SwiftUI

/// Una sola famiglia di campi per tutta l'app.
///
/// Prima lo stesso concetto aveva quattro comportamenti diversi (il telefono era
/// validato in registrazione, grezzo nell'editor operatore e inesistente nelle
/// due schede "nuovo cliente"). Qui la regola arriva sempre da
/// `Core/Common/Validation.swift`, che è la copia delle regole del backend:
/// stesso campo = stessa regola = stesso messaggio, ovunque.

// MARK: - Riga messaggio

/// Riga sotto un campo: errore in rosso, altrimenti l'eventuale aiuto in grigio.
/// È l'unico posto in cui si disegna il feedback di un campo.
struct FieldMessageRow: View {
    var error: String?
    var helper: String?

    var body: some View {
        if let error {
            Text(error)
                .font(Typo.bodySmall)
                .foregroundStyle(Color.errorRed)
                .fixedSize(horizontal: false, vertical: true)
        } else if let helper {
            Text(helper)
                .font(Typo.bodySmall)
                .foregroundStyle(Color.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Nome

/// Nome o cognome: maiuscola iniziale automatica e la regola dei nomi propri.
struct NameField: View {
    let label: String
    @Binding var text: String
    var placeholder: String?
    var error: String?
    var height: CGFloat = 52
    var outlined: Bool = false

    var body: some View {
        FilledTextField(
            label: label,
            text: $text,
            placeholder: placeholder,
            autocapitalization: .words,
            error: error,
            height: height,
            outlined: outlined
        )
    }
}

// MARK: - Email

/// Tastiera email e nessuna maiuscola automatica: "Mario@…" e "mario@…" sono
/// lo stesso account, e la normalizzazione a minuscolo la fa il view model.
struct EmailField: View {
    let label: String
    @Binding var text: String
    var placeholder: String?
    var leadingSystemImage: String?
    var error: String?
    var height: CGFloat = 52
    var outlined: Bool = false

    var body: some View {
        FilledTextField(
            label: label,
            text: $text,
            placeholder: placeholder,
            leadingSystemImage: leadingSystemImage,
            keyboard: .emailAddress,
            autocapitalization: .never,
            error: error,
            height: height,
            outlined: outlined
        )
    }
}

// MARK: - Telefono

/// Telefono: tastiera numerica, prefisso `+39` mostrato finché il numero non ne
/// porta uno suo, e normalizzazione in E.164 quando il campo perde il fuoco —
/// così quello che si salva è sempre nello stesso formato, da qualsiasi
/// schermata arrivi.
struct PhoneField: View {
    let label: String
    @Binding var text: String
    var placeholder: String?
    var error: String?
    var height: CGFloat = 52
    var outlined: Bool = false

    @FocusState private var focused: Bool

    /// Il prefisso è solo decorazione: sparisce appena il numero ne ha uno
    /// proprio (`+39…`, `0039…`), altrimenti si leggerebbe "+39 +39 347…".
    private var showsPrefix: Bool {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        return !trimmed.hasPrefix("+") && !trimmed.hasPrefix("00")
    }

    var body: some View {
        FilledTextField(
            label: label,
            text: $text,
            placeholder: placeholder,
            prefix: showsPrefix ? defaultPhoneCountryCode : nil,
            keyboard: .phonePad,
            autocapitalization: .never,
            error: error,
            height: height,
            outlined: outlined,
            focus: $focused
        )
        .onChange(of: focused) { _, isFocused in
            guard !isFocused else { return }
            if let e164 = normalizePhone(text) { text = e164 }
        }
    }
}

// MARK: - Password

/// Password: mostra/nascondi già nel campo, più il misuratore di forza quando
/// serve. Il punteggio arriva da `passwordStrength`, lo stesso del backend:
/// prima registrazione e cambio password lo calcolavano ognuna per conto suo.
struct PasswordField: View {
    let label: String
    @Binding var text: String
    var error: String?
    var showStrength: Bool = false
    var height: CGFloat = 52

    private var strength: PasswordStrength? {
        guard showStrength, !text.isEmpty else { return nil }
        return passwordStrength(text)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            BorderedPasswordField(label: label, text: $text, error: error, height: height)
            if let strength {
                HStack(spacing: 7) {
                    SegmentedMeter(filled: strength.meterFill, total: 4)
                    Text(strength.label)
                        .font(Typo.jost(11))
                        .foregroundStyle(Color.ink)
                }
            }
        }
    }
}

// MARK: - Prezzo

/// Prezzo in euro: tastiera decimale, un solo separatore ammesso e conversione
/// in centesimi con arrotondamento (mai troncamento) fatta dal view model con
/// `parsePriceToCents`. Il filtro a ogni tasto impedisce che "1.2.3" arrivi al
/// parser e finisca salvato come 0,00 €.
struct PriceField: View {
    let label: String
    @Binding var text: String
    var error: String?
    var height: CGFloat = 56

    var body: some View {
        FilledTextField(
            label: label,
            text: Binding(
                get: { text },
                set: { text = sanitizePriceInput($0) }
            ),
            placeholder: "0",
            keyboard: .decimalPad,
            autocapitalization: .never,
            error: error,
            height: height
        )
    }
}

// MARK: - Durata

/// Durata a passi di 5 minuti, con i limiti del backend (da 5 minuti a 8 ore).
struct DurationField: View {
    let label: String
    @Binding var minutes: Int
    var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !label.isEmpty {
                BrandSectionLabel(text: label)
            }
            HStack {
                Text(L("svc_minutes", minutes))
                    .font(Typo.bodyLarge)
                    .foregroundStyle(Color.ink)
                Spacer()
                stepperButton("minus") {
                    minutes = clampDuration(minutes - ValidationLimits.durationStepMinutes)
                }
                stepperButton("plus") {
                    minutes = clampDuration(minutes + ValidationLimits.durationStepMinutes)
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 56)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.stone))
            FieldMessageRow(error: error, helper: nil)
        }
    }

    private func stepperButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle()
                .fill(Color.bone)
                .frame(width: 28, height: 28)
                .overlay(
                    Image(systemName: systemImage)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.ink)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Testo lungo

/// Campo multiriga con contatore: nota della prenotazione e corpo della
/// campagna, che finora erano due box scritti a mano.
struct CounterTextField<Accessory: View>: View {
    let label: String
    @Binding var text: String
    var placeholder: String
    var limit: Int
    var error: String?
    var minHeight: CGFloat
    @ViewBuilder var accessory: Accessory

    init(
        label: String,
        text: Binding<String>,
        placeholder: String = "",
        limit: Int,
        error: String? = nil,
        minHeight: CGFloat = 56,
        @ViewBuilder accessory: () -> Accessory = { EmptyView() }
    ) {
        self.label = label
        self._text = text
        self.placeholder = placeholder
        self.limit = limit
        self.error = error
        self.minHeight = minHeight
        self.accessory = accessory()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                BrandSectionLabel(text: label)
                Spacer()
                Text(L("field_char_counter", text.count, limit))
                    .font(Typo.jost(11))
                    .foregroundStyle(text.count >= limit ? Color.errorRed : Color.textMuted)
            }
            VStack(alignment: .leading, spacing: 10) {
                TextField(placeholder, text: $text, axis: .vertical)
                    .font(Typo.jost(14))
                    .foregroundStyle(Color.ink)
                    .tint(Color.oliveWood)
                    .frame(minHeight: minHeight, alignment: .top)
                    .onChange(of: text) { _, newValue in
                        // Il limite è del backend: si taglia qui, non al salvataggio.
                        if newValue.count > limit { text = String(newValue.prefix(limit)) }
                    }
                accessory
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(error != nil ? Color.errorRed : Color.oliveWood, lineWidth: 1.5)
            )
            FieldMessageRow(error: error, helper: nil)
        }
    }
}

// MARK: - Nuovo cliente

/// Nome + cognome + telefono: la stessa scheda che aprono l'agenda
/// dell'operatore e la prenotazione manuale del titolare. Prima erano due copie
/// senza nessun controllo, e la creazione falliva in silenzio.
struct NewClientForm: View {
    let firstLabel: String
    let lastLabel: String
    let phoneLabel: String
    let ctaLabel: String
    @Binding var firstName: String
    @Binding var lastName: String
    @Binding var phone: String
    var errors: [String: String]
    var formError: String?
    var creating: Bool = false
    let onCreate: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                NameField(label: firstLabel, text: $firstName, error: errors["firstName"])
                NameField(label: lastLabel, text: $lastName, error: errors["lastName"])
            }
            PhoneField(label: phoneLabel, text: $phone, error: errors["phone"])
            FormErrorBanner(message: formError)
            AccentButton(text: ctaLabel, action: onCreate, loading: creating, height: 48, corner: 14)
        }
    }
}

/// Le regole della scheda, in un posto solo: le usano entrambi i view model.
/// Il telefono è obbligatorio come su `POST /crm/clients`.
func validateNewClient(firstName: String, lastName: String, phone: String) -> [String: String] {
    var errors: [String: String] = [:]
    errors["firstName"] = validateName(firstName).message
    errors["lastName"] = validateName(lastName).message
    errors["phone"] = validatePhone(phone).message
    return errors.compactMapValues { $0 }
}

// MARK: - Errore di form

/// Banner di fallimento sotto un form: prima esisteva solo nelle schermate di
/// autenticazione, e le schermate del titolare non dicevano nulla.
struct FormErrorBanner: View {
    let message: String?

    var body: some View {
        if let message {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.errorRed)
                Text(message)
                    .font(Typo.bodySmall)
                    .foregroundStyle(Color.errorRed)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.errorRed.opacity(0.10)))
        }
    }
}
