package com.devora.mencare.core.designsystem.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the phone dialer on a stored number ("+39 347 812 4490"), for a
 * "Chiama" button. ACTION_DIAL needs no permission; on a tablet without
 * telephony there is no dialer and the tap does nothing.
 */
fun Context.dialPhone(phone: String?) {
    val number = phone.orEmpty().filter { it.isDigit() || it == '+' }
    if (number.isEmpty()) return
    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}
