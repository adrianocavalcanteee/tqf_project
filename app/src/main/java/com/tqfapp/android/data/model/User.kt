package com.tqfapp.android.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class User(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String,
    val isProvider: Boolean, // true = prestador, false = contratante
    val category: String? = null,
    val rating: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null
)
