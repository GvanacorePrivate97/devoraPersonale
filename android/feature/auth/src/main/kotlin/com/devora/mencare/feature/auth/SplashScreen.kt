package com.devora.mencare.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(progress, animationSpec = tween(1400), label = "splash")

    LaunchedEffect(Unit) {
        progress = 1f
        delay(1600)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(com.devora.mencare.core.designsystem.R.drawable.logo_mark),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Text(
                stringResource(R.string.auth_brand_name),
                style = MaterialTheme.typography.displayMedium,
                color = Bone,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                stringResource(R.string.auth_brand_claim).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = OliveWood,
                modifier = Modifier.padding(top = 4.dp),
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.padding(top = 40.dp).fillMaxWidth(0.4f),
                color = OliveWood,
                trackColor = Ink.copy(alpha = 0.4f),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                stringResource(R.string.auth_powered_by),
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
        }
    }
}
