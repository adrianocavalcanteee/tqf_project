package com.tqfapp.android.business

/** Urgência de uma solicitação (coluna solicitacoes_servico.urgency). */
enum class Urgency(val label: String) {
    HOJE("Hoje"),
    SEMANA("Esta semana"),
    FLEXIVEL("Sem pressa");

    companion object {
        fun fromName(name: String?): Urgency? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
