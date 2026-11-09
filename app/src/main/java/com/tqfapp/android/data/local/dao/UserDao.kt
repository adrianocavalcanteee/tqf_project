package com.tqfapp.android.data.local.dao

import androidx.room.*
import com.tqfapp.android.data.model.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: User)

    @Query("SELECT * FROM usuarios")
    fun getAll(): List<User>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun getById(id: String): User?

    @Update
    fun update(user: User)

    @Delete
    fun delete(user: User)
}