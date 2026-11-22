package com.tqfapp.android

import com.tqfapp.android.business.RecommendationEngine
import com.tqfapp.android.data.model.Solicitacao
import com.tqfapp.android.data.model.StatusSolicitacao
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.model.contratacoesDoPrestador
import com.tqfapp.android.data.model.historicoDoCliente
import com.tqfapp.android.data.model.resumoParaPrestador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolicitacaoFlowTest {

    private fun provider(
        id: String,
        category: String,
        rating: Double = 4.5,
        available: Boolean = true,
        distanceKm: Double? = null
    ) = User(
        id = id,
        name = "Prestador $id",
        email = "",
        isProvider = true,
        category = category,
        rating = rating,
        isAvailable = available,
        distanceKm = distanceKm
    )

    private fun solicitacao(id: String, status: StatusSolicitacao, clientId: String = "c", providerId: String = "p") =
        Solicitacao(id = id, title = "t$id", description = "d", clientId = clientId, providerId = providerId, status = status)

    @Test
    fun recommendationKeepsSameCategoryAndExcludesRefusingProvider() {
        val providers = listOf(
            provider("recusou", "Reformas e Reparos", rating = 5.0),
            provider("a", "Reformas e Reparos", rating = 4.2),
            provider("b", "reformas e reparos", rating = 4.9),
            provider("outraCategoria", "Eventos", rating = 5.0),
            provider("indisponivel", "Reformas e Reparos", rating = 5.0, available = false)
        )

        val result = RecommendationEngine.suggest(providers, "Reformas e Reparos", excludedProviderId = "recusou")

        assertEquals(listOf("b", "a"), result.map { it.id })
    }

    @Test
    fun recommendationBreaksRatingTiesByDistance() {
        val providers = listOf(
            provider("longe", "Eventos", rating = 4.8, distanceKm = 9.0),
            provider("semGps", "Eventos", rating = 4.8),
            provider("perto", "Eventos", rating = 4.8, distanceKm = 1.0)
        )

        val result = RecommendationEngine.suggest(providers, "Eventos", excludedProviderId = null)

        assertEquals(listOf("perto", "longe", "semGps"), result.map { it.id })
    }

    @Test
    fun clientHistoryShowsOnlyConcluded() {
        val all = StatusSolicitacao.entries.map { solicitacao(it.name, it) } +
                solicitacao("deOutroCliente", StatusSolicitacao.CONCLUIDO, clientId = "x")

        val history = all.historicoDoCliente("c")

        assertEquals(listOf("CONCLUIDO"), history.map { it.id })
    }

    @Test
    fun providerContractsShowOnlyInProgress() {
        val all = StatusSolicitacao.entries.map { solicitacao(it.name, it) } +
                solicitacao("deOutroPrestador", StatusSolicitacao.EM_ANDAMENTO, providerId = "x")

        val contracts = all.contratacoesDoPrestador("p")

        assertEquals(listOf("EM_ANDAMENTO"), contracts.map { it.id })
    }

    @Test
    fun priceLabelIsTransparent() {
        assertEquals("Valor negociável", provider("a", "Eventos").priceLabel())
        assertTrue(provider("a", "Eventos").copy(hourlyRate = 80.0).priceLabel().endsWith("/ hora"))
    }

    @Test
    fun providerSummaryShowsOpenRequestUrgencyLocationAndDistance() {
        val aberto = solicitacao("1", StatusSolicitacao.PENDENTE, providerId = "")
            .copy(urgency = "HOJE", locationText = "Centro, Recife")

        assertEquals("Pedido aberto • Hoje • Centro, Recife • 2,5 km", aberto.resumoParaPrestador(2.5))
        assertEquals("Solicitação direta", solicitacao("2", StatusSolicitacao.PENDENTE).resumoParaPrestador(null))
    }
}
