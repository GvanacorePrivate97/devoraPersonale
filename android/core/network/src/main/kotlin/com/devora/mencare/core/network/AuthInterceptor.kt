package com.devora.mencare.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

internal const val HEADER_AUTHORIZATION = "Authorization"

/**
 * Rotte pubbliche: sono le uniche senza token, e sono anche quelle su cui un
 * token vecchio darebbe fastidio (il rinnovo, per esempio, deve poter partire
 * proprio quando l'access token non vale più).
 */
private val PUBLIC_PATHS = setOf(
    "auth/login",
    "auth/register",
    "auth/social",
    "auth/refresh",
    "auth/logout",
    "auth/password/reset-request",
    "auth/password/reset",
)

/**
 * La richiesta appartiene a una rotta che non vuole (o non deve avere) il token.
 * Il confronto è sulla coda del percorso e non sul percorso intero: il prefisso
 * del base URL è configurabile, e una rotta pubblica scambiata per privata
 * farebbe partire un rinnovo dopo un banale "password sbagliata".
 */
internal fun Request.isPublic(): Boolean {
    val path = url.encodedPath
    return PUBLIC_PATHS.any { path.endsWith("/$it") }
}

/**
 * Attacca il bearer token a ogni richiesta che lo richiede.
 *
 * Non tocca le rotte pubbliche e non sostituisce un'intestazione già presente:
 * il [TokenAuthenticator] riprova la richiesta scrivendo lui il token nuovo, e
 * un interceptor che lo riscrivesse rimetterebbe quello vecchio.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokens: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isPublic() || request.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(request)
        }
        val access = tokens.accessToken ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(HEADER_AUTHORIZATION, "Bearer $access").build(),
        )
    }
}
