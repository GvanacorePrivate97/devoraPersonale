package com.devora.mencare.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.common.formatDateLong
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Meta
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Overline
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Navigazione del giorno in agenda — una sola, per il titolare e per
 * l'operatore.
 *
 * Le due agende navigavano il tempo in due modi diversi: il titolare con due
 * frecce nell'intestazione, l'operatore con una striscia di giorni, e da
 * nessuna delle due si tornava a oggi se non contando i tap all'indietro.
 * Questa barra è il pattern unico: frecce, data, striscia, e **"Oggi"**, che
 * compare solo quando serve — cioè solo quando il giorno scelto non è oggi.
 *
 * Vive sulla banda scura, quindi l'accento è [OliveLight], l'oro del marchio.
 */
@Composable
fun AgendaDayBar(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    daysVisible: Int = 6,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayArrow(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.ds_agenda_previous_day),
                onClick = { onSelect(selected.minusDays(1)) },
            )
            Text(
                formatDateLong(selected).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                color = Bone,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            DayArrow(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.ds_agenda_next_day),
                onClick = { onSelect(selected.plusDays(1)) },
            )
            // "Oggi" non occupa spazio quando siamo già su oggi: il posto se lo
            // prende, e lo restituisce, senza far saltare la riga.
            AnimatedVisibility(
                visible = selected != today,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    TodayButton(onClick = { onSelect(today) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        DayStrip(
            selected = selected,
            today = today,
            daysVisible = daysVisible,
            onSelect = onSelect,
        )
    }
}

/** Il bottone "Oggi": pill d'accento, alta 34.dp — sopra il minimo tattile con il padding della riga. */
@Composable
private fun TodayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OliveWood)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.ds_agenda_today).uppercase(),
            style = Overline,
            color = Bone,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayArrow(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Bone.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Bone, modifier = Modifier.size(20.dp))
    }
}

/**
 * La striscia dei giorni. Oggi porta sempre il punto d'oro sotto il numero,
 * anche quando è il giorno selezionato: così "dove sono" e "dov'è oggi" restano
 * due informazioni distinte invece di annullarsi a vicenda.
 */
@Composable
private fun DayStrip(
    selected: LocalDate,
    today: LocalDate,
    daysVisible: Int,
    onSelect: (LocalDate) -> Unit,
) {
    // La finestra tiene il giorno scelto al centro, mai sul bordo.
    val start = selected.minusDays((daysVisible / 2).toLong())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (offset in 0 until daysVisible) {
            val day = start.plusDays(offset.toLong())
            val isSelected = day == selected
            val isToday = day == today
            val dayName = day.dayOfWeek
                .getDisplayName(JavaTextStyle.SHORT, Locale.ITALIAN)
                .uppercase()
                .take(3)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) OliveWood else Color.Transparent)
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(1.dp, OliveLight, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(day) }
                    .padding(vertical = 8.dp)
                    // Una casella, una lettura: "lun 20, oggi" invece di "LUN" e "20" staccati.
                    .clearAndSetSemantics {
                        contentDescription = buildString {
                            append(dayName)
                            append(' ')
                            append(day.dayOfMonth)
                            if (isToday) append(", oggi")
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dayName,
                    style = Overline,
                    color = if (isSelected) Bone else OliveLight,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${day.dayOfMonth}",
                    style = Meta,
                    color = Bone,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isToday) OliveLight else Color.Transparent),
                )
            }
        }
    }
}
