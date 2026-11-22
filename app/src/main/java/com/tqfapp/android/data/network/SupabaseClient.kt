package com.tqfapp.android.data.network

import com.tqfapp.android.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

/**
 * Cliente central do Supabase configurado para o aplicativo Android.
 * Gerencia a autenticação (Auth), banco de dados (PostgREST) e mídia (Storage no bucket tqf-media)
 * utilizando a URL e a Publishable Key obtidas do BuildConfig (local.properties).
 */
object SupabaseClient {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                // Deep link do AndroidManifest (confirmação de e-mail e recuperação de senha):
                // sem isso o handleDeeplinks ignora o link e a sessão de recuperação não é importada.
                scheme = AUTH_CALLBACK_SCHEME
                host = AUTH_CALLBACK_HOST
            }
            install(Postgrest)
            install(Storage)
        }
    }

    const val AUTH_CALLBACK_SCHEME = "com.tqfapp.android"
    const val AUTH_CALLBACK_HOST = "login-callback"
    const val AUTH_CALLBACK_URL = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST"

    val auth: Auth get() = client.auth
    val postgrest: Postgrest get() = client.postgrest
    val storage: Storage get() = client.storage
}
