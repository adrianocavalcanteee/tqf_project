package com.tqfapp.android.data.local.dao

import androidx.room.*
import com.tqfapp.android.data.model.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Query("SELECT * FROM usuarios")
    suspend fun getAll(): List<User>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun getById(id: String): User?

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)
}