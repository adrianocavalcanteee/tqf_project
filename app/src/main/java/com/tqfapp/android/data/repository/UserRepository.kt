package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.local.preferences.SessionManager
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.dto.DadosPrivadosUsuarioDto
import com.tqfapp.android.data.network.dto.PerfilPrestadorDto
import com.tqfapp.android.data.network.dto.UsuarioDto
import com.tqfapp.android.utils.UserSessionManager
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.runBlocking

/**
 * Repositório responsável pela camada de dados do Usuário (Autenticação e Cadastro).
 * Suporta o fluxo de confirmação de e-mail do Supabase Auth finalizando a criação
 * do perfil em public.usuarios, dados_privados_usuario e perfis_prestador após o primeiro login.
 */
class UserRepository(private val context: Context) {

    private val userDao = AppDatabase.getDatabase(context).userDao()
    private val sessionManager = SessionManager(context)

    /**
     * Preserva temporariamente os dados de cadastro até a confirmação do e-mail.
     */
    private fun savePendingRegistrationData(user: User) {
        val prefs = context.getSharedPreferences("tqf_pending_registration", Context.MODE_PRIVATE)
        val key = user.email.trim().lowercase()
        prefs.edit().apply {
            putString("${key}_name", user.name)
            putBoolean("${key}_is_provider", user.isProvider)
            putString("${key}_phone", user.phone)
            putString("${key}_category", user.category)
            putString("${key}_specialty", user.specialty)
            putString("${key}_bio", user.bio)
            putString("${key}_address", user.address)
            if (user.latitude != null) putFloat("${key}_latitude", user.latitude.toFloat())
            if (user.longitude != null) putFloat("${key}_longitude", user.longitude.toFloat())
            putString("${key}_profile_pic", user.profilePictureUri)
            apply()
        }
    }

    /**
     * Recupera os dados salvos do cadastro pendente por e-mail.
     */
    private fun getPendingRegistrationData(email: String): User? {
        val prefs = context.getSharedPreferences("tqf_pending_registration", Context.MODE_PRIVATE)
        val key = email.trim().lowercase()
        val name = prefs.getString("${key}_name", null) ?: return null
        val isProvider = prefs.getBoolean("${key}_is_provider", false)
        val phone = prefs.getString("${key}_phone", null)
        val category = prefs.getString("${key}_category", null)
        val specialty = prefs.getString("${key}_specialty", null)
        val bio = prefs.getString("${key}_bio", null)
        val address = prefs.getString("${key}_address", null)
        val lat = if (prefs.contains("${key}_latitude")) prefs.getFloat("${key}_latitude", 0f).toDouble() else null
        val lng = if (prefs.contains("${key}_longitude")) prefs.getFloat("${key}_longitude", 0f).toDouble() else null
        val profilePic = prefs.getString("${key}_profile_pic", null)

        return User(
            id = "",
            name = name,
            email = email,
            isProvider = isProvider,
            category = category,
            specialty = specialty,
            bio = bio,
            address = address,
            phone = phone,
            profilePictureUri = profilePic,
            latitude = lat,
            longitude = lng
        )
    }

    /**
     * Limpa os dados de cadastro pendente após a criação bem-sucedida do perfil público.
     */
    private fun clearPendingRegistrationData(email: String) {
        val prefs = context.getSharedPreferences("tqf_pending_registration", Context.MODE_PRIVATE)
        val key = email.trim().lowercase()
        prefs.edit().apply {
            remove("${key}_name")
            remove("${key}_is_provider")
            remove("${key}_phone")
            remove("${key}_category")
            remove("${key}_specialty")
            remove("${key}_bio")
            remove("${key}_address")
            remove("${key}_latitude")
            remove("${key}_longitude")
            remove("${key}_profile_pic")
            apply()
        }
    }

    /**
     * Registra o usuário no Supabase Auth. Salva os dados de perfil no Supabase se houver sessão ativa
     * ou preserva os dados com segurança para criação no primeiro login se a confirmação de e-mail for exigida.
     */
    fun registerUser(user: User, passwordRaw: String): Result<User> = runBlocking {
        try {
            // Salva dados do cadastro temporariamente
            savePendingRegistrationData(user)

            // 1. Criar a conta no Supabase Auth
            SupabaseClient.auth.signUpWith(Email) {
                email = user.email
                password = passwordRaw
            }

            val authUser = SupabaseClient.auth.currentUserOrNull()
            val authSession = SupabaseClient.auth.currentSessionOrNull()

            if (authUser == null) {
                return@runBlocking Result.failure(
                    IllegalStateException("Cadastro realizado! Foi enviado um link de confirmação para o seu e-mail. Por favor, confirme para ativar sua conta.")
                )
            }

            val authUid = authUser.id

            // Se a confirmação de e-mail estiver ativa e não houver sessão autenticada de imediato
            if (authSession == null) {
                return@runBlocking Result.failure(
                    IllegalStateException("Cadastro realizado com sucesso! Por favor, acesse seu e-mail para confirmar a conta antes do primeiro acesso.")
                )
            }

            // 2. Se houver sessão autenticada ativa, insere as tabelas públicas imediatamente no Supabase
            createPublicProfileTablesInSupabase(authUid, user)
            clearPendingRegistrationData(user.email)

            // Salva sessão local no SharedPreferences e Room
            sessionManager.saveLogin(authUid)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)

            val roomUser = user.copy(id = authUid)
            try { userDao.insert(roomUser) } catch (_: Exception) {}

            Result.success(roomUser)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("already registered", ignoreCase = true) == true -> "Este e-mail já está cadastrado no sistema."
                e.message?.contains("Password should be at least", ignoreCase = true) == true -> "A senha deve conter no mínimo 6 caracteres."
                e.message?.contains("row-level security", ignoreCase = true) == true -> "Cadastro realizado! Por favor, confirme seu e-mail antes de fazer o primeiro login."
                else -> e.message ?: "Falha ao realizar cadastro no Supabase."
            }
            Result.failure(IllegalArgumentException(errorMessage, e))
        }
    }

    /**
     * Autentica o usuário pelo Supabase Auth e finaliza a criação das tabelas públicas
     * caso o perfil ainda não exista no Supabase (primeiro login após confirmação de e-mail).
     */
    fun loginUser(email: String, passwordRaw: String): Result<User> = runBlocking {
        try {
            // 1. Autenticar com e-mail e senha no Supabase Auth
            SupabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = passwordRaw
            }

            val authUser = SupabaseClient.auth.currentUserOrNull()
                ?: return@runBlocking Result.failure(IllegalArgumentException("Falha na autenticação. Verifique suas credenciais."))

            val authUid = authUser.id

            // 2. Verificar se o perfil público já existe em public.usuarios
            var usuarioDto = try {
                SupabaseClient.postgrest.from("usuarios")
                    .select {
                        filter {
                            eq("id", authUid)
                        }
                    }
                    .decodeSingleOrNull<UsuarioDto>()
            } catch (_: Exception) { null }

            // 3. Se o perfil não existir (primeiro login pós-confirmação), cria usando a sessão recém-autenticada
            if (usuarioDto == null) {
                val pendingData = getPendingRegistrationData(email)
                val userToCreate = pendingData ?: User(
                    id = authUid,
                    name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    email = email,
                    isProvider = UserSessionManager.isProviderUser(context)
                )

                createPublicProfileTablesInSupabase(authUid, userToCreate)
                clearPendingRegistrationData(email)

                usuarioDto = UsuarioDto(
                    id = authUid,
                    name = userToCreate.name,
                    isProvider = userToCreate.isProvider,
                    profilePictureUrl = userToCreate.profilePictureUri
                )
            }

            // 4. Salvar sessão usando Supabase Auth como fonte principal
            sessionManager.saveLogin(authUid)
            UserSessionManager.saveUserSession(context, usuarioDto.isProvider, usuarioDto.name, email)

            val roomUser = User(
                id = authUid,
                name = usuarioDto.name,
                email = email,
                isProvider = usuarioDto.isProvider,
                profilePictureUri = usuarioDto.profilePictureUrl
            )
            try { userDao.insert(roomUser) } catch (_: Exception) {}

            Result.success(roomUser)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Invalid login credentials", ignoreCase = true) == true -> "E-mail ou senha incorretos."
                e.message?.contains("Email not confirmed", ignoreCase = true) == true -> "Seu e-mail ainda não foi confirmado. Por favor, verifique sua caixa de entrada."
                else -> e.message ?: "Falha na conexão com o Supabase."
            }
            Result.failure(IllegalArgumentException(errorMessage, e))
        }
    }

    /**
     * Método auxiliar para inserir o perfil público (usuarios, dados_privados_usuario e perfis_prestador)
     * no Supabase sob a sessão autenticada ativa.
     */
    private suspend fun createPublicProfileTablesInSupabase(authUid: String, user: User) {
        val usuarioDto = UsuarioDto(
            id = authUid,
            name = user.name,
            isProvider = user.isProvider,
            profilePictureUrl = user.profilePictureUri
        )
        SupabaseClient.postgrest.from("usuarios").insert(usuarioDto)

        val dadosPrivadosDto = DadosPrivadosUsuarioDto(
            id = authUid,
            phone = user.phone
        )
        SupabaseClient.postgrest.from("dados_privados_usuario").insert(dadosPrivadosDto)

        if (user.isProvider) {
            val perfilPrestadorDto = PerfilPrestadorDto(
                userId = authUid,
                category = user.category,
                specialty = user.specialty,
                bio = user.bio,
                officeAddress = user.address,
                latitude = user.latitude,
                longitude = user.longitude,
                portfolioUrls = user.portfolioUris,
                serviceTags = emptyList()
            )
            SupabaseClient.postgrest.from("perfis_prestador").insert(perfilPrestadorDto)
        }
    }

    /**
     * Obtém o usuário atualmente logado na sessão do Supabase Auth.
     */
    fun getCurrentUser(): User? = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            if (authUser != null) {
                val authUid = authUser.id
                val usuarioDto = try {
                    SupabaseClient.postgrest.from("usuarios")
                        .select {
                            filter {
                                eq("id", authUid)
                            }
                        }
                        .decodeSingleOrNull<UsuarioDto>()
                } catch (_: Exception) { null }

                if (usuarioDto != null) {
                    val email = authUser.email ?: "usuario@tqf.com"
                    UserSessionManager.saveUserSession(context, usuarioDto.isProvider, usuarioDto.name, email)
                    return@runBlocking User(
                        id = authUid,
                        name = usuarioDto.name,
                        email = email,
                        isProvider = usuarioDto.isProvider,
                        profilePictureUri = usuarioDto.profilePictureUrl
                    )
                }
            }

            // Fallback para Room local se offline
            val userId = sessionManager.getUserId()
            if (userId != null) {
                val localUser = userDao.getById(userId)
                if (localUser != null) return@runBlocking localUser
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Atualiza o usuário no Room local e no Supabase PostgREST.
     */
    fun updateUser(user: User): Boolean = runBlocking {
        try {
            userDao.insert(user)
            val usuarioDto = UsuarioDto(
                id = user.id,
                name = user.name,
                isProvider = user.isProvider,
                profilePictureUrl = user.profilePictureUri
            )
            try {
                SupabaseClient.postgrest.from("usuarios").upsert(usuarioDto)
            } catch (_: Exception) {}

            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encerra a sessão no Supabase Auth e no SharedPreferences.
     */
    fun logout() = runBlocking {
        try {
            SupabaseClient.auth.signOut()
        } catch (_: Exception) {}
        sessionManager.logout()
        UserSessionManager.clearSession(context)
    }
}
