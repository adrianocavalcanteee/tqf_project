package com.tqfapp.android.data.local.dao

import androidx.room.*
import com.tqfapp.android.data.model.Proposal

@Dao
interface ProposalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(proposal: Proposal)

    @Query("SELECT * FROM propostas")
    suspend fun getAll(): List<Proposal>

    @Query("SELECT * FROM propostas WHERE serviceId = :serviceId")
    suspend fun getByService(serviceId: String): List<Proposal>

    @Update
    suspend fun update(proposal: Proposal)

    @Delete
    suspend fun delete(proposal: Proposal)
}