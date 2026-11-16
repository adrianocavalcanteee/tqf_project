package com.tqfapp.android.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("is_provider") val isProvider: Boolean,
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null
)
