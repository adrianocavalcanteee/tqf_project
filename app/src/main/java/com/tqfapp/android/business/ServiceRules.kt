package com.tqfapp.android.business

object ServiceRules {

    fun isValid(title: String, price: Double = 0.0): Boolean {
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

            ServiceStatus.CONCLUIDO -> false
            ServiceStatus.RECUSADO -> false
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