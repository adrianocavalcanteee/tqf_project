package com.tqfapp.android.data.model

import android.net.Uri
import com.tqfapp.android.business.ServiceStatus

/**
 * Nomes de domínio do fluxo de solicitação. A persistência continua na entidade Room [Service]
 * (tabela "servicos") para não duplicar dados nem quebrar o cache local existente.
 */
typealias Solicitacao = Service
typealias StatusSolicitacao = ServiceStatus

/** Aba "Histórico" do Contratante: somente serviços concluídos. */
fun List<Solicitacao>.historicoDoCliente(clientId: String): List<Solicitacao> =
    filter { it.clientId == clientId && it.status == StatusSolicitacao.CONCLUIDO }

/** Aba "Contratações" do Prestador: somente serviços aceitos e em andamento. */
fun List<Solicitacao>.contratacoesDoPrestador(providerId: String): List<Solicitacao> =
    filter { it.providerId == providerId && it.status == StatusSolicitacao.EM_ANDAMENTO }

/** Link do WhatsApp liberado somente após o aceite (status EM_ANDAMENTO). */
fun Solicitacao.whatsAppUrl(message: String): String? {
    if (status != StatusSolicitacao.EM_ANDAMENTO) return null
    val digits = clientPhone?.filter { it.isDigit() }.orEmpty()
    if (digits.length < 10) return null
    val phone = if (digits.startsWith("55") && digits.length >= 12) digits else "55$digits"
    return "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"
}

fun StatusSolicitacao.label(): String = when (this) {
    StatusSolicitacao.PENDENTE -> "Pendente"
    StatusSolicitacao.EM_ANDAMENTO -> "Em andamento"
    StatusSolicitacao.CONCLUIDO -> "Concluído"
    StatusSolicitacao.RECUSADO -> "Recusado"
    StatusSolicitacao.CANCELADO -> "Cancelado"
}
