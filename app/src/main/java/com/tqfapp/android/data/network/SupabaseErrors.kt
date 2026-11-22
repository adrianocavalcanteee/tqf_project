package com.tqfapp.android.data.network

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException

/** Código do Postgres para `raise exception` (mensagens de regra de negócio escritas nas RPCs). */
private const val RAISE_EXCEPTION = "P0001"

/**
 * Mensagem segura para exibir na UI. A `message` das exceções do Supabase inclui URL, cabeçalhos e o
 * token JWT da requisição: nunca deve ser mostrada nem logada. Só repassa as mensagens das RPCs
 * (escritas para o usuário) e as de validação do próprio app.
 */
fun Throwable.userMessage(default: String): String = when (this) {
    is PostgrestRestException -> if (code == RAISE_EXCEPTION) error.ifBlank { default } else default
    is HttpRequestException -> "Sem conexão com o servidor. Verifique sua internet e tente novamente."
    is RestException -> default
    is IllegalStateException, is IllegalArgumentException -> message?.takeIf { it.isNotBlank() } ?: default
    else -> default
}

/** Descrição para log sem dados sensíveis (tipo + código do erro, sem headers/token). */
fun Throwable.safeLogDescription(): String = when (this) {
    is PostgrestRestException -> "${javaClass.simpleName} code=$code"
    is RestException -> "${javaClass.simpleName} error=$error"
    else -> javaClass.simpleName
}
