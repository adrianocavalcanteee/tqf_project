package com.tqfapp.android.data.local.dao

import androidx.room.*
import com.tqfapp.android.data.model.Service

@Dao
interface ServiceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(service: Service)

    @Query("SELECT * FROM servicos")
    fun getAll(): List<Service>

    @Query("SELECT * FROM servicos WHERE id = :id")
    fun getById(id: String): Service?

    @Update
    fun update(service: Service)

    @Delete
    fun delete(service: Service)
}