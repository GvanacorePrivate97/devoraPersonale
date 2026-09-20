package com.devora.mencare.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.devora.mencare.core.common.DURATION_MIN_MINUTES
import com.devora.mencare.core.common.DURATION_STEP_MINUTES
import com.devora.mencare.core.common.NAME_MAX
import com.devora.mencare.core.common.NAME_MIN
import com.devora.mencare.core.common.NOTE_MAX
import com.devora.mencare.core.common.PASSWORD_MAX
import com.devora.mencare.core.common.PASSWORD_MIN
import com.devora.mencare.core.common.PasswordStrength
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.designsystem.R

/**
 * Unico punto in cui un [ValidationError] di `:core:common` diventa testo.
 * La copy è identica a quella del backend: i numeri arrivano dalle costanti
 * condivise, così limite e messaggio non possono divergere.
 */
@Composable
fun validationMessage(error: ValidationError): String = when (error) {
    ValidationError.REQUIRED -> stringResource(R.string.validation_required)
    ValidationError.NAME_LENGTH -> stringResource(R.string.validation_name_length, NAME_MIN, NAME_MAX)
    ValidationError.NAME_CHARS -> stringResource(R.string.validation_name_chars)
    ValidationError.EMAIL_INVALID -> stringResource(R.string.validation_email)
    ValidationError.PHONE_INVALID -> stringResource(R.string.validation_phone)
    ValidationError.PASSWORD_TOO_SHORT -> stringResource(R.string.validation_password_short, PASSWORD_MIN)
    ValidationError.PASSWORD_TOO_LONG -> stringResource(R.string.validation_password_long, PASSWORD_MAX)
    ValidationError.PASSWORD_NO_LETTER -> stringResource(R.string.validation_password_letter)
    ValidationError.PASSWORD_NO_DIGIT -> stringResource(R.string.validation_password_digit)
    ValidationError.PASSWORD_MISMATCH -> stringResource(R.string.validation_password_match)
    ValidationError.TEXT_TOO_LONG -> stringResource(R.string.validation_text_long)
    ValidationError.NOTE_TOO_LONG -> stringResource(R.string.validation_note_long, NOTE_MAX)
    ValidationError.DURATION_TOO_SHORT -> stringResource(R.string.validation_duration_short, DURATION_MIN_MINUTES)
    ValidationError.DURATION_TOO_LONG -> stringResource(R.string.validation_duration_long)
    ValidationError.DURATION_STEP -> stringResource(R.string.validation_duration_step, DURATION_STEP_MINUTES)
    ValidationError.PRICE_INVALID -> stringResource(R.string.validation_price_invalid)
    ValidationError.PRICE_NEGATIVE -> stringResource(R.string.validation_price_negative)
    ValidationError.PRICE_TOO_HIGH -> stringResource(R.string.validation_price_high)
}

/** Comodo lato form: `null` quando il campo è a posto. */
@Composable
fun validationMessageOrNull(error: ValidationError?): String? = error?.let { validationMessage(it) }

@Composable
fun passwordStrengthLabel(strength: PasswordStrength): String = stringResource(
    when (strength) {
        PasswordStrength.DEBOLE -> R.string.ds_password_strength_weak
        PasswordStrength.MEDIA -> R.string.ds_password_strength_medium
        PasswordStrength.FORTE -> R.string.ds_password_strength_strong
    },
)
