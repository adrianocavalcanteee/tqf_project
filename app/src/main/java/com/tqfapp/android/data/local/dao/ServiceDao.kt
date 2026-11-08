package com.tqfapp.android.data.local.dao

import androidx.room.*
import com.tqfapp.android.data.model.Service

@Dao
interface ServiceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(service: Service)

    @Query("SELECT * FROM servicos")
    suspend fun getAll(): List<Service>

    @Query("SELECT * FROM servicos WHERE id = :id")
    suspend fun getById(id: String): Service?

    @Update
    suspend fun update(service: Service)

    @Delete
    suspend fun delete(service: Service)
}