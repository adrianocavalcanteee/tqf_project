package com.tqfapp.android.data.local.dao
import androidx.room.*
import com.tqfapp.android.data.model.Review

@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: Review)

    @Query("SELECT * FROM avaliacoes")
    suspend fun getAll(): List<Review>

    @Query("SELECT EXISTS(SELECT 1 FROM avaliacoes WHERE serviceId = :serviceId)")
    suspend fun existsForService(serviceId: String): Boolean

    @Query("SELECT * FROM avaliacoes WHERE providerId = :providerId")
    suspend fun getByProvider(providerId: String): List<Review>

    @Delete
    suspend fun delete(review: Review)
}