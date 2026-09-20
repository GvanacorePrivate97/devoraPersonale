package com.devora.mencare.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand palette — Antonio De Vito · Men Care
//
// Ogni colore qui dentro regge almeno 4.5:1 (WCAG 2.1 AA, testo normale) sulle
// superfici su cui l'app lo usa davvero. Le due superfici chiare sono Bone e
// Stone: Stone è la più severa delle due, quindi è quella su cui sono stati
// tarati i toni di testo. Cambiare un valore vuol dire rifare il conto.
val Bone = Color(0xFFFDFDFD) // background
val Stone = Color(0xFFEBEBEA) // surface / secondary

/**
 * Accento su superficie CHIARA: testo, icone, bordi, e i riempimenti che
 * portano testo Bone. 4.70:1 su Stone, 5.51:1 su Bone, e Bone sopra di lui
 * 5.51:1 — così l'oliva funziona sia come inchiostro sia come fondo.
 */
val OliveWood = Color(0xFF77654B) // accent

/**
 * Accento su superficie SCURA (le bande near-black): è l'oro del marchio,
 * campionato dalla scritta "MEN CARE" del logo ufficiale. 8.65:1 su Ink,
 * dove l'oliva scura si fermava a 3.75:1 e leggeva come fango.
 */
val OliveLight = Color(0xFFBFA277)

/** Stato premuto dell'accento chiaro. */
val OliveWoodDark = Color(0xFF6A5A43)

/** Fondo piatto delle pill d'accento: niente alpha, così è uguale su Bone e su Stone. */
val OliveTint = Color(0xFFEFECE9)

val Ink = Color(0xFF000006) // dark bands, primary text
val InkSoft = Color(0xFF000004)
val OnDarkMuted = Color(0xFFB9B6AE) // secondary text on dark bands — 10.4:1 su Ink
val StoneBorder = Color(0xFFDBDAD6)

// Fasce non prenotabili dell'agenda: pieno, così due fasce sovrapposte non si scuriscono.
val StoneSoft = Color(0xFFF3F3F2)

/** Testo secondario su chiaro: 5.67:1 su Stone, 6.65:1 su Bone. */
val TextMuted = Color(0xFF5F5B52)

// Semantic — tarati su Stone, la peggiore delle due superfici chiare.
val SuccessGreen = Color(0xFF3E6B4C) // 5.16:1 su Stone
val WarnAmber = Color(0xFF8A5F1B) // 4.72:1 su Stone
val ErrorRed = Color(0xFF8C2F2F) // 6.88:1 su Stone
