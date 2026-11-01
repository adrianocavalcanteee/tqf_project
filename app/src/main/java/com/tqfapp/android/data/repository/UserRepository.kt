package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.model.User

/**
 * Repositório responsável pela camada de dados do Usuário (Autenticação e Cadastro).
 * Estruturado e pronto para ser integrado com o Banco de Dados Local (Room / DatabaseHelper) e API (Retrofit).
 */
class UserRepository(private val context: Context) {

    // Armazenamento temporário em memória para permitir a execução até o BD estar conectado
    private val inMemoryUsers = mutableListOf<User>()

    /**
     * Ponto de integração para registrar o usuário.
     * Deve ser conectado ao DatabaseHelper / DAO ou à ApiService do Retrofit.
     */
    fun registerUser(user: User, passwordRaw: String): Result<User> {
        if (inMemoryUsers.any { it.email.equals(user.email, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Este e-mail já está cadastrado."))
        }

        inMemoryUsers.add(user)
        // TODO: Conectar com o DatabaseHelper / Room / Retrofit API
        return Result.success(user)
    }

    /**
     * Ponto de integração para autenticar o usuário.
     */
    fun loginUser(email: String, passwordRaw: String): Result<User> {
        val user = inMemoryUsers.find { it.email.equals(email, ignoreCase = true) }
        return if (user != null) {
            Result.success(user)
        } else {
            Result.failure(IllegalArgumentException("Usuário não encontrado no cache local."))
        }
    }
}
