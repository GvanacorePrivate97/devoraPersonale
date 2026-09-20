package com.devora.mencare.core.data.network

import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chi è connesso, per i repository che ne hanno bisogno.
 *
 * Con i dati finti l'identità stava nello store in memoria; con il backend la
 * stessa informazione serve a scegliere la rotta giusta: lo storico di un
 * cliente arriva da `/appointments/me` se a chiederlo è lui, dalla rubrica se a
 * chiederlo è il salone. A scriverla è solo `NetworkAuthRepository`; tutti gli
 * altri la leggono.
 *
 * Non contiene token: quelli stanno nel deposito cifrato di `:core:network` e
 * non escono da lì.
 */
@Singleton
class Session @Inject constructor() {

    private val _user = MutableStateFlow<User?>(null)

    val user: StateFlow<User?> = _user.asStateFlow()

    val role: UserRole? get() = _user.value?.role

    /** Scheda CRM dell'account, se è un cliente. */
    val clientId: String? get() = _user.value?.clientRecordId

    /** Poltrona dell'account, se è un operatore o il titolare. */
    val operatorId: String? get() = _user.value?.operatorId

    val isSalonSide: Boolean get() = role == UserRole.STAFF || role == UserRole.OWNER

    fun set(user: User?) {
        _user.value = user
    }
}
