package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.OliveWood

/**
 * Come si racconta un guasto di lettura.
 *
 * Finché i dati erano finti non poteva fallire niente e nessuna schermata
 * aveva un modo per dirlo. Con il backend può fallire tutto, e la risposta
 * giusta è quasi sempre la stessa: una frase in italiano, senza gergo, e un
 * bottone per riprovare. Il dettaglio tecnico non serve a chi taglia i capelli.
 */
@Composable
fun errorMessage(error: AppError): String = when (error) {
    // `Unknown` copre due cose diverse: la rete che non c'è (nessun messaggio,
    // l'errore è di trasporto) e un rifiuto del server che un messaggio ce
    // l'ha. Sono due consigli diversi: "controlla la connessione" o "riprova".
    is AppError.Unknown ->
        if (error.message == null) stringResource(R.string.ds_error_connection) else stringResource(R.string.ds_error_generic)
    AppError.NotFound -> stringResource(R.string.ds_error_not_found)
    AppError.SlotNoLongerAvailable -> stringResource(R.string.ds_error_slot_taken)
    AppError.InvalidCredentials, AppError.EmailAlreadyRegistered -> stringResource(R.string.ds_error_generic)
    is AppError.Validation -> stringResource(R.string.ds_error_generic)
}

/**
 * Schermata (o sezione) che non è riuscita a caricare. [onRetry] assente
 * quando non c'è niente da riprovare e resta solo il messaggio.
 */
@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = OliveWood,
            modifier = Modifier.size(32.dp),
        )
        Text(
            stringResource(R.string.ds_error_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (onRetry != null) {
            SecondaryButton(
                text = stringResource(R.string.ds_retry),
                onClick = onRetry,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/**
 * Riga sottile sopra a dei dati già visibili: la lettura è fallita ma quello
 * che c'era resta a schermo, invece di sparire per un timeout.
 */
@Composable
fun InlineErrorBanner(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = OliveWood,
            modifier = Modifier.size(18.dp),
        )
        Text(
            errorMessage(error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            LinkButton(text = stringResource(R.string.ds_retry), onClick = onRetry)
        }
    }
}

/** Attesa della prima risposta: una rotella centrata, senza testo. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = OliveWood, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}
