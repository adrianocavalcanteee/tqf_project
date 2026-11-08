package com.tqfapp.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "propostas")
data class Proposal(
    @PrimaryKey
    val id: String,
    val serviceId: String,
    val providerId: String,
    val clientId: String,
    val value: Double,
    val status: String
)