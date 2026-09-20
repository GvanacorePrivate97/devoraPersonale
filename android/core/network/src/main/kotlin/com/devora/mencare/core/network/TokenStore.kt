package com.devora.mencare.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val FILE_NAME = "mencare_session"
private const val KEY_ACCESS = "access_token"
private const val KEY_REFRESH = "refresh_token"
private const val KEY_EXPIRES_AT = "access_expires_at"

/** Margine con cui un access token è considerato scaduto in anticipo. */
private const val EXPIRY_SKEW_MILLIS = 30_000L

/** La coppia di token così come la consegna il backend. */
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Durata dell'access token in secondi, come da `/auth/…`. */
    val expiresInSeconds: Long,
)

/**
 * Deposito dei token della sessione.
 *
 * Sta in [EncryptedSharedPreferences] con chiave nell'Android Keystore: un
 * refresh token vale trenta giorni di accesso all'account, quindi non può
 * finire in un file leggibile né in un log. Qui dentro nessun valore viene mai
 * stampato, nemmeno in debug.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Creato alla prima lettura e non nel costruttore: aprire il Keystore costa
     * decine di millisecondi e non va fatto mentre si monta il grafo di Hilt.
     */
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)

    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)

    /** C'è una sessione da provare a riprendere all'avvio dell'app. */
    val hasSession: Boolean get() = refreshToken != null

    /** L'access token è (quasi) scaduto: conviene rinnovarlo prima di usarlo. */
    val accessExpired: Boolean
        get() {
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            return expiresAt == 0L || System.currentTimeMillis() >= expiresAt - EXPIRY_SKEW_MILLIS
        }

    fun save(tokens: SessionTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + tokens.expiresInSeconds * 1_000L)
            .apply()
    }

    /** Uscita dall'app o rinnovo fallito: non resta niente da riusare. */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
