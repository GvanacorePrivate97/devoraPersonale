package com.devora.mencare.core.data.di

import com.devora.mencare.core.common.DefaultDispatcherProvider
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.data.network.NetworkAdminRepository
import com.devora.mencare.core.data.network.NetworkAuthRepository
import com.devora.mencare.core.data.network.NetworkAvatarRepository
import com.devora.mencare.core.data.network.NetworkBookingRepository
import com.devora.mencare.core.data.network.NetworkCatalogRepository
import com.devora.mencare.core.data.network.NetworkCrmRepository
import com.devora.mencare.core.data.network.NetworkNotificationRepository
import com.devora.mencare.core.data.network.NetworkTimeBlockRepository
import com.devora.mencare.core.data.repository.AdminRepository
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.AvatarRepository
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.data.repository.TimeBlockRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Il punto in cui l'app sceglie da dove arrivano i dati.
 *
 * Dalla Fase 2 sono tutti dal backend. Le interfacce non sono cambiate di una
 * riga passando dai dati finti alla rete — era il motivo per cui esistevano — e
 * le implementazioni `Fake*` restano in `core/data/fake` come attrezzatura dei
 * test, non più collegate a niente che giri sul telefono.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindAuthRepository(impl: NetworkAuthRepository): AuthRepository

    @Binds
    abstract fun bindAvatarRepository(impl: NetworkAvatarRepository): AvatarRepository

    @Binds
    abstract fun bindCatalogRepository(impl: NetworkCatalogRepository): CatalogRepository

    @Binds
    abstract fun bindBookingRepository(impl: NetworkBookingRepository): BookingRepository

    @Binds
    abstract fun bindTimeBlockRepository(impl: NetworkTimeBlockRepository): TimeBlockRepository

    @Binds
    abstract fun bindCrmRepository(impl: NetworkCrmRepository): CrmRepository

    @Binds
    abstract fun bindAdminRepository(impl: NetworkAdminRepository): AdminRepository

    @Binds
    abstract fun bindNotificationRepository(impl: NetworkNotificationRepository): NotificationRepository

    companion object {
        @Provides
        @Singleton
        fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

        /**
         * `SupervisorJob`: se una cache cade non deve portarsi dietro le altre.
         * L'ambito non viene mai chiuso — muore con il processo — ed è riservato
         * al lavoro che sopravvive alle schermate.
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}
