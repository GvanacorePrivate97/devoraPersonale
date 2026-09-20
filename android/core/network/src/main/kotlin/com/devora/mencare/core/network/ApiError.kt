package com.devora.mencare.core.network

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Forma unica degli errori dell'API (`backend/src/lib/errors.ts`):
 * `{ "error": { "code", "message", "field"? } }`. Il client legge `code`, non
 * il messaggio: i testi cambiano, i codici no.
 */
@Serializable
data class ApiErrorEnvelope(
    @SerialName("error") val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String? = null,
    /** Presente solo su `VALIDATION`: è il campo del form da illuminare. */
    val field: String? = null,
)

/** Codici che l'API può restituire, uno a uno con `ErrorCode` del backend. */
private const val CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
private const val CODE_EMAIL_TAKEN = "EMAIL_ALREADY_REGISTERED"
private const val CODE_SLOT_TAKEN = "SLOT_NO_LONGER_AVAILABLE"
private const val CODE_NOT_FOUND = "NOT_FOUND"
private const val CODE_VALIDATION = "VALIDATION"
private const val CODE_FORBIDDEN = "FORBIDDEN"
private const val CODE_UNAUTHORIZED = "UNAUTHORIZED"
private const val CODE_CONFLICT = "CONFLICT"
private const val CODE_RATE_LIMITED = "RATE_LIMITED"

/**
 * Campo convenzionale con cui il backend segnala che un blocco si sovrappone a
 * degli appuntamenti: `TimeBlockRepository.createBlock` promette proprio
 * `Validation("conflicts")`, quindi il codice `CONFLICT` generico ricade qui.
 */
const val FIELD_CONFLICTS = "conflicts"

/**
 * Traduce un errore di rete nel vocabolario delle app (`AppError`), condiviso
 * con iOS. Tutto ciò che non è riconducibile a un codice noto resta
 * [AppError.Unknown]: l'app mostra "riprova", non un dettaglio tecnico.
 */
object ErrorMapper {

    /** Tollerante: se il corpo non è quello previsto non si perde l'errore vero. */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun map(throwable: Throwable): AppError = when (throwable) {
        is ApiException -> throwable.error
        is HttpException -> fromHttp(throwable)
        // Nessuna rete, DNS, timeout: il messaggio tecnico non serve all'utente.
        is IOException -> AppError.Unknown(null)
        else -> AppError.Unknown(throwable.message)
    }

    private fun fromHttp(exception: HttpException): AppError {
        val raw = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val body = raw?.let { runCatching { json.decodeFromString<ApiErrorEnvelope>(it).error }.getOrNull() }
        return body?.let(::fromBody) ?: AppError.Unknown(null)
    }

    fun fromBody(body: ApiErrorBody): AppError = when (body.code) {
        CODE_INVALID_CREDENTIALS -> AppError.InvalidCredentials
        CODE_EMAIL_TAKEN -> AppError.EmailAlreadyRegistered
        CODE_SLOT_TAKEN -> AppError.SlotNoLongerAvailable
        CODE_NOT_FOUND -> AppError.NotFound
        CODE_VALIDATION -> AppError.Validation(body.field.orEmpty())
        // Un blocco che travolge appuntamenti torna come conflitto: le schermate
        // lo aspettano già sotto forma di errore di campo.
        CODE_CONFLICT -> AppError.Validation(FIELD_CONFLICTS)
        // Sessione scaduta o permesso negato: l'utente non può farci nulla se non
        // rientrare, e il rientro lo gestisce l'Authenticator.
        CODE_FORBIDDEN, CODE_UNAUTHORIZED, CODE_RATE_LIMITED -> AppError.Unknown(body.message)
        else -> AppError.Unknown(body.message)
    }
}

/** Errore già tradotto, sollevato da chi sa leggere il corpo (es. l'Authenticator). */
class ApiException(val error: AppError, message: String? = null) : Exception(message)

/**
 * Corre una chiamata di rete e ne fa un [AppResult]: è il confine fra "il
 * mondo lancia eccezioni" e "il dominio torna risultati". La cancellazione non
 * è un errore e deve continuare a propagarsi, altrimenti una schermata chiusa
 * a metà caricamento comparirebbe come guasto.
 */
suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(ErrorMapper.map(throwable))
}
