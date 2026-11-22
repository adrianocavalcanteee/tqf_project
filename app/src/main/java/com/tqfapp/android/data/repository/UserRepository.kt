package com.tqfapp.android.data.repository

import android.content.Context
import android.util.Log
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.local.preferences.SessionManager
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.safeLogDescription
import com.tqfapp.android.data.network.userMessage
import com.tqfapp.android.data.network.dto.AvaliacaoDto
import com.tqfapp.android.data.network.dto.DadosPrivadosUsuarioDto
import com.tqfapp.android.data.network.dto.PerfilPrestadorDto
import com.tqfapp.android.data.network.dto.UsuarioDto
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.UserSessionManager
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.runBlocking

/**
 * Repositório responsável pela camada de dados do Usuário (Autenticação e Cadastro).
 * Integrado ao Supabase (Auth + PostgREST) com suporte a redirecionamento de deep link (login-callback),
 * tratamento amigável de erros, logs de diagnóstico seguros e persistência completa em usuarios, dados_privados_usuario e perfis_prestador.
 */
class UserRepository(private val context: Context) {

    private val userDao = AppDatabase.getDatabase(context).userDao()
    private val sessionManager = SessionManager(context)

    companion object {
        private const val TAG = "AuthFlow"
        private const val CURRENT_USER_CACHE_TTL_MS = 60_000L
        private const val KEY_RECOVERY_REQUESTED_AT = "recovery_requested_at"
        /** O link de recuperação do Supabase vale 1 hora por padrão. */
        private const val RECOVERY_LINK_VALIDITY_MS = 60 * 60 * 1000L

        /**
         * Usuário logado em memória: cada getCurrentUser() custa 3 consultas ao Supabase e várias telas
         * o chamam repetidamente. Invalidado em login, logout e qualquer alteração de perfil.
         */
        @Volatile
        private var cachedCurrentUser: Pair<Long, User>? = null

        fun invalidateCurrentUserCache() {
            cachedCurrentUser = null
        }

        /** Foto atual do usuário logado (para restaurar a tela se uma troca de foto falhar). */
        fun cachedProfilePicture(): String? = cachedCurrentUser?.second?.profilePictureUri
    }

    private fun savePendingRegistrationData(user: User) {
        val prefs = context.getSharedPreferences("tqf_pending_registration", Context.MODE_PRIVATE)
        val key = user.email.trim().lowercase()
        prefs.edit().apply {
            putString("${key}_name", user.name)
            putBoolean("${key}_is_provider", user.isProvider)
            putString("${key}_phone", user.telephone)
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
            telephone = phone,
            profilePictureUri = profilePic,
            latitude = lat,
            longitude = lng
        )
    }

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
     * Busca os prestadores reais cadastrados no Supabase PostgREST (public.usuarios + public.perfis_prestador)
     * com fallback de Geocoder para o endereço cadastrado e cache no Room local.
     */
    fun getProviders(): List<User> = runBlocking {
        try {
            val usuariosDtoList = try {
                SupabaseClient.postgrest.from("usuarios")
                    .select {
                        filter {
                            eq("is_provider", true)
                        }
                    }
                    .decodeList<UsuarioDto>()
            } catch (_: Exception) { emptyList() }

            val perfisDtoList = try {
                SupabaseClient.postgrest.from("perfis_prestador")
                    .select()
                    .decodeList<PerfilPrestadorDto>()
            } catch (_: Exception) { emptyList() }

            // Nota média e quantidade de avaliações reais por prestador
            val ratingsByProvider = try {
                SupabaseClient.postgrest.from("avaliacoes")
                    .select()
                    .decodeList<AvaliacaoDto>()
                    .groupBy { it.reviewedId }
            } catch (_: Exception) { emptyMap() }

            if (usuariosDtoList.isNotEmpty()) {
                val resultList = usuariosDtoList.map { uDto ->
                    val perfilDto = perfisDtoList.find { it.userId == uDto.id }
                    val reviews = ratingsByProvider[uDto.id].orEmpty()

                    var lat = perfilDto?.latitude
                    var lng = perfilDto?.longitude
                    val officeAddr = perfilDto?.officeAddress

                    if ((lat == null || lng == null || (lat == 0.0 && lng == 0.0)) && !officeAddr.isNullOrBlank()) {
                        val coords = LocationUtils.getCoordinatesFromAddress(context, officeAddr)
                        if (coords != null) {
                            lat = coords.latitude
                            lng = coords.longitude
                        }
                    }

                    User(
                        id = uDto.id,
                        name = uDto.name,
                        email = "",
                        isProvider = true,
                        category = perfilDto?.category,
                        specialty = perfilDto?.specialty,
                        bio = perfilDto?.bio,
                        services = perfilDto?.serviceTags ?: emptyList(),
                        address = officeAddr,
                        profilePictureUri = uDto.profilePictureUrl,
                        portfolioUris = perfilDto?.portfolioUrls ?: emptyList(),
                        rating = if (reviews.isEmpty()) 0.0 else reviews.map { it.rating }.average(),
                        reviewCount = reviews.size,
                        isAvailable = perfilDto?.isAvailable ?: true,
                        latitude = lat,
                        longitude = lng
                    )
                }
                resultList.forEach { try { userDao.insert(it) } catch (_: Exception) {} }
                return@runBlocking resultList
            }

            val localProviders = try { userDao.getAll().filter { it.isProvider } } catch (_: Exception) { emptyList() }
            if (localProviders.isNotEmpty()) return@runBlocking localProviders

            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Registra o usuário no Supabase Auth usando redirectUrl para deep link.
     */
    fun registerUser(user: User, passwordRaw: String): Result<User> = runBlocking {
        try {
            Log.d(TAG, "signUp iniciado para e-mail domain: ${user.email.substringAfter("@")}")
            savePendingRegistrationData(user)

            // Sessão de outra conta neste aparelho: o signUp com confirmação de e-mail não a substitui,
            // e currentUserOrNull() devolveria o usuário anterior (dados gravados no id errado).
            logout()

            SupabaseClient.auth.signUpWith(Email, redirectUrl = SupabaseClient.AUTH_CALLBACK_URL) {
                email = user.email
                password = passwordRaw
            }

            Log.d(TAG, "signUp resposta recebida com sucesso")

            val authUser = SupabaseClient.auth.currentUserOrNull()
            val authSession = SupabaseClient.auth.currentSessionOrNull()

            if (authUser == null || !authUser.email.equals(user.email, ignoreCase = true)) {
                return@runBlocking Result.failure(EmailConfirmationPendingException())
            }

            val authUid = authUser.id

            if (authSession == null) {
                return@runBlocking Result.failure(EmailConfirmationPendingException())
            }

            createPublicProfileTablesInSupabase(authUid, user)
            clearPendingRegistrationData(user.email)

            sessionManager.saveLogin(authUid)
            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)

            val roomUser = user.copy(id = authUid)
            try { userDao.insert(roomUser) } catch (_: Exception) {}

            Result.success(roomUser)
        } catch (e: EmailConfirmationPendingException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "signUp erro: ${e.safeLogDescription()}")
            val errorMsg = mapAuthExceptionToFriendlyMessage(e)
            Result.failure(IllegalArgumentException(errorMsg, e))
        }
    }

    /**
     * Autentica o usuário pelo Supabase Auth e finaliza a criação das tabelas públicas
     * caso o perfil ainda não exista no Supabase (primeiro login após confirmação de e-mail).
     */
    /**
     * @param expectedIsProvider aba escolhida no login. O tipo gravado em `usuarios.is_provider` é a fonte
     * da verdade: se a conta for de outro tipo, a sessão é encerrada e o login falha com mensagem clara.
     */
    fun loginUser(email: String, passwordRaw: String, expectedIsProvider: Boolean): Result<User> = runBlocking {
        invalidateCurrentUserCache()
        try {
            Log.d(TAG, "signIn iniciado para e-mail domain: ${email.substringAfter("@")}")
            SupabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = passwordRaw
            }
            Log.d(TAG, "signIn resposta recebida com sucesso")

            val authUser = SupabaseClient.auth.currentUserOrNull()
                ?: return@runBlocking Result.failure(IllegalArgumentException("E-mail ou senha incorretos."))

            val authUid = authUser.id

            var usuarioDto = try {
                SupabaseClient.postgrest.from("usuarios")
                    .select {
                        filter {
                            eq("id", authUid)
                        }
                    }
                    .decodeSingleOrNull<UsuarioDto>()
            } catch (_: Exception) { null }

            if (usuarioDto == null) {
                val pendingData = getPendingRegistrationData(email)
                val userToCreate = pendingData ?: User(
                    id = authUid,
                    name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    email = email,
                    // Sem dados do cadastro neste aparelho: usa a aba escolhida, nunca a sessão anterior.
                    isProvider = expectedIsProvider
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

            if (usuarioDto.isProvider != expectedIsProvider) {
                logout()
                val accountType = if (usuarioDto.isProvider) "prestador" else "contratante"
                return@runBlocking Result.failure(
                    AccountTypeMismatchException(
                        "Esta conta é de $accountType. Selecione \"${accountType.replaceFirstChar { it.uppercase() }}\" para entrar."
                    )
                )
            }

            sessionManager.saveLogin(authUid)
            UserSessionManager.saveUserSession(context, usuarioDto.isProvider, usuarioDto.name, email)

            val currentUser = getCurrentUser() ?: User(
                id = authUid,
                name = usuarioDto.name,
                email = email,
                isProvider = usuarioDto.isProvider,
                profilePictureUri = usuarioDto.profilePictureUrl
            )
            try { userDao.insert(currentUser) } catch (_: Exception) {}

            Result.success(currentUser)
        } catch (e: AccountTypeMismatchException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "signIn erro: ${e.safeLogDescription()}")
            val errorMsg = mapAuthExceptionToFriendlyMessage(e)
            Result.failure(IllegalArgumentException(errorMsg, e))
        }
    }

    private suspend fun createPublicProfileTablesInSupabase(authUid: String, user: User) {
        val storage = StorageRepository(context)
        // Foto do cadastro é uma URI local: sobe para o Storage antes de gravar (pasta profile/ não exige prestador).
        val (avatarUrl, _) = storage.uploadLocalMedia(user.profilePictureUri, emptyList())
        val usuarioDto = UsuarioDto(
            id = authUid,
            name = user.name,
            isProvider = user.isProvider,
            profilePictureUrl = avatarUrl
        )
        SupabaseClient.postgrest.from("usuarios").insert(usuarioDto)
        // Portfólio só pode subir depois que usuarios.is_provider existe (regra da pasta portfolio/).
        val portfolioUrls = if (user.isProvider) storage.uploadLocalMedia(null, user.portfolioUris).second else emptyList()

        val dadosPrivadosDto = DadosPrivadosUsuarioDto(
            id = authUid,
            phone = user.telephone,
            address = user.address
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
                portfolioUrls = portfolioUrls,
                serviceTags = user.services,
                isAvailable = user.isAvailable
            )
            SupabaseClient.postgrest.from("perfis_prestador").insert(perfilPrestadorDto)
        }
    }

    private fun mapAuthExceptionToFriendlyMessage(e: Throwable): String {
        val msg = e.message?.lowercase().orEmpty()
        return when {
            msg.contains("over_email_send_rate_limit") || msg.contains("rate limit") ->
                "Muitos e-mails de confirmação foram solicitados. Aguarde um pouco e tente novamente."
            msg.contains("already registered") || msg.contains("user_already_exists") || msg.contains("already exists") ->
                "Este e-mail já possui uma conta."
            msg.contains("invalid_email") || msg.contains("invalid email") ->
                "Informe um endereço de e-mail válido."
            msg.contains("network") || msg.contains("connect") || msg.contains("socket") || msg.contains("unknownhost") ->
                "Não foi possível concluir o cadastro. Verifique sua conexão e tente novamente."
            msg.contains("invalid login credentials") || msg.contains("invalid credentials") ->
                "E-mail ou senha incorretos."
            msg.contains("email not confirmed") ->
                "Seu e-mail ainda não foi confirmado. Por favor, verifique sua caixa de entrada."
            // Nunca repassa e.message bruto: contém URL, cabeçalhos e o token da requisição.
            else -> e.userMessage("Não foi possível concluir a operação. Tente novamente.")
        }
    }

    fun getCurrentUser(): User? = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            if (authUser != null) {
                val authUid = authUser.id
                cachedCurrentUser?.let { (loadedAt, user) ->
                    val fresh = System.currentTimeMillis() - loadedAt < CURRENT_USER_CACHE_TTL_MS
                    if (fresh && user.id == authUid) return@runBlocking user
                }
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
                    val email = authUser.email.orEmpty()

                    val dadosPrivados = try {
                        SupabaseClient.postgrest.from("dados_privados_usuario")
                            .select { filter { eq("id", authUid) } }
                            .decodeSingleOrNull<DadosPrivadosUsuarioDto>()
                    } catch (_: Exception) { null }

                    var category: String? = null
                    var specialty: String? = null
                    var bio: String? = null
                    var address: String? = dadosPrivados?.address
                    val phone: String? = dadosPrivados?.phone
                    var latitude: Double? = null
                    var longitude: Double? = null
                    var portfolioUris: List<String> = emptyList()
                    var services: List<String> = emptyList()
                    var isAvailable = true

                    if (usuarioDto.isProvider) {
                        val perfil = try {
                            SupabaseClient.postgrest.from("perfis_prestador")
                                .select { filter { eq("user_id", authUid) } }
                                .decodeSingleOrNull<PerfilPrestadorDto>()
                        } catch (_: Exception) { null }

                        if (perfil != null) {
                            category = perfil.category
                            specialty = perfil.specialty
                            bio = perfil.bio
                            if (address.isNullOrBlank()) address = perfil.officeAddress
                            latitude = perfil.latitude
                            longitude = perfil.longitude
                            portfolioUris = perfil.portfolioUrls
                            services = perfil.serviceTags
                            isAvailable = perfil.isAvailable
                        }
                    }

                    UserSessionManager.saveUserSession(context, usuarioDto.isProvider, usuarioDto.name, email)
                    val loaded = User(
                        id = authUid,
                        name = usuarioDto.name,
                        email = email,
                        isProvider = usuarioDto.isProvider,
                        category = category,
                        specialty = specialty,
                        bio = bio,
                        services = services,
                        address = address,
                        telephone = phone,
                        profilePictureUri = usuarioDto.profilePictureUrl,
                        portfolioUris = portfolioUris,
                        isAvailable = isAvailable,
                        latitude = latitude,
                        longitude = longitude
                    )
                    cachedCurrentUser = System.currentTimeMillis() to loaded
                    return@runBlocking loaded
                }
            }

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

    fun updateUser(user: User): Boolean = runBlocking {
        invalidateCurrentUserCache()
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

            val dadosPrivadosDto = DadosPrivadosUsuarioDto(
                id = user.id,
                phone = user.telephone,
                address = user.address
            )
            try {
                SupabaseClient.postgrest.from("dados_privados_usuario").upsert(dadosPrivadosDto)
            } catch (_: Exception) {}

            if (user.isProvider) {
                val perfilPrestadorDto = PerfilPrestadorDto(
                    userId = user.id,
                    category = user.category,
                    specialty = user.specialty,
                    bio = user.bio,
                    officeAddress = user.address,
                    latitude = user.latitude,
                    longitude = user.longitude,
                    portfolioUrls = user.portfolioUris,
                    serviceTags = user.services,
                    isAvailable = user.isAvailable
                )
                try {
                    SupabaseClient.postgrest.from("perfis_prestador").upsert(perfilPrestadorDto)
                } catch (_: Exception) {}
            }

            UserSessionManager.saveUserSession(context, user.isProvider, user.name, user.email)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * "Esqueceu a senha?": o Supabase envia um link que volta ao app (login-callback). Marca o pedido
     * neste aparelho para a LoginActivity abrir a tela de nova senha quando o link chegar.
     */
    fun requestPasswordReset(email: String): Result<Unit> = runBlocking {
        try {
            SupabaseClient.auth.resetPasswordForEmail(email, redirectUrl = SupabaseClient.AUTH_CALLBACK_URL)
            recoveryPrefs().edit().putLong(KEY_RECOVERY_REQUESTED_AT, System.currentTimeMillis()).apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "resetPassword erro: ${e.safeLogDescription()}")
            Result.failure(IllegalArgumentException(mapAuthExceptionToFriendlyMessage(e), e))
        }
    }

    /** true (uma vez) se houve um pedido de recuperação recente neste aparelho. */
    fun consumePasswordRecoveryRequest(): Boolean {
        val prefs = recoveryPrefs()
        val requestedAt = prefs.getLong(KEY_RECOVERY_REQUESTED_AT, 0L)
        prefs.edit().remove(KEY_RECOVERY_REQUESTED_AT).apply()
        return requestedAt > 0 && System.currentTimeMillis() - requestedAt < RECOVERY_LINK_VALIDITY_MS
    }

    /** Tela "Nova senha": usa a sessão de recuperação aberta pelo link e encerra a sessão em seguida. */
    fun updatePassword(newPassword: String): Result<Unit> = runBlocking {
        try {
            SupabaseClient.auth.currentUserOrNull()
                ?: throw IllegalStateException("Link de recuperação expirado. Solicite um novo pelo login.")
            SupabaseClient.auth.updateUser { password = newPassword }
            logout()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updatePassword erro: ${e.safeLogDescription()}")
            Result.failure(IllegalArgumentException(e.userMessage("Não foi possível alterar a senha. Solicite um novo link."), e))
        }
    }

    private fun recoveryPrefs() = context.getSharedPreferences("tqf_password_recovery", Context.MODE_PRIVATE)

    /**
     * Troca a foto de perfil: envia ao Supabase Storage (bucket tqf-media) e grava a URL pública em
     * usuarios.profile_picture_url. Retorna a URL para a tela exibir.
     */
    fun updateProfilePicture(imageUri: android.net.Uri): Result<String> {
        val url = StorageRepository(context).uploadProfilePicture(imageUri).getOrElse {
            return Result.failure(IllegalStateException(it.userMessage("Não foi possível enviar a foto. Tente novamente."), it))
        }
        val user = getCurrentUser()
            ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        return if (updateUser(user.copy(profilePictureUri = url))) Result.success(url)
        else Result.failure(IllegalStateException("A foto foi enviada, mas não foi possível salvá-la no perfil."))
    }

    /** Switch "Disponível para chamados": grava em perfis_prestador.is_available (RLS: só o próprio prestador). */
    fun setAvailability(isAvailable: Boolean): Result<Unit> = runBlocking {
        try {
            val userId = SupabaseClient.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Usuário não autenticado.")
            SupabaseClient.postgrest.from("perfis_prestador")
                .update(mapOf("is_available" to isAvailable)) { filter { eq("user_id", userId) } }
            invalidateCurrentUserCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.userMessage("Não foi possível alterar sua disponibilidade."), e))
        }
    }

    fun logout() = runBlocking {
        invalidateCurrentUserCache()
        try {
            SupabaseClient.auth.signOut()
        } catch (_: Exception) {}
        sessionManager.logout()
        UserSessionManager.clearSession(context)
        // Cache local (usuários, solicitações, avaliações) é por conta: não pode vazar para o próximo login.
        try { AppDatabase.getDatabase(context).clearAllTables() } catch (_: Exception) {}
    }
}

/** Login pela aba errada: a conta existe, mas é de outro tipo (contratante x prestador). */
class AccountTypeMismatchException(message: String) : IllegalArgumentException(message)

/**
 * Cadastro criado no Supabase Auth, mas a conta só pode logar após confirmar o e-mail.
 * Não é erro: a tela de cadastro deve levar o usuário ao login.
 */
class EmailConfirmationPendingException :
    IllegalStateException("Cadastro realizado! Confirme seu e-mail pelo link enviado e depois faça login.")
