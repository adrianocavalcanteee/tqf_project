package com.tqfapp.android.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "avaliacoes")
data class Review(
    @PrimaryKey
    val id: String,
    val providerId: String,
    val clientId: String,
    val rating: Int,
    val comment: String,
    val date: String
)