package com.devora.mencare.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Campanello che suona quando la sessione non è più recuperabile: il rinnovo è
 * fallito, i token sono già stati cancellati e all'utente resta solo rientrare.
 *
 * Sta qui e non in `:core:data` perché a scoprirlo è il [TokenAuthenticator],
 * che vive nel livello di rete; chi decide cosa farne — svuotare le cache,
 * riportare al login — è il livello dati, che lo ascolta.
 */
@Singleton
class SessionExpiryNotifier @Inject constructor() {

    /**
     * `extraBufferCapacity` perché la segnalazione parte da un thread di OkHttp
     * e non deve mai restare bloccata ad aspettare un collettore.
     */
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}
