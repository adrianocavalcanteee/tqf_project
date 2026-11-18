package com.tqfapp.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale

/**
 * Entidade única de usuário. O papel é definido por [isProvider]:
 * true = Prestador, false = Contratante (Cliente).
 * Implementa [Serializable] para ser repassada entre Activities via Intent.
 */
@Entity(tableName = "usuarios")
data class User(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String,
    val isProvider: Boolean,
    val category: String? = null,
    val specialty: String? = null,
    val bio: String? = null,
    val services: List<String> = emptyList(),
    val address: String? = null,
    val telephone: String? = null,
    val profilePictureUri: String? = null,
    val portfolioUris: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val distanceKm: Double? = null,
    val hourlyRate: Double? = null,
    val isAvailable: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null
) : Serializable {

    /** Rótulo de preço transparente: valor por hora quando informado, senão negociação livre. */
    fun priceLabel(): String =
        hourlyRate?.takeIf { it > 0 }?.let { "${formatCurrency(it)} / hora" } ?: "Valor negociável"

    companion object {
        private const val serialVersionUID = 1L

        fun formatCurrency(value: Double): String =
            NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
    }
}
