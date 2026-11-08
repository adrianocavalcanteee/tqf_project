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
    val specialty: String? = null,
    val phone: String? = null,
    val profilePictureUri: String? = null,
    val portfolioUris: List<String> = emptyList(),
    val rating: Double = 5.0,
    val latitude: Double? = null,
    val longitude: Double? = null
)
