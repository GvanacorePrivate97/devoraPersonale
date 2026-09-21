package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.common.DURATION_STEP_MINUTES
import com.devora.mencare.core.common.PasswordStrength
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatPhone
import com.devora.mencare.core.common.normalizePhone
import com.devora.mencare.core.common.passwordStrength
import com.devora.mencare.core.common.sanitizePriceInput
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Stone

/**
 * L'unica famiglia di campi del prodotto: ogni form dell'app passa di qui, così
 * un concetto (telefono, email, password, prezzo, durata) si comporta e si
 * lamenta allo stesso modo ovunque. Le regole stanno in `:core:common`
 * (`Validation.kt`), la copy in `strings.xml` di questo modulo.
 */

/** Rende visibile la normalizzazione appena il campo perde il fuoco. */
@Composable
private fun Modifier.normalizeOnBlur(value: String, onNormalized: (String) -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    return onFocusChanged { state ->
        if (hadFocus && !state.hasFocus) onNormalized(value)
        hadFocus = state.hasFocus
    }
}

/** Nome o cognome di una persona: iniziali maiuscole, trim all'uscita dal campo. */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: ValidationError? = null,
    errorText: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    height: Dp = FieldHeight,
) {
    FilledTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.normalizeOnBlur(value) { raw ->
            val trimmed = raw.trim()
            if (trimmed != raw) onValueChange(trimmed)
        },
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        error = errorText ?: validationMessageOrNull(error),
        enabled = enabled,
        height = height,
    )
}

/** Email: tastiera dedicata, niente maiuscola automatica, trim + minuscole all'uscita. */
@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: ValidationError? = null,
    errorText: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = FieldHeight,
    trailing: @Composable (() -> Unit)? = null,
) {
    FilledTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.normalizeOnBlur(value) { raw ->
            val normalized = raw.trim().lowercase()
            if (normalized != raw) onValueChange(normalized)
        },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
        ),
        error = errorText ?: validationMessageOrNull(error),
        enabled = enabled,
        height = height,
        trailing = trailing,
    )
}

/**
 * Telefono: tastiera numerica, prefisso `+39` finché il numero non ne porta uno
 * suo, e normalizzazione in E.164 quando il campo perde il fuoco — il valore
 * torna al chiamante già nella forma che il backend accetta.
 */
@Composable
fun PhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: ValidationError? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    height: Dp = FieldHeight,
    trailing: @Composable (() -> Unit)? = null,
) {
    val typed = value.trimStart()
    val hasOwnPrefix = typed.startsWith("+") || typed.startsWith("00")
    FilledTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.normalizeOnBlur(value) { raw ->
            val pretty = normalizePhone(raw)?.let(::formatPhone)
            if (pretty != null && pretty != raw) onValueChange(pretty)
        },
        placeholder = stringResource(R.string.ds_phone_placeholder),
        prefix = if (hasOwnPrefix) null else stringResource(R.string.ds_phone_prefix),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        error = errorText ?: validationMessageOrNull(error),
        enabled = enabled,
        height = height,
        trailing = trailing,
    )
}

/**
 * Password: campo bordato con il toggle mostra/nascondi e, quando serve, il
 * misuratore di sicurezza calcolato dalla stessa funzione del backend.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: ValidationError? = null,
    errorText: String? = null,
    showStrength: Boolean = false,
    height: Dp = FieldHeight,
) {
    Column(modifier.fillMaxWidth()) {
        BorderedPasswordField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            error = errorText ?: validationMessageOrNull(error),
            height = height,
        )
        if (showStrength && value.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PasswordStrengthMeter(passwordStrength(value))
        }
    }
}

@Composable
fun PasswordStrengthMeter(strength: PasswordStrength, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SegmentedMeter(
            filled = when (strength) {
                PasswordStrength.DEBOLE -> 1
                PasswordStrength.MEDIA -> 2
                PasswordStrength.FORTE -> 3
            },
            total = 4,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            passwordStrengthLabel(strength),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Ink,
        )
    }
}

/**
 * Prezzo in euro: tastiera decimale e filtro che lascia passare solo ciò che
 * può diventare un prezzo. La conversione in centesimi (arrotondata, mai
 * troncata) resta in `parsePriceEuros`.
 */
@Composable
fun PriceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: ValidationError? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    height: Dp = FieldHeight,
) {
    FilledTextField(
        value = value,
        onValueChange = { onValueChange(sanitizePriceInput(it)) },
        label = label,
        modifier = modifier,
        prefix = stringResource(R.string.ds_price_prefix),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        error = errorText ?: validationMessageOrNull(error),
        enabled = enabled,
        height = height,
    )
}

/** Durata di un servizio: passi di 5 minuti, come la griglia degli slot. */
@Composable
fun DurationField(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    error: ValidationError? = null,
    step: Int = DURATION_STEP_MINUTES,
    height: Dp = 56.dp,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(FieldShape)
                .background(Stone)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                formatDuration(minutes),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            StepperButton(
                icon = Icons.Outlined.Remove,
                contentDescription = stringResource(R.string.ds_duration_less),
                onClick = { onMinutesChange(minutes - step) },
            )
            StepperButton(
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.ds_duration_more),
                onClick = { onMinutesChange(minutes + step) },
            )
        }
        FieldMessage(validationMessageOrNull(error))
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Bone)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Ink, modifier = Modifier.size(15.dp))
    }
}
