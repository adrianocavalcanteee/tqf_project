package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.business.ServiceRules
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.contratacoesDoPrestador
import com.tqfapp.android.data.model.historicoDoCliente
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.dto.DadosPrivadosUsuarioDto
import com.tqfapp.android.data.network.dto.SolicitacaoServicoDto
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Repositório da entidade Serviço/Solicitação integrado ao Supabase PostgREST, com cache no Room.
 * As funções são bloqueantes: chame-as fora da main thread (ex.: lifecycleScope + Dispatchers.IO).
 */
class ServiceRepository(private val context: Context) {

    private val serviceDao = AppDatabase.getDatabase(context).serviceDao()

    fun getAllServices(): List<Service> {
        return try {
            serviceDao.getAll()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun createDirectRequest(
        title: String,
        description: String,
        category: String,
        targetProviderId: String,
        providerName: String,
        clientName: String,
        clientPhone: String
    ): Result<Service> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            val clientId = authUser?.id ?: throw IllegalStateException("Usuário não autenticado.")
            // O mesmo ID é usado no Supabase e no Room para que aceite/recusa atualizem o registro certo.
            val requestId = UUID.randomUUID().toString()

            val dto = SolicitacaoServicoDto(
                id = requestId,
                clientId = clientId,
                targetProviderId = targetProviderId,
                category = category,
                title = title,
                description = description,
                status = ServiceStatus.PENDENTE.name
            )

            SupabaseClient.postgrest.from("solicitacoes_servico").insert(dto)

            val service = Service(
                id = requestId,
                title = title,
                description = description,
                category = category,
                clientId = clientId,
                clientName = clientName,
                clientPhone = clientPhone,
                providerId = targetProviderId,
                providerName = providerName,
                status = ServiceStatus.PENDENTE,
                date = "Hoje"
            )
            try { serviceDao.insert(service) } catch (_: Exception) {}

            Result.success(service)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createGeneralRequest(
        title: String,
        description: String,
        category: String
    ): Result<Unit> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            val clientId = authUser?.id ?: throw IllegalStateException("Usuário não autenticado.")

            val dto = SolicitacaoServicoDto(
                clientId = clientId,
                category = category,
                title = title,
                description = description,
                status = ServiceStatus.PENDENTE.name
            )

            SupabaseClient.postgrest.from("solicitacoes_servico").insert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Todas as solicitações do contratante (qualquer status). */
    fun getClientRequests(clientId: String): List<Service> = runBlocking {
        val remote = try {
            SupabaseClient.postgrest.from("solicitacoes_servico")
                .select { filter { eq("client_id", clientId) } }
                .decodeList<SolicitacaoServicoDto>()
        } catch (_: Exception) { null }

        if (remote != null) {
            val services = remote.map { mergeWithLocal(it) }
            services.forEach { try { serviceDao.insert(it) } catch (_: Exception) {} }
            return@runBlocking services
        }

        try { serviceDao.getAll().filter { it.clientId == clientId } } catch (_: Exception) { emptyList() }
    }

    /** Aba "Histórico" do contratante: somente CONCLUIDO. */
    fun getClientHistory(clientId: String): List<Service> =
        getClientRequests(clientId).historicoDoCliente(clientId)

    /** Solicitações do contratante recusadas pelo prestador (alimentam o motor de recomendação). */
    fun getRefusedClientRequests(clientId: String): List<Service> =
        getClientRequests(clientId).filter { it.status == ServiceStatus.RECUSADO }

    /**
     * Feed do prestador: chamados PENDENTE direcionados a ele, ou chamados gerais (sem prestador alvo)
     * da sua categoria.
     */
    fun getProviderRequests(providerId: String, providerCategory: String?): List<Service> = runBlocking {
        fun isForProvider(targetId: String?, category: String): Boolean =
            targetId == providerId ||
                (targetId.isNullOrBlank() && providerCategory != null && category.equals(providerCategory, ignoreCase = true))

        val remote = try {
            SupabaseClient.postgrest.from("solicitacoes_servico")
                .select { filter { eq("status", ServiceStatus.PENDENTE.name) } }
                .decodeList<SolicitacaoServicoDto>()
        } catch (_: Exception) { null }

        if (remote != null) {
            return@runBlocking remote
                .filter { isForProvider(it.targetProviderId, it.category) }
                .map { mergeWithLocal(it) }
        }

        try {
            serviceDao.getAll().filter {
                it.status == ServiceStatus.PENDENTE && isForProvider(it.providerId, it.category)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Aba "Contratações" do prestador: somente EM_ANDAMENTO aceitos por ele. */
    fun getProviderContracts(providerId: String): List<Service> = runBlocking {
        val remote = try {
            SupabaseClient.postgrest.from("solicitacoes_servico")
                .select {
                    filter {
                        eq("accepted_provider_id", providerId)
                        eq("status", ServiceStatus.EM_ANDAMENTO.name)
                    }
                }
                .decodeList<SolicitacaoServicoDto>()
        } catch (_: Exception) { null }

        if (remote != null) {
            val services = remote.map { mergeWithLocal(it) }
            services.forEach { try { serviceDao.insert(it) } catch (_: Exception) {} }
            return@runBlocking services.contratacoesDoPrestador(providerId)
        }

        try { serviceDao.getAll().contratacoesDoPrestador(providerId) } catch (_: Exception) { emptyList() }
    }

    /**
     * Aceite: PENDENTE -> EM_ANDAMENTO. Retorna a solicitação atualizada (com o telefone do cliente
     * para liberar o WhatsApp) ou null se a transição não for permitida.
     */
    fun acceptRequest(service: Service): Service? = runBlocking {
        if (!ServiceRules.canChangeStatus(service.status, ServiceStatus.EM_ANDAMENTO)) return@runBlocking null
        val providerId = SupabaseClient.auth.currentUserOrNull()?.id ?: service.providerId

        try {
            SupabaseClient.postgrest.from("solicitacoes_servico")
                .update(
                    mapOf(
                        "status" to ServiceStatus.EM_ANDAMENTO.name,
                        "accepted_provider_id" to providerId
                    )
                ) {
                    filter { eq("id", service.id) }
                }
        } catch (_: Exception) {}

        val phone = service.clientPhone?.takeIf { it.isNotBlank() } ?: getUserPhone(service.clientId)
        val updated = service.copy(
            status = ServiceStatus.EM_ANDAMENTO,
            providerId = providerId,
            clientPhone = phone.ifBlank { null }
        )
        try { serviceDao.insert(updated) } catch (_: Exception) {}
        updated
    }

    /** Recusa: PENDENTE -> RECUSADO. O cliente recebe sugestões da mesma categoria. */
    fun refuseRequest(service: Service): Service? =
        changeStatus(service, ServiceStatus.RECUSADO)

    /** Conclusão (exclusiva do prestador): EM_ANDAMENTO -> CONCLUIDO. */
    fun finalizeRequest(service: Service): Service? =
        changeStatus(service, ServiceStatus.CONCLUIDO)

    fun getUserPhone(userId: String): String = runBlocking {
        try {
            val dto = SupabaseClient.postgrest.from("dados_privados_usuario")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<DadosPrivadosUsuarioDto>()
            dto?.phone ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun changeStatus(service: Service, newStatus: ServiceStatus): Service? = runBlocking {
        if (!ServiceRules.canChangeStatus(service.status, newStatus)) return@runBlocking null

        try {
            SupabaseClient.postgrest.from("solicitacoes_servico")
                .update(mapOf("status" to newStatus.name)) {
                    filter { eq("id", service.id) }
                }
        } catch (_: Exception) {}

        val updated = service.copy(status = newStatus)
        try { serviceDao.insert(updated) } catch (_: Exception) {}
        updated
    }

    /** Converte o DTO remoto preservando dados que só existem no cache local (nomes, telefone). */
    private fun mergeWithLocal(dto: SolicitacaoServicoDto): Service {
        val id = dto.id ?: UUID.randomUUID().toString()
        val local = try { serviceDao.getById(id) } catch (_: Exception) { null }
        val status = ServiceStatus.entries.firstOrNull { it.name.equals(dto.status, ignoreCase = true) }
            ?: ServiceStatus.PENDENTE

        return Service(
            id = id,
            title = dto.title,
            description = dto.description,
            category = dto.category,
            clientId = dto.clientId,
            clientName = local?.clientName?.takeIf { it.isNotBlank() } ?: "Cliente",
            clientPhone = local?.clientPhone,
            providerId = dto.acceptedProviderId ?: dto.targetProviderId ?: local?.providerId.orEmpty(),
            providerName = local?.providerName.orEmpty(),
            price = local?.price ?: 0.0,
            status = status,
            date = dto.createdAt?.take(10) ?: local?.date.orEmpty()
        )
    }
}
