package com.tqfapp.android.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolicitacaoServicoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("client_id") val clientId: String,
    @SerialName("target_provider_id") val targetProviderId: String? = null,
    @SerialName("accepted_provider_id") val acceptedProviderId: String? = null,
    @SerialName("category") val category: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String = "PENDENTE",
    @SerialName("urgency") val urgency: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
