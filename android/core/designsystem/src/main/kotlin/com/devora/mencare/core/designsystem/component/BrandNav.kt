package com.devora.mencare.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.GoldSoft
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveTint
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.core.designsystem.theme.Overline
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted

/**
 * Navigation row for the dark band: Android back arrow where the mockup draws an
 * iOS chevron, centred title, optional trailing action.
 */
@Composable
fun BrandTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(88.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.ds_back),
                    tint = OliveLight,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 6.dp)
                        .size(20.dp),
                )
                if (backLabel != null) {
                    Text(
                        backLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = OliveLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onBack).padding(start = 4.dp),
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Bone,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.width(88.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

/** Text action sitting in a dark band (the mockup's "Annulla" / "Salva" / "Modifica"). */
@Composable
fun BarAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Bone) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
    )
}

/** Four-step progress rail of the booking wizard. */
@Composable
fun WizardSteps(labels: List<String>, currentIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val done = index <= currentIndex
            Column(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (done) OliveLight else Bone.copy(alpha = 0.16f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    label.uppercase(),
                    style = Overline,
                    color = if (done) OliveLight else OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Tab a pillola — "Prossimi · 2 / Passati · 14", il periodo della dashboard, le
 * sotto-schede di Gestione.
 *
 * Sulla banda scura la voce attiva è una pillola d'oro traslucida con il filo
 * chiaro: lo stesso vetro della barra di navigazione, così tab e navigazione si
 * leggono come un'unica famiglia invece di due controlli diversi.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
) {
    val trackShape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(if (onDark) Bone.copy(alpha = 0.08f) else Stone)
            .then(
                if (onDark) Modifier.border(1.dp, Bone.copy(alpha = 0.14f), trackShape) else Modifier,
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(shape)
                    .then(
                        when {
                            selected && onDark -> Modifier
                                .background(OliveLight.copy(alpha = 0.34f))
                                .border(1.dp, GoldSoft.copy(alpha = 0.55f), shape)
                            selected -> Modifier.background(Ink)
                            else -> Modifier
                        },
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        selected && onDark -> GoldSoft
                        selected -> Bone
                        onDark -> OnDarkMuted
                        else -> TextMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Chip selezionabile: filtri, categorie e scelte rapide.
 *
 * Scelto = fondo OliveTint con il filo d'accento e il testo d'accento; non
 * scelto = fondo Bone con il filo grigio e il testo secondario. Prima lo scelto
 * era un blocco d'oliva pieno: pesava come un pulsante di conferma e, in una
 * fila di quattro, la schermata sembrava avere quattro azioni primarie.
 *
 * `fill` is for rows of equal chips (give each `Modifier.weight(1f)`): the chip
 * gets a fixed height and the label shrinks (and, with `maxLines = 2`, wraps)
 * instead of being truncated.
 */
@Composable
fun BrandChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    fill: Boolean = false,
    maxLines: Int = 1,
) {
    val container = when {
        selected && onDark -> OliveLight.copy(alpha = 0.22f)
        selected -> OliveTint
        onDark -> Bone.copy(alpha = 0.1f)
        else -> Bone
    }
    val border = when {
        selected && onDark -> OliveLight.copy(alpha = 0.45f)
        selected -> OliveWood
        onDark -> Bone.copy(alpha = 0.14f)
        else -> StoneBorder
    }
    val content = when {
        selected && onDark -> GoldSoft
        selected -> OliveWood
        onDark -> OnDarkMuted
        else -> TextMuted
    }
    Box(
        modifier = modifier
            .then(if (fill) Modifier.height(if (maxLines > 1) 58.dp else 42.dp) else Modifier)
            .clip(Radii.Pill)
            .background(container)
            .border(1.5.dp, border, Radii.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = if (fill) 6.dp else 16.dp, vertical = if (fill) 0.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = MaterialTheme.typography.titleSmall
        Text(
            text,
            style = style,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = if (fill) TextOverflow.Clip else TextOverflow.Ellipsis,
            autoSize = if (fill) TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize) else null,
        )
    }
}

/** Rounded Stone container that most list rows and panels sit in. */
@Composable
fun StoneCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: androidx.compose.ui.unit.Dp = 18.dp,
    container: Color = Stone,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(corner))
        .background(container)
    Column(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        content = content,
    )
}

/** Uppercase section heading used above every block of content. */
@Composable
fun BrandSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink,
) {
    Text(
        text.uppercase(),
        style = Overline,
        color = color,
        modifier = modifier,
    )
}

/**
 * KPI tile: big value under a small uppercase caption, both centred. The caption
 * wraps rather than truncating; put tiles side by side in a
 * `Row(Modifier.height(IntrinsicSize.Min))` with `fillMaxHeight()` so they share a height.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = Stone,
    contentColor: Color = Ink,
    borderColor: Color? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label.uppercase(),
            style = Overline,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Olive Wood toggle. */
@Composable
fun BrandSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Bone,
            checkedTrackColor = OliveWood,
            checkedBorderColor = OliveWood,
            uncheckedThumbColor = Bone,
            uncheckedTrackColor = StoneBorder,
            uncheckedBorderColor = StoneBorder,
        ),
    )
}

/** Label + value row inside a Stone panel. */
@Composable
fun StoneKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth()
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/** Bottom action area pinned under the content, separated by a hairline. */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    container: Color = Bone,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().background(container)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(StoneBorder))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            content = content,
        )
    }
}

/** Segmented strength/progress meter (password strength, occupancy). */
@Composable
fun SegmentedMeter(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = OliveWood,
    trackColor: Color = Stone,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) color else trackColor),
            )
        }
    }
}

/** Stone row with a leading icon and a chevron — "other ways in", settings entries. */
@Composable
fun NavigationRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Stone)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = Ink, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(11.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Olive Wood checkbox with the mockup's rounded-square shape. */
@Composable
fun BrandCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) OliveWood else Stone)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = Bone,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * Barra in fondo che esiste solo quando si può andare avanti: sale dal bordo
 * inferiore quando [visible] diventa vero e riscende se la selezione si
 * svuota. Avvolge [DarkContinueBar] e [DarkTotalBar] nel wizard di prenotazione.
 */
@Composable
fun BottomBarReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        content()
    }
}

/** Light footer holding a dark "continue" block with an olive arrow button. */
@Composable
fun DarkContinueBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(Bone)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(StoneBorder))
        Row(
            modifier = Modifier
                .readableWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Ink)
                .clickable(onClick = onClick)
                .padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OliveWood),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Bone,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Barra scura col totale e l'azione che porta avanti (passi 2 e 3 del wizard).
 * L'azione è oro, non oliva: sul nero l'oliva si ferma sotto il 4.5:1 ed è la
 * regola della banda scura in tutta l'app.
 */
@Composable
fun DarkTotalBar(
    caption: String,
    value: String,
    ctaLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Ink),
    ) {
        Row(
            modifier = Modifier
                .readableWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    caption.uppercase(),
                    style = Overline,
                    color = OliveLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Bone,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .height(50.dp)
                    .clip(Radii.Md)
                    .background(OliveLight)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ctaLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Spacer(Modifier.width(9.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** One choice of a [MultiSelectDropdown]. */
data class DropdownOption(val id: String, val label: String)

/**
 * Barra di navigazione: una pillola nera, per tutti e tre i ruoli.
 *
 * Prima era la `NavigationBar` di Material su fondo chiaro, con l'indicatore
 * oliva: si confondeva con il contenuto e non c'entrava niente con le bande
 * scure che sono la firma del marchio. Adesso è un blocco nero con il filo oro,
 * e la voce attiva porta la sua pillola d'oro — lo stesso vetro delle tab, così
 * navigazione e tab si leggono come una famiglia sola.
 */
@Composable
fun BrandBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Bone)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .readableWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink)
                .border(1.dp, OliveLight.copy(alpha = 0.26f), RoundedCornerShape(999.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Una voce della [BrandBottomBar]: icona in pillola d'oro quando è attiva. */
@Composable
fun RowScope.BrandBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val pill = RoundedCornerShape(999.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(pill)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (selected) {
                        Modifier
                            .clip(pill)
                            .background(OliveLight.copy(alpha = 0.22f))
                            .border(1.dp, OliveLight.copy(alpha = 0.45f), pill)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) GoldSoft else OnDarkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) GoldSoft else OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
