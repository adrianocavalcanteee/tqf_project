package com.tqfapp.android.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PerfilPrestadorDto(
    @SerialName("user_id") val userId: String,
    @SerialName("category") val category: String? = null,
    @SerialName("specialty") val specialty: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("office_address") val officeAddress: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("portfolio_urls") val portfolioUrls: List<String> = emptyList(),
    @SerialName("service_tags") val serviceTags: List<String> = emptyList()
)
