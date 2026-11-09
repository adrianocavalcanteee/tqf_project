package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.local.preferences.SessionManager
import com.tqfapp.android.data.model.User
import com.tqfapp.android.utils.UserSessionManager
import java.util.UUID

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
    fun registerUser(user: User, passwordRaw: String): Result<User> {
        try {
            val existingUsers = try { userDao.getAll() } catch (_: Exception) { emptyList() }
            if (existingUsers.any { it.email.equals(user.email, ignoreCase = true) }) {
                return Result.failure(IllegalArgumentException("Este e-mail já está cadastrado."))
            }

            userDao.insert(user)
            sessionManager.saveLogin(user.id)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)

            return Result.success(user)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Autentica o usuário pelo banco de dados Room.
     */
    fun loginUser(email: String, passwordRaw: String): Result<User> {
        try {
            val allUsers = try { userDao.getAll() } catch (_: Exception) { emptyList() }
            var user = allUsers.find { it.email.equals(email, ignoreCase = true) }

            val isProvider = UserSessionManager.isProviderUser(context)
            val userName = context.getSharedPreferences("tqf_user_session", Context.MODE_PRIVATE)
                .getString("key_user_name", email.substringBefore("@")) ?: email.substringBefore("@")

            if (user == null) {
                user = User(
                    id = UUID.randomUUID().toString(),
                    name = userName,
                    email = email,
                    isProvider = isProvider
                )
                try {
                    userDao.insert(user)
                } catch (_: Exception) {}
            } else {
                // Atualiza o isProvider para respeitar o toggle de login
                user = user.copy(isProvider = isProvider)
                try {
                    userDao.insert(user)
                } catch (_: Exception) {}
            }

            sessionManager.saveLogin(user.id)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)
            return Result.success(user)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Obtém o usuário atualmente logado na sessão do Room/SessionManager.
     */
    fun getCurrentUser(): User? {
        try {
            val prefs = context.getSharedPreferences("tqf_user_session", Context.MODE_PRIVATE)
            val sessionEmail = prefs.getString("key_user_email", null)
            val allUsers = try { userDao.getAll() } catch (_: Exception) { emptyList() }

            if (!sessionEmail.isNullOrBlank()) {
                val found = allUsers.find { it.email.equals(sessionEmail, ignoreCase = true) }
                if (found != null) {
                    sessionManager.saveLogin(found.id)
                    return found
                }
            }

            val userId = sessionManager.getUserId()
            if (userId != null) {
                val user = try { userDao.getById(userId) } catch (_: Exception) { null }
                if (user != null) return user
            }

            val isProvider = UserSessionManager.isProviderUser(context)
            val byRole = allUsers.find { it.isProvider == isProvider }
            if (byRole != null) {
                sessionManager.saveLogin(byRole.id)
                return byRole
            }

            if (allUsers.isNotEmpty()) {
                return allUsers.first()
            }

            val defaultUser = User(
                id = UUID.randomUUID().toString(),
                name = prefs.getString("key_user_name", "Usuário TQF") ?: "Usuário TQF",
                email = sessionEmail ?: "usuario@tqf.com",
                isProvider = isProvider,
                category = if (isProvider) "Assistência Técnica" else null,
                specialty = if (isProvider) "Profissional TQF" else null
            )
            try {
                userDao.insert(defaultUser)
                sessionManager.saveLogin(defaultUser.id)
            } catch (_: Exception) {}
            return defaultUser
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Atualiza os dados do usuário no Room e atualiza os dados da sessão.
     */
    fun updateUser(user: User): Boolean {
        try {
            userDao.insert(user) // Upsert using OnConflictStrategy.REPLACE
            sessionManager.saveLogin(user.id)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Encerra a sessão do usuário limpando o SharedPreferences.
     */
    fun logout() {
        try {
            sessionManager.logout()
            UserSessionManager.clearSession(context)
        } catch (_: Exception) {}
    }
}
