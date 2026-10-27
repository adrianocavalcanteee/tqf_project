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

    // Regra 1: Serviço novo começa em EM_ABERTO
    @Test
    fun testNewServiceStatusIsEmAberto() {
        val service = Service(
            id = "s1",
            title = "Limpeza de Ar Condicionado",
            description = "Higienização completa",
            price = 150.0
        )
        assertEquals(ServiceStatus.EM_ABERTO, service.status)
    }

    // Regra 2: Serviço só pode seguir o fluxo: EM_ABERTO -> EM_NEGOCIACAO -> CONCLUIDO
    @Test
    fun testServiceStatusFlow() {
        // Fluxos válidos
        assertTrue(ServiceRules.canChangeStatus(ServiceStatus.EM_ABERTO, ServiceStatus.EM_NEGOCIACAO))
        assertTrue(ServiceRules.canChangeStatus(ServiceStatus.EM_NEGOCIACAO, ServiceStatus.CONCLUIDO))

        // Fluxos inválidos (pular etapas ou voltar status)
        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.EM_ABERTO, ServiceStatus.CONCLUIDO))
        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.EM_NEGOCIACAO, ServiceStatus.EM_ABERTO))
        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.CONCLUIDO, ServiceStatus.EM_NEGOCIACAO))
        assertFalse(ServiceRules.canChangeStatus(ServiceStatus.CONCLUIDO, ServiceStatus.EM_ABERTO))
    }

    // Regra 3: Serviço sem título ou com preço negativo deve ser inválido
    @Test
    fun testServiceValidation() {
        assertFalse(ServiceRules.isValid("", 100.0))
        assertFalse(ServiceRules.isValid("   ", 100.0))
        assertFalse(ServiceRules.isValid("Pintura de Parede", -50.0))

        assertTrue(ServiceRules.isValid("Pintura de Parede", 0.0))
        assertTrue(ServiceRules.isValid("Pintura de Parede", 200.0))
    }

    // Regra 4: Proposta deve ter valor maior que zero
    @Test
    fun testProposalValueValidation() {
        assertFalse(ProposalRules.isValid(0.0))
        assertFalse(ProposalRules.isValid(-10.0))
        assertTrue(ProposalRules.isValid(50.0))
    }

    // Regra 5: Não pode criar proposta para serviço CONCLUIDO
    @Test
    fun testProposalCreationRestrictions() {
        assertTrue(ProposalRules.canCreateProposal(ServiceStatus.EM_ABERTO))
        assertTrue(ProposalRules.canCreateProposal(ServiceStatus.EM_NEGOCIACAO))
        assertFalse(ProposalRules.canCreateProposal(ServiceStatus.CONCLUIDO))
    }

    // Regra 6: Ao aceitar uma proposta, o status do serviço deve ir para EM_NEGOCIACAO
    @Test
    fun testAcceptedProposalChangesStatusToEmNegociacao() {
        assertEquals(ServiceStatus.EM_NEGOCIACAO, ServiceRules.statusAfterAcceptedProposal(ServiceStatus.EM_ABERTO))
        assertEquals(ServiceStatus.EM_NEGOCIACAO, ServiceRules.statusAfterAcceptedProposal(ServiceStatus.EM_NEGOCIACAO))
        assertEquals(ServiceStatus.CONCLUIDO, ServiceRules.statusAfterAcceptedProposal(ServiceStatus.CONCLUIDO))
    }

    // Regra 7: Avaliação só pode ser feita se o serviço estiver CONCLUIDO
    @Test
    fun testReviewOnlyAllowedWhenServiceIsConcluido() {
        assertFalse(ReviewRules.canReview(ServiceStatus.EM_ABERTO))
        assertFalse(ReviewRules.canReview(ServiceStatus.EM_NEGOCIACAO))
        assertTrue(ReviewRules.canReview(ServiceStatus.CONCLUIDO))
    }

    // Regra 8: Nota deve estar entre 1 e 5
    @Test
    fun testReviewRatingValidation() {
        assertFalse(ReviewRules.isValidRating(0))
        assertFalse(ReviewRules.isValidRating(6))
        assertFalse(ReviewRules.isValidRating(-1))

        assertTrue(ReviewRules.isValidRating(1))
        assertTrue(ReviewRules.isValidRating(3))
        assertTrue(ReviewRules.isValidRating(5))
    }

    // Regra 9: Review deve estar vinculada ao serviceId, providerId e clientId
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

    // Regra 10: Impedir mais de uma avaliação para o mesmo serviceId
    @Test
    fun testPreventMultipleReviewsForSameService() {
        // Se já foi avaliado (alreadyReviewed = true), não pode criar avaliação
        assertFalse(ReviewRules.canCreateReview(ServiceStatus.CONCLUIDO, alreadyReviewed = true))

        // Se o serviço está concluído e ainda não foi avaliado, pode criar
        assertTrue(ReviewRules.canCreateReview(ServiceStatus.CONCLUIDO, alreadyReviewed = false))

        // Se o serviço não está concluído, mesmo se não tiver sido avaliado, não pode criar
        assertFalse(ReviewRules.canCreateReview(ServiceStatus.EM_ABERTO, alreadyReviewed = false))
    }
}
