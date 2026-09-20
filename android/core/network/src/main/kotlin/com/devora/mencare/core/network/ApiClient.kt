package com.devora.mencare.core.network

import com.devora.mencare.core.network.api.AdminApi
import com.devora.mencare.core.network.api.AuthApi
import com.devora.mencare.core.network.api.BlockApi
import com.devora.mencare.core.network.api.BookingApi
import com.devora.mencare.core.network.api.CatalogApi
import com.devora.mencare.core.network.api.CrmApi
import com.devora.mencare.core.network.api.NotificationApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Timeout volutamente corti: meglio "riprova" che una rotella che gira a vuoto. */
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Il cliente HTTP dell'app: un solo [OkHttpClient], un solo [Retrofit], una
 * interfaccia per area.
 *
 * L'indirizzo arriva da `BuildConfig.API_BASE_URL`, che il modulo prende da
 * `local.properties` (o dall'ambiente) al momento della build: nel repository
 * non c'è nessun URL di produzione.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiClient {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Il backend può aggiungere campi senza rompere una app già installata.
        ignoreUnknownKeys = true
        // Un campo nullo non viene scritto: su un PATCH "assente" vuol dire
        // "non toccare", ed è quasi sempre l'intenzione giusta. Dove invece
        // serve un `null` vero si passa `JsonNull` (vedi `jsonOrNull`).
        explicitNulls = false
        // I default dei DTO servono a leggere, non a scrivere: un corpo che
        // ripete valori non richiesti è solo rumore in più da validare.
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val baseUrl = BuildConfig.API_BASE_URL
        require(baseUrl.isNotBlank()) {
            "API_BASE_URL non configurato: aggiungilo a local.properties prima di costruire l'app."
        }
        return Retrofit.Builder()
            // Retrofit pretende la barra finale, altrimenti l'ultimo segmento
            // del percorso base verrebbe buttato via.
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideCatalogApi(retrofit: Retrofit): CatalogApi = retrofit.create(CatalogApi::class.java)

    @Provides
    @Singleton
    fun provideBookingApi(retrofit: Retrofit): BookingApi = retrofit.create(BookingApi::class.java)

    @Provides
    @Singleton
    fun provideBlockApi(retrofit: Retrofit): BlockApi = retrofit.create(BlockApi::class.java)

    @Provides
    @Singleton
    fun provideCrmApi(retrofit: Retrofit): CrmApi = retrofit.create(CrmApi::class.java)

    @Provides
    @Singleton
    fun provideAdminApi(retrofit: Retrofit): AdminApi = retrofit.create(AdminApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)
}
