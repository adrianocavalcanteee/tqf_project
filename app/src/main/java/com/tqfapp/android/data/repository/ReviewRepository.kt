package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Review

/**
 * Repositório desacoplado para Avaliações.
 * Concentra buscas por prestador/cliente e inclusão de avaliações.
 */
class ReviewRepository(private val context: Context) {

    private val reviewDao = AppDatabase.getDatabase(context).reviewDao()

    fun getAllReviews(): List<Review> {
        return try {
            reviewDao.getAll()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getReviewsByProvider(providerId: String): List<Review> {
        return try {
            reviewDao.getByProvider(providerId)
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
