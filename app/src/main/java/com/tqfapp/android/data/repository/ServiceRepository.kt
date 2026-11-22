package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.business.ServiceRules
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.business.Urgency
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.contratacoesDoPrestador
import com.tqfapp.android.data.model.historicoDoCliente
import com.tqfapp.android.data.model.label
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.userMessage
import com.tqfapp.android.data.network.dto.DadosPrivadosUsuarioDto
import com.tqfapp.android.data.network.dto.SolicitacaoServicoDto
import com.tqfapp.android.data.network.dto.UsuarioDto
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    /**
     * Cria uma solicitação PENDENTE. Com [targetProviderId] é direcionada ao prestador; sem ele é um
     * pedido aberto, visível a todos os prestadores da [category] (RLS em solicitacoes_servico).
     */
    fun createRequest(
        title: String,
        description: String,
        category: String,
        targetProviderId: String?,
        providerName: String,
        clientName: String,
        clientPhone: String,
        urgency: Urgency? = null,
        locationText: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<Service> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
            val clientId = authUser?.id ?: throw IllegalStateException("Usuário não autenticado.")
            // O mesmo ID é usado no Supabase e no Room para que aceite/recusa atualizem o registro certo.
            val requestId = UUID.randomUUID().toString()
            val location = locationText?.trim()?.takeIf { it.isNotEmpty() }

            val dto = SolicitacaoServicoDto(
                id = requestId,
                clientId = clientId,
                targetProviderId = targetProviderId?.takeIf { it.isNotBlank() },
                category = category,
                title = title,
                description = description,
                status = ServiceStatus.PENDENTE.name,
                urgency = urgency?.name,
                locationText = location,
                latitude = latitude,
                longitude = longitude
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
                providerId = targetProviderId.orEmpty(),
                providerName = providerName,
                status = ServiceStatus.PENDENTE,
                date = "Hoje",
                urgency = urgency?.name,
                locationText = location,
                latitude = latitude,
                longitude = longitude
            )
            try { serviceDao.insert(service) } catch (_: Exception) {}

            Result.success(service)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.userMessage("Não foi possível enviar a solicitação. Tente novamente."), e))
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
            val services = withPartyNames(remote.map { mergeWithLocal(it) })
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
    fun getProviderRequests(providerId: String, providerCategory: String?): List<Service> =
        sortForProvider(loadProviderRequests(providerId, providerCategory))

    /** Ordena o feed do prestador: mais urgentes primeiro, depois os mais recentes. */
    private fun sortForProvider(requests: List<Service>): List<Service> =
        requests.sortedWith(
            compareBy<Service> { Urgency.fromName(it.urgency)?.ordinal ?: Urgency.entries.size }
                .thenByDescending { it.date }
        )

    private fun loadProviderRequests(providerId: String, providerCategory: String?): List<Service> = runBlocking {
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
            val services = withPartyNames(remote.map { mergeWithLocal(it) })
            services.forEach { try { serviceDao.insert(it) } catch (_: Exception) {} }
            return@runBlocking services.contratacoesDoPrestador(providerId)
        }

        try { serviceDao.getAll().contratacoesDoPrestador(providerId) } catch (_: Exception) { emptyList() }
    }

    /** Serviços concluídos pelo prestador: status CONCLUIDO aceitos por ele. */
    fun getProviderCompletedServices(providerId: String): List<Service> = runBlocking {
        val remote = try {
            SupabaseClient.postgrest.from("solicitacoes_servico")
                .select {
                    filter {
                        eq("accepted_provider_id", providerId)
                        eq("status", ServiceStatus.CONCLUIDO.name)
                    }
                }
                .decodeList<SolicitacaoServicoDto>()
        } catch (_: Exception) { null }

        if (remote != null) {
            val services = withPartyNames(remote.map { mergeWithLocal(it) })
            services.forEach { try { serviceDao.insert(it) } catch (_: Exception) {} }
            return@runBlocking services.filter { it.status == ServiceStatus.CONCLUIDO && it.providerId == providerId }
        }

        try { serviceDao.getAll().filter { it.status == ServiceStatus.CONCLUIDO && it.providerId == providerId } } catch (_: Exception) { emptyList() }
    }

    /**
     * Aceite: PENDENTE -> EM_ANDAMENTO via RPC `aceitar_solicitacao` (o RLS não permite UPDATE direto).
     * O Room só é atualizado após a confirmação do Supabase. Em caso de sucesso, a solicitação volta
     * com o telefone do cliente (liberado pelo RLS após o aceite) para habilitar o WhatsApp.
     */
    fun acceptRequest(service: Service): Result<Service> =
        runStatusRpc(service, ServiceStatus.EM_ANDAMENTO, "aceitar_solicitacao")

    /** Recusa: PENDENTE -> RECUSADO via RPC. Só vale para solicitações direcionadas ao prestador. */
    fun refuseRequest(service: Service): Result<Service> =
        runStatusRpc(service, ServiceStatus.RECUSADO, "recusar_solicitacao")

    /** Conclusão (exclusiva do prestador aceito): EM_ANDAMENTO -> CONCLUIDO via RPC. */
    fun finalizeRequest(service: Service): Result<Service> =
        runStatusRpc(service, ServiceStatus.CONCLUIDO, "finalizar_solicitacao") {
            put("p_status", ServiceStatus.CONCLUIDO.name)
        }

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

    /**
     * Salva o WhatsApp informado pelo contratante em dados_privados_usuario (RLS: id = auth.uid()),
     * para que o prestador leia esse mesmo número após o aceite. Atualiza só o telefone,
     * preservando endereço/CEP; cria a linha caso ainda não exista.
     */
    fun saveCurrentUserPhone(phone: String): Result<Unit> = runBlocking {
        try {
            val userId = SupabaseClient.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Usuário não autenticado.")
            val updated = SupabaseClient.postgrest.from("dados_privados_usuario")
                .update(mapOf("phone" to phone)) {
                    select()
                    filter { eq("id", userId) }
                }
                .decodeList<DadosPrivadosUsuarioDto>()
            if (updated.isEmpty()) {
                SupabaseClient.postgrest.from("dados_privados_usuario")
                    .insert(DadosPrivadosUsuarioDto(id = userId, phone = phone))
            }
            UserRepository.invalidateCurrentUserCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun runStatusRpc(
        service: Service,
        newStatus: ServiceStatus,
        function: String,
        extraParams: JsonObjectBuilder.() -> Unit = {}
    ): Result<Service> = runBlocking {
        if (!ServiceRules.canChangeStatus(service.status, newStatus)) {
            return@runBlocking Result.failure(
                IllegalStateException("Ação não permitida para o status atual (${service.status.label()}).")
            )
        }

        val dto = try {
            SupabaseClient.postgrest.rpc(function, buildJsonObject {
                put("p_solicitacao_id", service.id)
                extraParams()
            }).decodeAs<SolicitacaoServicoDto>()
        } catch (e: Exception) {
            return@runBlocking Result.failure(IllegalStateException(friendlyError(e), e))
        }

        val remoteStatus = ServiceStatus.entries.firstOrNull { it.name.equals(dto.status, ignoreCase = true) }
            ?: newStatus
        val providerId = dto.acceptedProviderId ?: dto.targetProviderId ?: service.providerId
        val phone = if (remoteStatus == ServiceStatus.EM_ANDAMENTO) {
            getUserPhone(service.clientId).ifBlank { service.clientPhone.orEmpty() }.ifBlank { null }
        } else {
            service.clientPhone
        }

        val updated = service.copy(status = remoteStatus, providerId = providerId, clientPhone = phone)
        try { serviceDao.insert(updated) } catch (_: Exception) {}
        Result.success(updated)
    }

    private fun friendlyError(e: Exception): String =
        e.userMessage("Não foi possível atualizar a solicitação. Tente novamente.")

    /**
     * Preenche os nomes que não estão no cache local: prestador (visão do contratante) e cliente
     * (visão do prestador aceito — o RLS de usuarios libera esse cliente). Uma consulta só.
     */
    private suspend fun withPartyNames(services: List<Service>): List<Service> {
        fun needsClientName(s: Service) = s.clientId.isNotBlank() && (s.clientName.isBlank() || s.clientName == "Cliente")
        val missing = (services.filter { it.providerName.isBlank() && it.providerId.isNotBlank() }.map { it.providerId } +
                services.filter(::needsClientName).map { it.clientId }).distinct()
        if (missing.isEmpty()) return services

        val names = try {
            SupabaseClient.postgrest.from("usuarios")
                .select { filter { isIn("id", missing) } }
                .decodeList<UsuarioDto>()
                .associate { it.id to it.name }
        } catch (_: Exception) { emptyMap() }

        return services.map { s ->
            var updated = s
            if (s.providerName.isBlank()) names[s.providerId]?.let { updated = updated.copy(providerName = it) }
            if (needsClientName(s)) names[s.clientId]?.let { updated = updated.copy(clientName = it) }
            updated
        }
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
            date = dto.createdAt?.take(10) ?: local?.date.orEmpty(),
            urgency = dto.urgency,
            locationText = dto.locationText,
            latitude = dto.latitude,
            longitude = dto.longitude
        )
    }
}
