package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted

internal val FieldShape = RoundedCornerShape(14.dp)
internal val FieldHeight = 52.dp

/** Uppercase letter-spaced label sitting above an input, as in the design mockup. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.14.em),
        color = Ink,
        modifier = modifier,
    )
}

private val FieldTextSize = 15.sp

/** Filled input on the Stone surface — the resting state of every text field in the app. */
@Composable
fun FilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    prefix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    error: String? = null,
    enabled: Boolean = true,
    height: Dp = FieldHeight,
    outlined: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            FieldLabel(label)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = FieldTextSize),
            cursorBrush = SolidColor(OliveWood),
            modifier = Modifier.fillMaxWidth().height(height),
        ) { field ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(FieldShape)
                    .background(if (outlined) Bone else Stone)
                    .then(
                        if (outlined) Modifier.border(1.5.dp, OliveWood, FieldShape) else Modifier,
                    )
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = Ink.copy(alpha = 0.45f),
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                if (prefix != null) {
                    Text(
                        prefix,
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Spacer(Modifier.width(11.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Ink.copy(alpha = 0.14f)),
                    )
                    Spacer(Modifier.width(11.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = FieldTextSize),
                            color = Ink.copy(alpha = 0.4f),
                        )
                    }
                    field()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(10.dp))
                    trailing()
                }
            }
        }
        FieldMessage(error)
    }
}

/** Password input: outlined in Olive Wood with an inline show/hide toggle. */
@Composable
fun BorderedPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    height: Dp = FieldHeight,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Ink,
                fontSize = FieldTextSize,
                letterSpacing = if (visible) 0.em else 0.26.em,
            ),
            cursorBrush = SolidColor(OliveWood),
            modifier = Modifier.fillMaxWidth().height(height),
        ) { field ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(FieldShape)
                    .background(Bone)
                    .border(1.5.dp, if (error != null) ErrorRed else OliveWood, FieldShape)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { field() }
                val toggleLabel = stringResource(
                    if (visible) R.string.ds_hide_password else R.string.ds_show_password,
                )
                Text(
                    stringResource(
                        if (visible) R.string.ds_hide_password_short else R.string.ds_show_password_short,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = OliveWood,
                    modifier = Modifier
                        .clickable(onClickLabel = toggleLabel) { visible = !visible }
                        .padding(start = 10.dp),
                )
            }
        }
        FieldMessage(error)
    }
}

/**
 * Riga sotto un campo: l'errore in rosso, altrimenti l'aiuto in grigio.
 * È l'unico posto in cui si scrive un messaggio sotto a un input.
 */
@Composable
fun FieldMessage(
    error: String?,
    modifier: Modifier = Modifier,
    helper: String? = null,
) {
    val text = error ?: helper ?: return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null) ErrorRed else TextMuted,
        modifier = modifier.padding(top = 6.dp),
    )
}
