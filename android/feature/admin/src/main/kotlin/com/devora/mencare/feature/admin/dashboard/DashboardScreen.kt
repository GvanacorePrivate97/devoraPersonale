package com.devora.mencare.feature.admin.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatPriceCompact
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.SegmentedTabs
import com.devora.mencare.core.designsystem.component.StatTile
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Overline
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.StoneSoft
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.DashboardPeriod
import com.devora.mencare.core.model.UpcomingDay
import com.devora.mencare.feature.admin.R

@Composable
fun DashboardScreen(
    onSendCampaign: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    Column(modifier = Modifier.fillMaxSize().background(Bone).verticalScroll(rememberScrollState())) {
        DarkHeader(
            roundedBottom = true,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
        ) {
            // La banda porta il suo titolo, come le altre schermate del
            // titolare: prima si sa dove si è, poi si sceglie il periodo.
            Text(
                stringResource(R.string.dash_overline).uppercase(),
                style = Overline,
                color = OliveLight,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.dash_title),
                style = MaterialTheme.typography.displaySmall,
                color = Bone,
            )
            Spacer(Modifier.height(16.dp))
            SegmentedTabs(
                options = listOf(
                    stringResource(R.string.dash_period_day),
                    stringResource(R.string.dash_period_week),
                    stringResource(R.string.dash_period_month),
                ),
                selectedIndex = state.period.ordinal,
                onSelect = { viewModel.setPeriod(DashboardPeriod.entries[it]) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    when (state.period) {
                        DashboardPeriod.DAY -> R.string.dash_revenue_label_day
                        DashboardPeriod.WEEK -> R.string.dash_revenue_label_week
                        DashboardPeriod.MONTH -> R.string.dash_revenue_label
                    },
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.16.em),
                color = OliveWood,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatPriceCompact(stats?.revenueCents ?: 0),
                    fontFamily = Cormorant,
                    fontSize = 44.sp,
                    color = Bone,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.dash_trend, stats?.revenueTrendPercent ?: 0),
                    style = MaterialTheme.typography.titleSmall,
                    color = Bone,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(OliveWood)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            RevenueBars()
        }

        Column(
            modifier = Modifier.readableWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // I tre KPI hanno lo stesso aspetto: fondo chiaro e filo, niente grigio.
                StatTile(
                    label = stringResource(R.string.dash_appointments),
                    value = "${stats?.appointmentCount ?: 0}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    container = Bone,
                    borderColor = StoneBorder,
                )
                StatTile(
                    label = stringResource(R.string.dash_no_show),
                    value = String.format(java.util.Locale.ITALIAN, "%.1f%%", stats?.noShowPercent ?: 0.0),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    container = Bone,
                    borderColor = StoneBorder,
                )
                StatTile(
                    label = stringResource(R.string.dash_avg_ticket),
                    value = formatPriceCompact(stats?.averageTicketCents ?: 0),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    container = Bone,
                    borderColor = StoneBorder,
                )
            }

            Column {
                BrandSectionLabel(stringResource(R.string.dash_operator_occupancy))
                Spacer(Modifier.height(9.dp))
                stats?.operatorOccupancy?.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.operatorName.substringBefore(' '),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            modifier = Modifier.width(64.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Stone),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(row.percent / 100f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (row.percent >= 80) OliveWood else Ink),
                            )
                        }
                        Text(
                            "${row.percent}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                            modifier = Modifier.width(44.dp).padding(start = 10.dp),
                        )
                    }
                }
            }

            UpcomingSection(stats?.upcomingDays.orEmpty(), onSendCampaign)
        }
    }
}

/** Period-over-period revenue sketch; the demo layer ships no per-day series yet. */
@Composable
private fun RevenueBars() {
    val heights = listOf(0.42f, 0.55f, 0.48f, 0.72f, 0.5f, 1f, 0.6f, 0.38f)
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEachIndexed { index, fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (index == 5) OliveWood else Bone.copy(alpha = 0.12f)),
            )
        }
    }
}

/** Sotto questa soglia un giorno aperto è "vuoto": è lì che una campagna serve. */
private const val EMPTY_DAY_THRESHOLD = 50

/**
 * I prossimi 7 giorni: una colonna per giorno con l'occupazione, i giorni
 * chiusi segnati, chi aspetta in lista ("Avvisami") e, se c'è un giorno vuoto,
 * la scorciatoia per riempirlo con una campagna.
 */
@Composable
private fun UpcomingSection(days: List<UpcomingDay>, onSendCampaign: () -> Unit) {
    val waiting = days.sumOf { it.waitlistCount }
    val hasEmptyDay = days.any { !it.closed && it.occupancyPercent < EMPTY_DAY_THRESHOLD }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandSectionLabel(stringResource(R.string.dash_upcoming))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { day -> UpcomingColumn(day, Modifier.weight(1f)) }
        }
        if (waiting > 0) {
            Text(
                stringResource(R.string.dash_upcoming_waitlist, waiting),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
            )
        }
        // L'unico ingresso alla campagna push: sempre presente, e dice "riempi"
        // quando c'è davvero un giorno vuoto.
        AccentButton(
            text = stringResource(if (hasEmptyDay) R.string.dash_upcoming_fill else R.string.dash_send_campaign),
            onClick = onSendCampaign,
            height = 52.dp,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = Icons.Outlined.NotificationsNone,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun UpcomingColumn(day: UpcomingDay, modifier: Modifier) {
    val parts = formatDateShort(day.date).split(" ")
    val barHeight = 56.dp
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Chi aspetta un posto quel giorno: la domanda che l'agenda non ha servito.
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (day.waitlistCount > 0) OliveWood else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (day.waitlistCount > 0) {
                Text(
                    "${day.waitlistCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = Bone,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(if (day.closed) StoneSoft else Stone),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (!day.closed) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight * (day.occupancyPercent / 100f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (day.occupancyPercent >= 80) OliveWood else Ink),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (day.closed) stringResource(R.string.dash_upcoming_closed) else "${day.occupancyPercent}%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
            color = if (day.closed) TextMuted else Ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            parts.getOrElse(0) { "" }.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.08.em),
            color = TextMuted,
        )
        Text(
            parts.getOrElse(1) { "" },
            fontFamily = Cormorant,
            fontSize = 16.sp,
            color = Ink,
        )
    }
}
