package com.tqfapp.android.data.model

data class Review(
    val id: String,
    val providerId: String,
    val clientId: String,
    val rating: Int,
    val comment: String,
    val date: String
)
