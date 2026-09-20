package com.devora.mencare.core.data.di

import javax.inject.Qualifier

/**
 * Ambito di coroutine lungo quanto il processo, per il lavoro che non appartiene
 * a nessuna schermata: riprendere la sessione all'avvio, tenere una cache viva
 * fra due schermate che leggono gli stessi dati. Non va usato per niente che
 * debba morire con una `ViewModel`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
