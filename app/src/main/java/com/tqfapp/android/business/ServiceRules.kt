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
                newStatus == ServiceStatus.EM_ANDAMENTO || newStatus == ServiceStatus.RECUSADO

            ServiceStatus.EM_ANDAMENTO ->
                newStatus == ServiceStatus.CONCLUIDO

            ServiceStatus.CONCLUIDO -> false
            ServiceStatus.RECUSADO -> false
        }
    }

    fun statusAfterAcceptedProposal(
        current: ServiceStatus
    ): ServiceStatus {
        return if (current != ServiceStatus.CONCLUIDO) {
            ServiceStatus.EM_ANDAMENTO
        } else {
            current
        }
    }
}