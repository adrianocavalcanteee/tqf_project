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
            ServiceStatus.EM_ABERTO ->
                newStatus == ServiceStatus.EM_NEGOCIACAO

            ServiceStatus.EM_NEGOCIACAO ->
                newStatus == ServiceStatus.CONCLUIDO

            ServiceStatus.CONCLUIDO -> false
        }
    }

    fun statusAfterAcceptedProposal(
        current: ServiceStatus
    ): ServiceStatus {
        return if (current != ServiceStatus.CONCLUIDO) {
            ServiceStatus.EM_NEGOCIACAO
        } else {
            current
        }
    }
}