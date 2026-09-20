package com.devora.mencare.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Scala dei raggi — sei valori, non venti.
 *
 * Le schermate giravano con una ventina di raggi diversi scritti a mano (6, 7,
 * 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 20, 22, 24, 28…): due card vicine non
 * avevano mai lo stesso angolo. Qui ce n'è uno per ogni ruolo, e chi disegna
 * sceglie il ruolo, non il numero.
 */
object Radii {
    /** Micro-elementi: barre di avanzamento, tacche, indicatori. */
    val Xs = RoundedCornerShape(6.dp)

    /** Controlli piccoli: chip compatti, quadratini, riquadri d'icona. */
    val Sm = RoundedCornerShape(10.dp)

    /** Il raggio di serie: bottoni, campi, card, tessere. */
    val Md = RoundedCornerShape(16.dp)

    /** Contenitori grandi: card scure in evidenza, fogli, bottom sheet. */
    val Lg = RoundedCornerShape(22.dp)

    /** Bande e fogli a tutta larghezza: la testa scura, il foglio chiaro. */
    val Xl = RoundedCornerShape(28.dp)

    /** Pillole: barra di navigazione, tab, badge di stato. */
    val Pill = RoundedCornerShape(999.dp)
}
