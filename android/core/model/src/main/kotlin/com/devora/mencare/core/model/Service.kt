package com.devora.mencare.core.model

data class Service(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val priceCents: Long,
    val description: String? = null,
    val featured: Boolean = false,
    /**
     * Un servizio ritirato dal listino resta nell'elenco: lo storico di un
     * cliente ci rimanda, e senza la riga l'app mostrerebbe un appuntamento
     * senza nome. Chi prenota però non deve vederlo — si filtra qui.
     */
    val active: Boolean = true,
)
