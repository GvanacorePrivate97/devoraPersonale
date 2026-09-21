package com.devora.mencare.core.ui.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatPriceCompact
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.StatTile
import com.devora.mencare.core.designsystem.component.StoneKeyValueRow
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.InkBorder
import com.devora.mencare.core.designsystem.theme.InkRaised
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.util.dialPhone
import com.devora.mencare.core.ui.R

/** [showEconomics]: spesa totale e importi delle visite, solo il titolare vede le cifre. */
@Composable
fun CrmDetailScreen(
    showEconomics: Boolean,
    onBack: () -> Unit,
    onNewBooking: () -> Unit,
    viewModel: CrmDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val client = state.client ?: return

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 18.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.crm_detail_title),
                onBack = onBack,
                backLabel = stringResource(R.string.crm_title),
                trailing = { BarAction(stringResource(R.string.crm_edit), onBack, color = OliveLight) },
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sulla banda scura l'iniziale è oro su un tondo appena
                // sollevato: il quadrato d'oliva era l'unico posto in cui
                // l'accento chiaro finiva sul nero.
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(InkRaised)
                        .border(1.dp, InkBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${client.firstName.first()}${client.lastName.first()}",
                        fontFamily = Cormorant,
                        fontSize = 22.sp,
                        color = OliveLight,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(
                        "${client.firstName} ${client.lastName}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 25.sp),
                        color = Bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${client.phone} · ${stringResource(R.string.crm_since, client.customerSince.year)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Bone,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    label = stringResource(R.string.crm_stats_visits),
                    value = "${client.visitCount}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    container = Bone.copy(alpha = 0.08f),
                    contentColor = Bone,
                )
                if (showEconomics) {
                    StatTile(
                        label = stringResource(R.string.crm_stats_spend),
                        value = formatPriceCompact(client.lifetimeSpendCents),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        container = Bone.copy(alpha = 0.08f),
                        contentColor = Bone,
                    )
                }
                StatTile(
                    label = stringResource(R.string.crm_stats_noshow),
                    value = "${client.noShowCount}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    container = Bone.copy(alpha = 0.08f),
                    contentColor = Bone,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 16.dp),
        ) {
            // Abitudini calcolate dal server: con chi viene più spesso e ogni
            // quanto torna. Compaiono solo quando c'è qualcosa da dire.
            val favorite = client.favoriteOperatorId?.let { state.operators[it]?.name }
            val cadence = client.averageDaysBetweenVisits
            if (favorite != null || cadence != null) {
                BrandSectionLabel(stringResource(R.string.crm_habits))
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radii.Md)
                        .background(Bone)
                        .border(1.5.dp, StoneBorder, Radii.Md),
                ) {
                    if (favorite != null) {
                        StoneKeyValueRow(stringResource(R.string.crm_favorite_operator), favorite)
                    }
                    if (favorite != null && cadence != null) {
                        HorizontalDivider(color = StoneBorder)
                    }
                    if (cadence != null) {
                        StoneKeyValueRow(
                            stringResource(R.string.crm_cadence),
                            stringResource(R.string.crm_cadence_value, cadence),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            BrandSectionLabel(stringResource(R.string.crm_history))
            Spacer(Modifier.height(10.dp))
            state.history.forEach { appointment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(OliveWood))
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text(
                            "${formatDateShort(appointment.date)} · " +
                                appointment.serviceIds
                                    .mapNotNull { state.services[it]?.name }
                                    .joinToString(" + "),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            color = Bone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.operators[appointment.operatorId]?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = OnDarkMuted,
                        )
                    }
                    if (showEconomics) {
                        Text(
                            formatPriceCompact(appointment.totalPriceCents),
                            style = MaterialTheme.typography.titleSmall,
                            color = OliveWood,
                        )
                    }
                }
            }
        }

        // Barra fissa in fondo: fondo pieno, filo e ombra sopra, così le azioni
        // restano sopra lo storico che scorre invece di confondersi con le card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp)
                .background(Bone),
        ) {
            HorizontalDivider(color = StoneBorder)
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .readableWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // "Chiama" apre il tastierino con il numero del cliente.
                val context = LocalContext.current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(Radii.Md)
                        .background(Bone)
                        .border(1.5.dp, StoneBorder, Radii.Md)
                        .clickable(enabled = client.phone.isNotBlank()) { context.dialPhone(client.phone) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.crm_call),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                }
                AccentButton(
                    text = stringResource(R.string.crm_new_booking),
                    onClick = onNewBooking,
                    height = 54.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
