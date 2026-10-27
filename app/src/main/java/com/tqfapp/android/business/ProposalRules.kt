package com.tqfapp.android.business

object ProposalRules {

    fun isValid(value: Double): Boolean {
        return value > 0
    }

    fun canCreateProposal(serviceStatus: ServiceStatus): Boolean {
        return serviceStatus != ServiceStatus.CONCLUIDO
    }
}