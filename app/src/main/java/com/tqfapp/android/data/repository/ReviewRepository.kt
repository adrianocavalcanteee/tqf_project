package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.business.ReviewRules
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.dto.AvaliacaoDto
import kotlinx.coroutines.runBlocking

/**
 * Repositório responsável pelo gerenciamento de Avaliações integrado ao Supabase PostgREST.
 * Suporta avaliação bidirecional (Cliente -> Prestador e Prestador -> Cliente)
 * com trava contra avaliações duplicadas por serviço.
 */
class ReviewRepository(private val context: Context) {

    private val reviewDao = AppDatabase.getDatabase(context).reviewDao()

    /**
     * Adiciona uma avaliação no Supabase no formato (Reviewer -> Reviewed).
     * Garante que o mesmo usuário não possa avaliar o mesmo serviço mais de uma vez.
     */
    fun addReview(
        serviceRequestId: String,
        reviewedId: String,
        rating: Int,
        comment: String? = null
    ): Result<Boolean> = runBlocking {
        try {
            if (!ReviewRules.isValidRating(rating)) {
                return@runBlocking Result.failure(IllegalArgumentException("Nota de avaliação deve estar entre 1 e 5."))
            }

            val reviewerId = SupabaseClient.auth.currentUserOrNull()?.id
                ?: return@runBlocking Result.failure(IllegalStateException("Usuário não autenticado no Supabase."))

            // Verifica se o mesmo usuário já avaliou este serviço
            val existing = try {
                SupabaseClient.postgrest.from("avaliacoes")
                    .select {
                        filter {
                            eq("service_request_id", serviceRequestId)
                            eq("reviewer_id", reviewerId)
                        }
                    }
                    .decodeList<AvaliacaoDto>()
            } catch (_: Exception) { emptyList() }

            if (existing.isNotEmpty()) {
                return@runBlocking Result.failure(IllegalStateException("Você já enviou uma avaliação para este serviço."))
            }

            val dto = AvaliacaoDto(
                serviceRequestId = serviceRequestId,
                reviewerId = reviewerId,
                reviewedId = reviewedId,
                rating = rating,
                comment = comment
            )

            SupabaseClient.postgrest.from("avaliacoes").insert(dto)

            // Cache local Room
            try {
                reviewDao.insert(
                    Review(
                        id = java.util.UUID.randomUUID().toString(),
                        serviceId = serviceRequestId,
                        providerId = reviewedId,
                        clientId = reviewerId,
                        rating = rating,
                        comment = comment ?: "",
                        date = "Hoje"
                    )
                )
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Busca todas as avaliações recebidas por um determinado usuário (Prestador ou Cliente).
     */
    fun getReviewsForUser(userId: String): List<AvaliacaoDto> = runBlocking {
        try {
            SupabaseClient.postgrest.from("avaliacoes")
                .select {
                    filter {
                        eq("reviewed_id", userId)
                    }
                }
                .decodeList<AvaliacaoDto>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Verifica se determinado usuário já avaliou uma solicitação específica.
     */
    fun hasUserReviewedService(serviceRequestId: String, reviewerId: String): Boolean = runBlocking {
        try {
            val list = SupabaseClient.postgrest.from("avaliacoes")
                .select {
                    filter {
                        eq("service_request_id", serviceRequestId)
                        eq("reviewer_id", reviewerId)
                    }
                }
                .decodeList<AvaliacaoDto>()
            list.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // Compatibilidade com Room/UI local
    fun getAllReviews(): List<Review> {
        return try {
            reviewDao.getAll()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getReviewsByProvider(providerId: String): List<Review> {
        return try {
            val supabaseReviews = getReviewsForUser(providerId)
            if (supabaseReviews.isNotEmpty()) {
                supabaseReviews.map {
                    Review(
                        id = it.id ?: java.util.UUID.randomUUID().toString(),
                        serviceId = it.serviceRequestId,
                        providerId = it.reviewedId,
                        clientId = it.reviewerId,
                        rating = it.rating,
                        comment = it.comment ?: "",
                        date = it.createdAt?.substringBefore("T") ?: "Hoje"
                    )
                }
            } else {
                reviewDao.getByProvider(providerId)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getReviewsByClient(clientId: String): List<Review> {
        return try {
            reviewDao.getByClient(clientId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addReview(review: Review): Boolean {
        return try {
            reviewDao.insert(review)
            true
        } catch (e: Exception) {
            false
        }
    }
}
