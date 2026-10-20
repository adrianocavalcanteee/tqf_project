package com.tqfapp.android.network

import retrofit2.Response

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> =
    try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Error("Erro ${response.code()}: ${response.message()}")
    } catch (e: Exception) {
        ApiResult.Error(e.localizedMessage ?: "Erro de conexão")
    }