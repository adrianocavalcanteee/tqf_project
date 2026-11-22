package com.tqfapp.android

import com.tqfapp.android.business.ProposalRules
import com.tqfapp.android.business.ReviewRules
import com.tqfapp.android.business.ServiceRules
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessRulesTest {

    @Test
    fun testNewServiceStatusIsPendente() {
        val service = Service(
            id = "s1",
            title = "Limpeza de Ar Condicionado",
            description = "Higienização completa",
            price = 150.0
        )
        assertEquals(ServiceStatus.PENDENTE, service.status)
    }

    @Test
    fun testServiceStatusFlow() {
        assertTrue(ServiceRules.canChangeStatus(ServiceStatus.PENDENTE, ServiceStatus.EM_ANDAMENTO))
        assertTrue(ServiceRules.canChangeStatus(ServiceStatus.PENDENTE, ServiceStatus.RECUSADO))
        assertTrue(ServiceRules.canChangeStatus(ServiceStatus.EM_ANDAMENTO, ServiceStatus.CONCLUIDO))

        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.PENDENTE, ServiceStatus.CONCLUIDO))
        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.EM_ANDAMENTO, ServiceStatus.PENDENTE))
        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.CONCLUIDO, ServiceStatus.EM_ANDAMENTO))
    }

    @Test
    fun testServiceValidation() {
        assertFalse(ServiceRules.isValid("", 100.0))
        assertFalse(ServiceRules.isValid("   ", 100.0))
        assertFalse(ServiceRules.isValid("Pintura de Parede", -50.0))

        assertTrue(ServiceRules.isValid("Pintura de Parede", 0.0))
        assertTrue(ServiceRules.isValid("Pintura de Parede", 200.0))
    }

    @Test
    fun testProposalValueValidation() {
        assertFalse(ProposalRules.isValid(0.0))
        assertFalse(ProposalRules.isValid(-10.0))
        assertTrue(ProposalRules.isValid(50.0))
    }

    @Test
    fun testProposalCreationRestrictions() {
        assertTrue(ProposalRules.canCreateProposal(ServiceStatus.PENDENTE))
        assertTrue(ProposalRules.canCreateProposal(ServiceStatus.EM_ANDAMENTO))
        assertFalse(ProposalRules.canCreateProposal(ServiceStatus.CONCLUIDO))
    }

    @Test
    fun testAcceptedProposalChangesStatusToEmAndamento() {
        assertEquals(ServiceStatus.EM_ANDAMENTO, ServiceRules.statusAfterAcceptedProposal(ServiceStatus.PENDENTE))
        assertEquals(ServiceStatus.EM_ANDAMENTO, ServiceRules.statusAfterAcceptedProposal(ServiceStatus.EM_ANDAMENTO))
        assertEquals(ServiceStatus.CONCLUIDO, ServiceRules.statusAfterAcceptedProposal(ServiceStatus.CONCLUIDO))
    }

    @Test
    fun testReviewOnlyAllowedWhenServiceIsConcluido() {
        assertFalse(ReviewRules.canReview(ServiceStatus.PENDENTE))
        assertFalse(ReviewRules.canReview(ServiceStatus.EM_ANDAMENTO))
        assertTrue(ReviewRules.canReview(ServiceStatus.CONCLUIDO))
    }

    @Test
    fun testReviewRatingValidation() {
        assertFalse(ReviewRules.isValidRating(0))
        assertFalse(ReviewRules.isValidRating(6))
        assertFalse(ReviewRules.isValidRating(-1))

        assertTrue(ReviewRules.isValidRating(1))
        assertTrue(ReviewRules.isValidRating(3))
        assertTrue(ReviewRules.isValidRating(5))
    }

    @Test
    fun testReviewStructureWithRequiredIds() {
        val review = Review(
            id = "r1",
            serviceId = "s123",
            providerId = "p456",
            clientId = "c789",
            rating = 5,
            comment = "Excelente trabalho!",
            date = "2025-03-30"
        )
        assertEquals("s123", review.serviceId)
        assertEquals("p456", review.providerId)
        assertEquals("c789", review.clientId)
    }

    @Test
    fun testPreventMultipleReviewsForSameService() {
        assertFalse(ReviewRules.canCreateReview(ServiceStatus.CONCLUIDO, alreadyReviewed = true))
        assertTrue(ReviewRules.canCreateReview(ServiceStatus.CONCLUIDO, alreadyReviewed = false))
        assertFalse(ReviewRules.canCreateReview(ServiceStatus.PENDENTE, alreadyReviewed = false))
    }
}
