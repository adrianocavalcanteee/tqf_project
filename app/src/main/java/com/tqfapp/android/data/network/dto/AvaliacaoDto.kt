package com.tqfapp.android.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvaliacaoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("service_request_id") val serviceRequestId: String,
    @SerialName("reviewer_id") val reviewerId: String,
    @SerialName("reviewed_id") val reviewedId: String,
    @SerialName("rating") val rating: Int,
    @SerialName("comment") val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
