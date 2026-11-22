package com.tqfapp.android.business

object ReviewRules {

    fun canReview(serviceStatus: ServiceStatus): Boolean {
        return serviceStatus == ServiceStatus.CONCLUIDO
    }

    fun isValidRating(rating: Int): Boolean {
        return rating in 1..5
    }

    fun canCreateReview(
        serviceStatus: ServiceStatus,
        alreadyReviewed: Boolean
    ): Boolean {
        return serviceStatus == ServiceStatus.CONCLUIDO &&
                !alreadyReviewed
    }
}