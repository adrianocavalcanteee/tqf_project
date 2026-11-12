package com.tqfapp.android.data.local.dao

import androidx.room.*
import com.tqfapp.android.data.model.Review

@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(review: Review)

    @Query("SELECT * FROM avaliacoes")
    fun getAll(): List<Review>

    @Query("SELECT EXISTS(SELECT 1 FROM avaliacoes WHERE serviceId = :serviceId)")
    fun existsForService(serviceId: String): Boolean

    @Query("SELECT * FROM avaliacoes WHERE providerId = :providerId")
    fun getByProvider(providerId: String): List<Review>

    @Query("SELECT * FROM avaliacoes WHERE clientId = :clientId")
    fun getByClient(clientId: String): List<Review>

    @Delete
    fun delete(review: Review)
}