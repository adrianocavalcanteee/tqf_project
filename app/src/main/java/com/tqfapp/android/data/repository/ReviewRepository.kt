package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.dto.AvaliacaoDto
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.runBlocking
import java.util.UUID

class ReviewRepository(private val context: Context) {

    private val reviewDao = AppDatabase.getDatabase(context).reviewDao()

    fun getAllReviews(): List<Review> = runBlocking {
        try {
            reviewDao.getAll()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getReviewsByProvider(providerId: String): List<Review> = runBlocking {
        try {
            val dtoList = try {
                SupabaseClient.postgrest.from("avaliacoes")
                    .select { filter { eq("reviewed_id", providerId) } }
                    .decodeList<AvaliacaoDto>()
            } catch (_: Exception) { null }

            if (dtoList != null) {
                dtoList.map { dto ->
                    Review(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        serviceId = dto.serviceRequestId,
                        providerId = dto.reviewedId,
                        clientId = dto.reviewerId,
                        rating = dto.rating,
                        comment = dto.comment ?: "",
                        date = dto.createdAt?.take(10) ?: "Hoje"
                    )
                }
            } else {
                reviewDao.getByProvider(providerId)
            }
        } catch (e: Exception) {
            reviewDao.getByProvider(providerId)
        }
    }

    fun getReviewsByClient(clientId: String): List<Review> = runBlocking {
        try {
            val dtoList = try {
                SupabaseClient.postgrest.from("avaliacoes")
                    .select { filter { eq("reviewer_id", clientId) } }
                    .decodeList<AvaliacaoDto>()
            } catch (_: Exception) { null }

            if (dtoList != null) {
                dtoList.map { dto ->
                    Review(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        serviceId = dto.serviceRequestId,
                        providerId = dto.reviewedId,
                        clientId = dto.reviewerId,
                        rating = dto.rating,
                        comment = dto.comment ?: "",
                        date = dto.createdAt?.take(10) ?: "Hoje"
                    )
                }
            } else {
                reviewDao.getByClient(clientId)
            }
        } catch (e: Exception) {
            reviewDao.getByClient(clientId)
        }
    }

    /**
     * Grava a avaliação vinculada ao avaliado (reviewed_id: prestador ou cliente) e à solicitação (service_request_id).
     * Duplicidade é barrada pelo UNIQUE (service_request_id, reviewer_id) do Supabase: o cache local não
     * distingue quem avaliou, então não serve para essa checagem.
     * Retorna falha se o Supabase recusar (ex.: serviço ainda não concluído) — nada é salvo localmente nesse caso.
     */
    fun addReview(serviceRequestId: String, reviewedId: String, rating: Int, comment: String): Result<Unit> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            val reviewerId = authUser?.id ?: throw IllegalStateException("Usuário não autenticado.")

            val dto = AvaliacaoDto(
                serviceRequestId = serviceRequestId,
                reviewerId = reviewerId,
                reviewedId = reviewedId,
                rating = rating,
                comment = comment
            )

            try {
                SupabaseClient.postgrest.from("avaliacoes").insert(dto)
            } catch (e: PostgrestRestException) {
                // UNIQUE (service_request_id, reviewer_id): já existe avaliação desta contratação.
                if (e.code == UNIQUE_VIOLATION) throw IllegalStateException("Você já avaliou este serviço.")
                throw e
            }

            val review = Review(
                id = UUID.randomUUID().toString(),
                serviceId = serviceRequestId,
                providerId = reviewedId,
                clientId = reviewerId,
                rating = rating,
                comment = comment,
                date = "Hoje"
            )
            reviewDao.insert(review)

            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Não foi possível registrar a avaliação. Tente novamente.", e))
        }
    }

    /** Se o usuário logado já avaliou esta contratação (consulta o Supabase; cache local se offline). */
    fun hasReviewedService(serviceRequestId: String): Boolean = runBlocking {
        val reviewerId = SupabaseClient.auth.currentUserOrNull()?.id ?: return@runBlocking false
        try {
            SupabaseClient.postgrest.from("avaliacoes")
                .select {
                    filter {
                        eq("service_request_id", serviceRequestId)
                        eq("reviewer_id", reviewerId)
                    }
                }
                .decodeList<AvaliacaoDto>()
                .isNotEmpty()
        } catch (_: Exception) {
            try { reviewDao.existsForService(serviceRequestId) } catch (_: Exception) { false }
        }
    }

    /** Média e total de avaliações recebidas por usuário, em uma única consulta. */
    fun getRatingSummaries(reviewedIds: Collection<String>): Map<String, List<Review>> = runBlocking {
        val ids = reviewedIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return@runBlocking emptyMap()
        try {
            SupabaseClient.postgrest.from("avaliacoes")
                .select { filter { isIn("reviewed_id", ids) } }
                .decodeList<AvaliacaoDto>()
                .map { it.toReview() }
                .groupBy { it.providerId }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun AvaliacaoDto.toReview() = Review(
        id = id ?: UUID.randomUUID().toString(),
        serviceId = serviceRequestId,
        providerId = reviewedId,
        clientId = reviewerId,
        rating = rating,
        comment = comment ?: "",
        date = createdAt?.take(10) ?: "Hoje"
    )

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
