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
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveTint
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.core.designsystem.theme.Overline
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

/** Pill segmented control — "Prossimi · 2 / Passati · 14" and friends. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
    selectedContainer: Color = OliveWood,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (onDark) Bone.copy(alpha = 0.1f) else Stone)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) selectedContainer else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        selected -> Bone
                        onDark -> Bone
                        else -> Ink
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Standalone selectable chip used for filters, categories and quick choices.
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
        selected -> OliveWood
        onDark -> Bone.copy(alpha = 0.1f)
        else -> Stone
    }
    Box(
        modifier = modifier
            .then(if (fill) Modifier.height(if (maxLines > 1) 58.dp else 42.dp) else Modifier)
            .clip(RoundedCornerShape(11.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = if (fill) 6.dp else 16.dp, vertical = if (fill) 0.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = MaterialTheme.typography.titleSmall
        Text(
            text,
            style = style,
            color = if (selected || onDark) Bone else Ink,
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

/** Outlined variant used for highlighted panels (staff notes, conflict warnings). */
@Composable
fun AccentOutlinedCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Bone)
            .border(1.5.dp, OliveWood, RoundedCornerShape(16.dp)),
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
            .clip(RoundedCornerShape(15.dp))
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
            .clip(RoundedCornerShape(7.dp))
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
                    .clip(RoundedCornerShape(11.dp))
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

/** Dark running-total footer with an olive call to action (booking steps 2 and 3). */
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
                    .clip(RoundedCornerShape(15.dp))
                    .background(OliveWood)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ctaLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Bone,
                )
                Spacer(Modifier.width(9.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Bone,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** One choice of a [MultiSelectDropdown]. */
data class DropdownOption(val id: String, val label: String)

/**
 * Colori della barra di navigazione, uno per tutti e tre i ruoli.
 *
 * Erano tre copie identiche in `ClientRoot`, `StaffRoot` e `AdminRoot`: identiche
 * finché qualcuno non ne toccava una. La navigazione di cliente, operatore e
 * titolare deve avere lo stesso aspetto, quindi ha un posto solo da cui prenderlo.
 */
@Composable
fun brandNavBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = OliveWood,
    selectedTextColor = OliveWood,
    unselectedIconColor = Ink,
    unselectedTextColor = TextMuted,
    indicatorColor = OliveTint,
)
