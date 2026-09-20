package com.devora.mencare.core.data.network

import android.content.Context
import android.net.Uri
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.map
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.AvatarRepository
import com.devora.mencare.core.network.api.AuthApi
import com.devora.mencare.core.network.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Lo stesso limite dichiarato dal backend: si scarta prima di salire, non dopo. */
private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

private const val DEFAULT_MIME = "image/jpeg"

/**
 * Foto profilo sul backend.
 *
 * In Fase 1 l'immagine restava nello spazio dell'app e il modello ne teneva il
 * percorso; adesso sale al server e quello che torna è un URL — per chi la
 * disegna cambia poco, `ProfileAvatar` accetta entrambi, ma la foto segue
 * l'account anche cambiando telefono.
 */
@Singleton
class NetworkAvatarRepository @Inject constructor(
    private val api: AuthApi,
    private val session: Session,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val context: Context,
) : AvatarRepository {

    override suspend fun save(sourceUri: String): AppResult<String> = withContext(dispatchers.io) {
        val bytes = readPickedImage(sourceUri) ?: return@withContext AppResult.Failure(AppError.Validation("file"))
        if (bytes.size > MAX_AVATAR_BYTES) return@withContext AppResult.Failure(AppError.Validation("file"))

        val mime = context.contentResolver.getType(Uri.parse(sourceUri)) ?: DEFAULT_MIME
        val part = MultipartBody.Part.createFormData(
            // Il campo si chiama "file": il backend rifiuta qualunque altro nome.
            name = "file",
            filename = "avatar",
            body = bytes.toRequestBody(mime.toMediaTypeOrNull()),
        )
        apiCall { api.uploadAvatar(part).avatarUrl }.onSuccess { applyAvatar(it) }
    }

    override suspend fun clear(): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.deleteAvatar() }.map { applyAvatar(null) }
    }

    /** Il selettore di sistema dà un URI di contenuto, non un file leggibile. */
    private fun readPickedImage(sourceUri: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { it.readBytes() }
    }.getOrNull()

    private fun applyAvatar(url: String?) {
        session.user.value?.let { user -> session.set(user.copy(avatarPath = url)) }
    }
}
