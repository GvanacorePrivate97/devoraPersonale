package com.devora.mencare.core.data.fake

import android.content.Context
import android.net.Uri
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.data.repository.AvatarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val AVATAR_FOLDER = "avatars"

@Singleton
class FakeAvatarRepository @Inject constructor(
    private val store: InMemoryStore,
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : AvatarRepository {

    override suspend fun save(sourceUri: String): AppResult<String> {
        val current = store.currentUser.value ?: return AppResult.Failure(AppError.NotFound)
        val saved = withContext(dispatchers.io) {
            runCatching {
                val folder = File(context.filesDir, AVATAR_FOLDER).apply { mkdirs() }
                // Nome nuovo a ogni salvataggio: così la cache immagini non
                // continua a mostrare la foto precedente.
                val target = File(folder, "${current.id}-${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(Uri.parse(sourceUri)).use { input ->
                    input ?: error("immagine non leggibile")
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
        }.getOrElse { return AppResult.Failure(AppError.Unknown(it.message)) }

        deleteFile(current.avatarPath)
        apply(current.id, saved.absolutePath)
        return AppResult.Success(saved.absolutePath)
    }

    override suspend fun clear(): AppResult<Unit> {
        val current = store.currentUser.value ?: return AppResult.Failure(AppError.NotFound)
        deleteFile(current.avatarPath)
        apply(current.id, null)
        return AppResult.Success(Unit)
    }

    private suspend fun deleteFile(path: String?) {
        if (path == null) return
        withContext(dispatchers.io) { runCatching { File(path).delete() } }
    }

    private fun apply(userId: String, path: String?) {
        store.users.value = store.users.value.map {
            if (it.id == userId) it.copy(avatarPath = path) else it
        }
        store.currentUser.value = store.currentUser.value?.copy(avatarPath = path)
    }
}
