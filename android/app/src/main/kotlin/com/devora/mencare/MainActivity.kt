package com.devora.mencare

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.devora.mencare.core.designsystem.theme.MenCareTheme
import dagger.hilt.android.AndroidEntryPoint

/** Lato corto minimo di un tablet, la soglia Android delle finestre "medium". */
private const val TABLET_MIN_WIDTH_DP = 600

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Every screen opens on a near-black band, and the bottom bar is light:
        // pin the system bars to that contrast instead of following the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Telefoni solo in verticale, come su iPhone; i tablet ruotano liberamente
        // e il contenuto resta entro ReadableMaxWidth.
        if (resources.configuration.smallestScreenWidthDp < TABLET_MIN_WIDTH_DP) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        super.onCreate(savedInstanceState)
        setContent {
            MenCareTheme {
                RootNavigation()
            }
        }
    }
}
