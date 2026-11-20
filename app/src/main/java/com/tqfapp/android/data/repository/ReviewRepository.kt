package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.dto.AvaliacaoDto
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
            } catch (_: Exception) { emptyList() }

            if (dtoList.isNotEmpty()) {
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
            } catch (_: Exception) { emptyList() }

            if (dtoList.isNotEmpty()) {
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
     * Grava a avaliação vinculada ao prestador avaliado (reviewed_id) e à solicitação (service_request_id).
     * Retorna falha se o Supabase recusar (ex.: serviço ainda não concluído) — nada é salvo localmente nesse caso.
     */
    fun addReview(serviceRequestId: String, reviewedId: String, rating: Int, comment: String): Result<Unit> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            val reviewerId = authUser?.id ?: throw IllegalStateException("Usuário não autenticado.")

            val existing = reviewDao.existsForService(serviceRequestId)
            if (existing) throw IllegalStateException("Você já avaliou este serviço.")

            val dto = AvaliacaoDto(
                serviceRequestId = serviceRequestId,
                reviewerId = reviewerId,
                reviewedId = reviewedId,
                rating = rating,
                comment = comment
            )

            SupabaseClient.postgrest.from("avaliacoes").insert(dto)

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
}
