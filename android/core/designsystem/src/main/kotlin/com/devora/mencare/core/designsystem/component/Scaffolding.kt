package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OnDarkMuted

/**
 * Larghezza massima del contenuto. Sui tablet le schermate pensate per il
 * telefono restano centrate a questa larghezza, mentre bande scure, barre e
 * sfondi vanno a tutto schermo. È la stessa larghezza massima della
 * ModalBottomSheet Material; sui telefoni non interviene mai.
 */
val ReadableMaxWidth = 640.dp

/** Tiene l'elemento entro [ReadableMaxWidth], centrato nello spazio disponibile. */
fun Modifier.readableWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = ReadableMaxWidth)
    .fillMaxWidth()

/**
 * Signature layout element: full-bleed near-black band under the status bar,
 * light body below. La banda va a tutta larghezza, il contenuto resta entro
 * [ReadableMaxWidth] — tranne con [fullWidthContent], per le schermate che
 * usano tutto lo schermo (l'agenda settimanale).
 */
@Composable
fun DarkHeader(
    modifier: Modifier = Modifier,
    roundedBottom: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    fullWidthContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                if (roundedBottom) {
                    RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
                } else {
                    RectangleShape
                },
            )
            .background(Ink)
            .padding(WindowInsets.statusBars.asPaddingValues()),
    ) {
        Column(
            modifier = (if (fullWidthContent) Modifier.fillMaxWidth() else Modifier.readableWidth())
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun DarkHeaderTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Bone)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
