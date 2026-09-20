package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult

/**
 * Profile photo of the signed-in account. Phase 1 keeps the image in the app's
 * own storage and hands back its path; Phase 2 uploads it and hands back a URL —
 * either way the caller only ever passes the picked image and gets a reference
 * to store on the user.
 */
interface AvatarRepository {
    /** [sourceUri] comes from the system photo picker. */
    suspend fun save(sourceUri: String): AppResult<String>

    /** Drops the photo: the initials come back. */
    suspend fun clear(): AppResult<Unit>
}
