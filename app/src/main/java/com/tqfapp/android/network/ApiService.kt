package com.tqfapp.android.network

import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<User>

    @POST("users")
    suspend fun register(@Body request: RegisterRequest): Response<User>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): Response<User>

    // Prestadores perto da posição (para o mapa)
    @GET("providers")
    suspend fun getProviders(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("category") category: String? = null
    ): Response<List<User>>

    // Serviços oferecidos por um prestador
    @GET("users/{id}/services")
    suspend fun getServices(@Path("id") providerId: String): Response<List<Service>>

    @GET("users/{id}/reviews")
    suspend fun getReviews(@Path("id") providerId: String): Response<List<Review>>

    @POST("reviews")
    suspend fun postReview(@Body review: Review): Response<Review>
}