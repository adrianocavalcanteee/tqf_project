package com.tqfapp.android.business

import com.tqfapp.android.data.model.User

/**
 * Motor de recomendação acionado quando um Prestador recusa uma solicitação:
 * sugere outros prestadores disponíveis da MESMA categoria, excluindo quem recusou.
 */
object RecommendationEngine {

    fun suggest(
        providers: List<User>,
        category: String,
        excludedProviderId: String?,
        limit: Int = 10
    ): List<User> {
        return providers
            .asSequence()
            .filter { it.isProvider && it.isAvailable }
            .filter { it.id != excludedProviderId }
            .filter { it.category.equals(category, ignoreCase = true) }
            .sortedWith(
                compareByDescending<User> { it.rating }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenByDescending { it.reviewCount }
            )
            .take(limit)
            .toList()
    }
}
