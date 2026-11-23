package com.tqfapp.android.ui.common

import androidx.annotation.DrawableRes
import com.tqfapp.android.R

/** Ícone de cada categoria (mesmos ícones dos filtros da Home); usado como imagem padrão sem foto. */
object CategoryIcons {
    private val icons = mapOf(
        "Assistência Técnica" to R.drawable.ic_tech_repair,
        "Reformas e Reparos" to R.drawable.ic_reforms,
        "Serviços Domésticos" to R.drawable.ic_domestic,
        "Design e Tecnologia" to R.drawable.ic_laptop,
        "Eventos" to R.drawable.ic_events,
        "Aulas e Treinamentos" to R.drawable.ic_education,
        "Saúde e Bem-Estar" to R.drawable.ic_health,
        "Autos e Transporte" to R.drawable.ic_auto_transport
    )

    @DrawableRes
    fun forCategory(category: String?): Int =
        icons.entries.firstOrNull { it.key.equals(category?.trim(), ignoreCase = true) }?.value ?: R.drawable.ic_briefcase
}
