package com.devora.mencare.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
    Font(
        resId = resId,
        weight = weight,
        style = style,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/**
 * Cormorant Garamond — display and headlines.
 *
 * È un serif ad alto contrasto: sotto il Bold le aste sottili si assottigliano
 * fino a sparire sugli schermi piccoli, per questo i titoli partono da Bold e
 * non da SemiBold.
 */
val Cormorant = FontFamily(
    variable(R.font.cormorant_garamond, FontWeight.Normal),
    variable(R.font.cormorant_garamond, FontWeight.Medium),
    variable(R.font.cormorant_garamond, FontWeight.SemiBold),
    variable(R.font.cormorant_garamond, FontWeight.Bold),
)

/** Jost — body and UI. */
val Jost = FontFamily(
    variable(R.font.jost, FontWeight.Light),
    variable(R.font.jost, FontWeight.Normal),
    variable(R.font.jost, FontWeight.Medium),
    variable(R.font.jost, FontWeight.SemiBold),
    variable(R.font.jost, FontWeight.Bold),
    variable(R.font.jost_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Scala tipografica.
 *
 * Due regole, e valgono per tutta l'app:
 *
 * 1. **Pesi.** Jost è un geometric sans dalle aste sottili: a 400 su fondo
 *    chiaro sparisce. Il corpo parte da Medium (500), titoli ed etichette da
 *    SemiBold (600), i display serif da Bold (700). Niente è più Regular.
 * 2. **Pavimento.** Nessuno stile scende sotto gli 11.sp, e sotto i 12.sp ci
 *    vanno solo etichette maiuscole brevi — mai una frase che l'utente deve
 *    leggere.
 *
 * Chi ha bisogno di una misura che qui non c'è aggiunge un ruolo, non un
 * `copy(fontSize = …)` sulla riga: le eccezioni sparse sono il motivo per cui
 * la stessa didascalia usciva a 9, 10, 11 e 12.sp in quattro schermate.
 */
val MenCareTypography = Typography(
    displayLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Jost, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Jost, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Jost, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Jost, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.02.em),
    labelMedium = TextStyle(fontFamily = Jost, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
    labelSmall = TextStyle(fontFamily = Jost, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.08.em),
)

/**
 * Etichetta maiuscola di sezione — un ruolo solo, invece dei 9/10/11.sp
 * spaziati a mano che giravano per le schermate.
 */
val Overline = TextStyle(
    fontFamily = Jost,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.14.em,
)

/**
 * Didascalie, orari della griglia, marche temporali: il gradino sotto
 * `bodySmall`, ma con lo stesso corpo — si distingue per peso e colore, non
 * rimpicciolendo ancora.
 */
val Meta = TextStyle(
    fontFamily = Jost,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/**
 * Numeri che devono restare in colonna: cifre a larghezza fissa, così i prezzi
 * e gli orari incolonnati non ballano da una riga all'altra.
 */
val Numeric = TextStyle(
    fontFamily = Jost,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum",
)
