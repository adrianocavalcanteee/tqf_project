package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.local.preferences.SessionManager
import com.tqfapp.android.data.model.User
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.runBlocking

/**
 * Repositório responsável pela camada de dados do Usuário (Autenticação e Cadastro).
 * Integrado ao Banco de Dados Local (Room) e SharedPreferences (SessionManager).
 */
class UserRepository(private val context: Context) {

    private val userDao = AppDatabase.getDatabase(context).userDao()
    private val sessionManager = SessionManager(context)

    /**
     * Registra o usuário no banco Room e inicia a sessão.
     */
    fun registerUser(user: User, passwordRaw: String): Result<User> = runBlocking {
        val existingUsers = userDao.getAll()
        if (existingUsers.any { it.email.equals(user.email, ignoreCase = true) }) {
            return@runBlocking Result.failure(IllegalArgumentException("Este e-mail já está cadastrado."))
        }

        userDao.insert(user)
        sessionManager.saveLogin(user.id)
        UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)

        Result.success(user)
    }

    /**
     * Autentica o usuário pelo banco de dados Room.
     */
    fun loginUser(email: String, passwordRaw: String): Result<User> = runBlocking {
        val allUsers = userDao.getAll()
        val user = allUsers.find { it.email.equals(email, ignoreCase = true) }

        if (user != null) {
            sessionManager.saveLogin(user.id)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)
            Result.success(user)
        } else {
            Result.failure(IllegalArgumentException("Usuário não encontrado no banco local."))
        }
    }

    /**
     * Obtém o usuário atualmente logado na sessão do Room/SessionManager.
     */
    fun getCurrentUser(): User? = runBlocking {
        val userId = sessionManager.getUserId()
        if (userId != null) {
            val user = userDao.getById(userId)
            if (user != null) return@runBlocking user
        }

        // Fallback buscando por email salvo no UserSessionManager
        val allUsers = userDao.getAll()
        val sessionEmail = context.getSharedPreferences("tqf_user_session", Context.MODE_PRIVATE)
            .getString("key_user_email", null)

        allUsers.find { it.email.equals(sessionEmail, ignoreCase = true) }
    }

    /**
     * Atualiza os dados do usuário no Room e atualiza os dados da sessão.
     */
    fun updateUser(user: User): Boolean = runBlocking {
        try {
            userDao.update(user)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encerra a sessão do usuário limpando o SharedPreferences.
     */
    fun logout() {
        sessionManager.logout()
        UserSessionManager.clearSession(context)
    }
}
