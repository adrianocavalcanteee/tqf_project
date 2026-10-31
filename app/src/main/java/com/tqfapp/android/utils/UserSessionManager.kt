package com.tqfapp.android.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gerenciador de sessão local para salvar e consultar o perfil/tipo do usuário logado.
 */
object UserSessionManager {

    private const val PREF_NAME = "tqf_user_session"
    private const val KEY_IS_PROVIDER = "key_is_provider"
    private const val KEY_USER_NAME = "key_user_name"
    private const val KEY_USER_EMAIL = "key_user_email"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Salva as informações da sessão ao realizar o login.
     */
    fun saveUserSession(context: Context, isProvider: Boolean, name: String? = null, email: String? = null) {
        getPreferences(context).edit().apply {
            putBoolean(KEY_IS_PROVIDER, isProvider)
            name?.let { putString(KEY_USER_NAME, it) }
            email?.let { putString(KEY_USER_EMAIL, it) }
            apply()
        }
    }

    /**
     * Verifica se o usuário logado é do tipo "Prestador de Serviço" (true) ou "Contratante" (false).
     */
    fun isProviderUser(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_PROVIDER, false)
    }

    /**
     * Limpa os dados salvos na sessão ao efetuar Logout.
     */
    fun clearSession(context: Context) {
        getPreferences(context).edit().clear().apply()
    }
}
