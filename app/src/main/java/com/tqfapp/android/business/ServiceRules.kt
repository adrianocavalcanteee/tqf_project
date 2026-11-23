package com.tqfapp.android.business

object ServiceRules {

    fun isValid(title: String, price: Double): Boolean {
        return title.isNotBlank() && price >= 0
    }

    fun canChangeStatus(
        current: ServiceStatus,
        newStatus: ServiceStatus
    ): Boolean {
        return when (current) {
            ServiceStatus.PENDENTE ->
                newStatus == ServiceStatus.EM_ANDAMENTO || newStatus == ServiceStatus.RECUSADO || newStatus == ServiceStatus.CANCELADO

            ServiceStatus.EM_ANDAMENTO ->
                newStatus == ServiceStatus.CONCLUIDO || newStatus == ServiceStatus.CANCELADO

            // Recusada pelo prestador: o contratante ainda pode encerrar (cancelar) o pedido.
            ServiceStatus.RECUSADO -> newStatus == ServiceStatus.CANCELADO

            ServiceStatus.CONCLUIDO,
            ServiceStatus.CANCELADO -> false
        }
    }

    fun statusAfterAcceptedProposal(
        current: ServiceStatus
    ): ServiceStatus {
        return if (current != ServiceStatus.CONCLUIDO && current != ServiceStatus.CANCELADO) {
            ServiceStatus.EM_ANDAMENTO
        } else {
            current
        }
    }
}
