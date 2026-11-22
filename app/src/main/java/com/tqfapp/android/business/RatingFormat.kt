package com.tqfapp.android.business

import com.tqfapp.android.data.model.Review
import java.util.Locale

/** Formatação única da nota (pt-BR) usada por todas as telas que exibem avaliações reais. */
object RatingFormat {
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    /** 4.66 -> "4,7" */
    fun value(rating: Double): String = String.format(PT_BR, "%.1f", rating)

    /** 1 -> "1 avaliação", 3 -> "3 avaliações" */
    fun count(total: Int): String = if (total == 1) "1 avaliação" else "$total avaliações"

    /** Média das avaliações ou null quando não há nenhuma (a UI mostra o estado vazio). */
    fun average(reviews: List<Review>): Double? =
        if (reviews.isEmpty()) null else reviews.map { it.rating }.average()

    /** "4,7 (12 avaliações)" ou null sem avaliações. */
    fun summary(reviews: List<Review>): String? =
        average(reviews)?.let { "${value(it)} (${count(reviews.size)})" }
}
