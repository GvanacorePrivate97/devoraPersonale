import SwiftUI

private let fieldCorner: CGFloat = 14
private let fieldHeight: CGFloat = 52
private let fieldTextSize: CGFloat = 15

/// Uppercase letter-spaced label sitting above an input, as in the design mockup.
struct FieldLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(Typo.jost(11, weight: .medium))
            .kerning(1.5)
            .foregroundStyle(Color.ink)
    }
}

/// Filled input on the Stone surface — the resting state of every text field in the app.
struct FilledTextField<Trailing: View>: View {
    let label: String
    @Binding var text: String
    var placeholder: String?
    var leadingSystemImage: String?
    var prefix: String?
    var keyboard: UIKeyboardType = .default
    var autocapitalization: TextInputAutocapitalization = .never
    var error: String?
    /// Riga grigia sotto il campo quando non c'è errore (es. il formato atteso).
    var helper: String?
    var enabled: Bool = true
    var height: CGFloat = fieldHeight
    var outlined: Bool = false
    /// Passata dai campi condivisi che devono normalizzare il valore al blur
    /// (il telefono): `.focused` funziona solo sulla TextField vera.
    var focus: FocusState<Bool>.Binding?
    @ViewBuilder var trailing: Trailing

    init(
        label: String,
        text: Binding<String>,
        placeholder: String? = nil,
        leadingSystemImage: String? = nil,
        prefix: String? = nil,
        keyboard: UIKeyboardType = .default,
        autocapitalization: TextInputAutocapitalization = .never,
        error: String? = nil,
        helper: String? = nil,
        enabled: Bool = true,
        height: CGFloat = fieldHeight,
        outlined: Bool = false,
        focus: FocusState<Bool>.Binding? = nil,
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) {
        self.label = label
        self._text = text
        self.placeholder = placeholder
        self.leadingSystemImage = leadingSystemImage
        self.prefix = prefix
        self.keyboard = keyboard
        self.autocapitalization = autocapitalization
        self.error = error
        self.helper = helper
        self.enabled = enabled
        self.height = height
        self.outlined = outlined
        self.focus = focus
        self.trailing = trailing()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !label.isEmpty {
                FieldLabel(text: label)
            }
            HStack(spacing: 10) {
                if let leadingSystemImage {
                    Image(systemName: leadingSystemImage)
                        .font(.system(size: 15))
                        .foregroundStyle(Color.ink.opacity(0.45))
                }
                if let prefix {
                    Text(prefix).font(Typo.titleSmall).foregroundStyle(Color.ink)
                    Rectangle()
                        .fill(Color.ink.opacity(0.14))
                        .frame(width: 1, height: 20)
                }
                textField
                    .font(Typo.jost(fieldTextSize))
                    .foregroundStyle(Color.ink)
                    .tint(Color.oliveWood)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(autocapitalization)
                    .autocorrectionDisabled()
                    .disabled(!enabled)
                trailing
            }
            .padding(.horizontal, 15)
            .frame(height: height)
            .background(RoundedRectangle(cornerRadius: fieldCorner).fill(outlined ? Color.bone : Color.stone))
            .overlay(
                RoundedRectangle(cornerRadius: fieldCorner)
                    .strokeBorder(
                        error != nil ? Color.errorRed : Color.oliveWood,
                        lineWidth: 1.5
                    )
                    .opacity(outlined || error != nil ? 1 : 0)
            )
            FieldMessageRow(error: error, helper: helper)
        }
    }

    @ViewBuilder
    private var textField: some View {
        if let focus {
            TextField(placeholder ?? "", text: $text).focused(focus)
        } else {
            TextField(placeholder ?? "", text: $text)
        }
    }
}

/// Password input: outlined in Olive Wood with an inline show/hide toggle.
struct BorderedPasswordField: View {
    let label: String
    @Binding var text: String
    var error: String?
    var helper: String?
    var height: CGFloat = fieldHeight

    @State private var visible = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            FieldLabel(text: label)
            HStack(spacing: 10) {
                Group {
                    if visible {
                        TextField("", text: $text)
                    } else {
                        SecureField("", text: $text)
                    }
                }
                .font(Typo.jost(fieldTextSize))
                .kerning(visible ? 0 : 3)
                .foregroundStyle(Color.ink)
                .tint(Color.oliveWood)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                Button {
                    visible.toggle()
                } label: {
                    Text(visible ? String(localized: "ds_hide_password_short") : String(localized: "ds_show_password_short"))
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.oliveWood)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 15)
            .frame(height: height)
            .background(RoundedRectangle(cornerRadius: fieldCorner).fill(Color.bone))
            .overlay(
                RoundedRectangle(cornerRadius: fieldCorner)
                    .strokeBorder(error != nil ? Color.errorRed : Color.oliveWood, lineWidth: 1.5)
            )
            FieldMessageRow(error: error, helper: helper)
        }
    }
}

/// Label above a tappable value: the date/time tiles used across the editors.
/// Takes the whole width it is offered, so tiles in a row come out equal.
struct PickerTile: View {
    let label: String
    let value: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                Text(label.uppercased())
                    .font(Typo.jost(9, weight: .medium))
                    .kerning(0.9)
                    .foregroundStyle(Color.textMuted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(value)
                    .font(Typo.jost(15, weight: .medium))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
        }
        .buttonStyle(.plain)
    }
}
