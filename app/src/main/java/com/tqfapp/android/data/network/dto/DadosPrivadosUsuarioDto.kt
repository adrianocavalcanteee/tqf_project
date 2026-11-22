package com.tqfapp.android.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DadosPrivadosUsuarioDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("postal_code") val postalCode: String? = null
)
