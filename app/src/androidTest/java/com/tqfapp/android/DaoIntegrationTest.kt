package com.tqfapp.android

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Proposal
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.Service
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoIntegrationTest {

    private lateinit var database: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testServiceDaoCrud() = runBlocking {
        val serviceDao = database.serviceDao()

        val service = Service(
            id = "s1",
            title = "Reparo Elétrico",
            description = "Conserto de fiação residencial",
            price = 120.0,
            status = ServiceStatus.EM_ABERTO
        )

        // 1. Inserir um Service
        serviceDao.insert(service)

        // 2. Buscar por id
        val savedService = serviceDao.getById("s1")
        assertNotNull(savedService)
        assertEquals("Reparo Elétrico", savedService?.title)
        assertEquals(120.0, savedService?.price ?: 0.0, 0.001)

        // 3. Listar todos
        val allServices = serviceDao.getAll()
        assertEquals(1, allServices.size)
        assertEquals("s1", allServices[0].id)

        // 4. Atualizar
        val updatedService = savedService!!.copy(
            title = "Reparo Elétrico Atualizado",
            price = 150.0,
            status = ServiceStatus.EM_NEGOCIACAO
        )
        serviceDao.update(updatedService)

        val fetchedAfterUpdate = serviceDao.getById("s1")
        assertNotNull(fetchedAfterUpdate)
        assertEquals("Reparo Elétrico Atualizado", fetchedAfterUpdate?.title)
        assertEquals(150.0, fetchedAfterUpdate?.price ?: 0.0, 0.001)
        assertEquals(ServiceStatus.EM_NEGOCIACAO, fetchedAfterUpdate?.status)

        // 5. Excluir
        serviceDao.delete(fetchedAfterUpdate!!)
        val deletedService = serviceDao.getById("s1")
        assertNull(deletedService)
        assertTrue(serviceDao.getAll().isEmpty())
    }

    @Test
    fun testProposalDaoCrud() = runBlocking {
        val proposalDao = database.proposalDao()

        val proposal = Proposal(
            id = "p1",
            serviceId = "s100",
            providerId = "prov10",
            clientId = "cli20",
            value = 250.0,
            status = "PENDENTE"
        )

        // 1. Inserir uma Proposal
        proposalDao.insert(proposal)

        // 2. Listar todas
        val allProposals = proposalDao.getAll()
        assertEquals(1, allProposals.size)
        assertEquals("p1", allProposals[0].id)

        // 3. Buscar por serviceId
        val proposalsForService = proposalDao.getByService("s100")
        assertEquals(1, proposalsForService.size)
        assertEquals("p1", proposalsForService[0].id)

        // 4. Atualizar
        val updatedProposal = proposalsForService[0].copy(
            value = 230.0,
            status = "ACEITO"
        )
        proposalDao.update(updatedProposal)

        val fetchedAfterUpdate = proposalDao.getByService("s100")
        assertEquals(1, fetchedAfterUpdate.size)
        assertEquals(230.0, fetchedAfterUpdate[0].value, 0.001)
        assertEquals("ACEITO", fetchedAfterUpdate[0].status)

        // 5. Excluir
        proposalDao.delete(updatedProposal)
        assertTrue(proposalDao.getByService("s100").isEmpty())
        assertTrue(proposalDao.getAll().isEmpty())
    }

    @Test
    fun testReviewDaoCrud() = runBlocking {
        val reviewDao = database.reviewDao()

        val review = Review(
            id = "r1",
            serviceId = "s100",
            providerId = "prov10",
            clientId = "cli20",
            rating = 5,
            comment = "Serviço excelente e rápido!",
            date = "2025-03-30"
        )

        // 1. Inserir uma Review
        reviewDao.insert(review)

        // 2. Listar todas
        val allReviews = reviewDao.getAll()
        assertEquals(1, allReviews.size)
        assertEquals("r1", allReviews[0].id)

        // 3. Buscar por providerId
        val providerReviews = reviewDao.getByProvider("prov10")
        assertEquals(1, providerReviews.size)
        assertEquals("r1", providerReviews[0].id)

        // 4. Validar existsForService(serviceId)
        assertTrue(reviewDao.existsForService("s100"))
        assertFalse(reviewDao.existsForService("s999"))

        // 5. Excluir
        reviewDao.delete(review)
        assertFalse(reviewDao.existsForService("s100"))
        assertTrue(reviewDao.getAll().isEmpty())
    }
}
