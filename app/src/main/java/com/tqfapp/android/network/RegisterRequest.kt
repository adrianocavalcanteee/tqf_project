package com.tqfapp.android.network

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val isProvider: Boolean,
    val category: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)