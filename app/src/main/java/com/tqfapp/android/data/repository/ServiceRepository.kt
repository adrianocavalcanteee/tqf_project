package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.network.dto.DadosPrivadosUsuarioDto
import com.tqfapp.android.data.network.dto.SolicitacaoServicoDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put

@Serializable
private data class AceitarParams(@SerialName("p_solicitacao_id") val pSolicitacaoId: String)

@Serializable
private data class RecusarParams(@SerialName("p_solicitacao_id") val pSolicitacaoId: String)

@Serializable
private data class FinalizarParams(
    @SerialName("p_solicitacao_id") val pSolicitacaoId: String,
    @SerialName("p_novo_status") val pNovoStatus: String
)

/**
 * Repositório responsável pelo fluxo de Solicitações de Serviço integrado ao Supabase PostgREST e RPCs.
 * Mantém sincronização e fallback no Room local.
 */
class ServiceRepository(private val context: Context) {

    private val serviceDao = AppDatabase.getDatabase(context).serviceDao()

    /**
     * CLIENTE: Cria uma solicitação DIRETA para um prestador específico.
     */
    fun createDirectRequest(
        targetProviderId: String,
        category: String,
        title: String,
        description: String
    ): Result<SolicitacaoServicoDto> = runBlocking {
        try {
            val clientId = SupabaseClient.auth.currentUserOrNull()?.id
                ?: return@runBlocking Result.failure(IllegalStateException("Usuário não autenticado no Supabase."))

            val dto = SolicitacaoServicoDto(
                clientId = clientId,
                targetProviderId = targetProviderId,
                acceptedProviderId = null,
                category = category,
                title = title,
                description = description,
                status = "PENDENTE"
            )

            val inserted = SupabaseClient.postgrest.from("solicitacoes_servico")
                .insert(dto) {
                    select()
                }
                .decodeSingle<SolicitacaoServicoDto>()

            // Cache local Room
            try {
                serviceDao.insert(
                    Service(
                        id = inserted.id ?: java.util.UUID.randomUUID().toString(),
                        title = inserted.title,
                        description = inserted.description,
                        category = inserted.category,
                        clientId = clientId,
                        providerId = targetProviderId,
                        status = ServiceStatus.PENDENTE
                    )
                )
            } catch (_: Exception) {}

            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * CLIENTE: Cria uma solicitação GERAL por categoria (para todos os prestadores daquela categoria).
     */
    fun createGeneralRequest(
        category: String,
        title: String,
        description: String
    ): Result<SolicitacaoServicoDto> = runBlocking {
        try {
            val clientId = SupabaseClient.auth.currentUserOrNull()?.id
                ?: return@runBlocking Result.failure(IllegalStateException("Usuário não autenticado no Supabase."))

            val dto = SolicitacaoServicoDto(
                clientId = clientId,
                targetProviderId = null,
                acceptedProviderId = null,
                category = category,
                title = title,
                description = description,
                status = "PENDENTE"
            )

            val inserted = SupabaseClient.postgrest.from("solicitacoes_servico")
                .insert(dto) {
                    select()
                }
                .decodeSingle<SolicitacaoServicoDto>()

            // Cache local Room
            try {
                serviceDao.insert(
                    Service(
                        id = inserted.id ?: java.util.UUID.randomUUID().toString(),
                        title = inserted.title,
                        description = inserted.description,
                        category = inserted.category,
                        clientId = clientId,
                        status = ServiceStatus.PENDENTE
                    )
                )
            } catch (_: Exception) {}

            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PRESTADOR: Consulta solicitações visíveis (Pendentes diretas/gerais da categoria, Em Andamento e Concluídas/Canceladas).
     */
    fun getProviderRequests(providerCategory: String): List<SolicitacaoServicoDto> = runBlocking {
        try {
            val currentProviderId = SupabaseClient.auth.currentUserOrNull()?.id ?: return@runBlocking emptyList()

            SupabaseClient.postgrest.from("solicitacoes_servico")
                .select()
                .decodeList<SolicitacaoServicoDto>()
                .filter { item ->
                    val isPendingTarget = item.status == "PENDENTE" && item.targetProviderId == currentProviderId
                    val isPendingGeneral = item.status == "PENDENTE" && item.targetProviderId == null && item.category.equals(providerCategory, ignoreCase = true)
                    val isInProgress = item.status == "EM_ANDAMENTO" && item.acceptedProviderId == currentProviderId
                    val isHistory = (item.status == "CONCLUIDO" || item.status == "CANCELADO") && (item.acceptedProviderId == currentProviderId || item.targetProviderId == currentProviderId)

                    isPendingTarget || isPendingGeneral || isInProgress || isHistory
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * CLIENTE: Consulta todas as solicitações do próprio cliente autenticado via Supabase PostgREST.
     * Os dados são recarregados a cada consulta (não utiliza Supabase Realtime).
     */
    fun getClientRequests(): List<SolicitacaoServicoDto> = runBlocking {
        try {
            val currentClientId = SupabaseClient.auth.currentUserOrNull()?.id ?: return@runBlocking emptyList()

            SupabaseClient.postgrest.from("solicitacoes_servico")
                .select {
                    filter {
                        eq("client_id", currentClientId)
                    }
                }
                .decodeList<SolicitacaoServicoDto>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * PRESTADOR: Aceita uma solicitação chamando a RPC aceitar_solicitacao(id).
     */
    fun acceptRequest(solicitacaoId: String): Result<Boolean> = runBlocking {
        try {
            try {
                SupabaseClient.postgrest.rpc(
                    function = "aceitar_solicitacao",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_solicitacao_id", solicitacaoId)
                    }
                )
            } catch (_: Exception) {
                SupabaseClient.postgrest.rpc(
                    function = "aceitar_solicitacao",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("id", solicitacaoId)
                    }
                )
            }

            try {
                val localService = serviceDao.getById(solicitacaoId)
                if (localService != null) {
                    serviceDao.update(localService.copy(status = ServiceStatus.EM_ANDAMENTO))
                }
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PRESTADOR: Recusa uma solicitação DIRETA chamando a RPC recusar_solicitacao(id).
     */
    fun refuseRequest(solicitacaoId: String): Result<Boolean> = runBlocking {
        try {
            try {
                SupabaseClient.postgrest.rpc(
                    function = "recusar_solicitacao",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_solicitacao_id", solicitacaoId)
                    }
                )
            } catch (_: Exception) {
                SupabaseClient.postgrest.rpc(
                    function = "recusar_solicitacao",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("id", solicitacaoId)
                    }
                )
            }

            try {
                val localService = serviceDao.getById(solicitacaoId)
                if (localService != null) {
                    serviceDao.update(localService.copy(status = ServiceStatus.RECUSADO))
                }
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PRESTADOR: Finaliza uma solicitação (CONCLUIDO ou CANCELADO) chamando a RPC finalizar_solicitacao(id, novo_status).
     */
    fun finalizeRequest(solicitacaoId: String, newStatus: String): Result<Boolean> = runBlocking {
        try {
            try {
                SupabaseClient.postgrest.rpc(
                    function = "finalizar_solicitacao",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_solicitacao_id", solicitacaoId)
                        put("p_novo_status", newStatus)
                    }
                )
            } catch (_: Exception) {
                SupabaseClient.postgrest.rpc(
                    function = "finalizar_solicitacao",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("id", solicitacaoId)
                        put("novo_status", newStatus)
                    }
                )
            }

            try {
                val localService = serviceDao.getById(solicitacaoId)
                if (localService != null) {
                    val statusEnum = if (newStatus == "CONCLUIDO") ServiceStatus.CONCLUIDO else ServiceStatus.CANCELADO
                    serviceDao.update(localService.copy(status = statusEnum))
                }
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtém o número de telefone de um usuário em dados_privados_usuario (para WhatsApp).
     */
    fun getUserPhone(userId: String): String? = runBlocking {
        try {
            val dto = SupabaseClient.postgrest.from("dados_privados_usuario")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<DadosPrivadosUsuarioDto>()
            dto?.phone
        } catch (e: Exception) {
            null
        }
    }

    // Compatibilidade com código legado
    fun getAllServices(): List<Service> {
        return try {
            serviceDao.getAll()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getServiceById(id: String): Service? {
        return try {
            serviceDao.getById(id)
        } catch (e: Exception) {
            null
        }
    }

    fun createService(service: Service): Boolean {
        return try {
            serviceDao.insert(service)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun updateServiceStatus(serviceId: String, newStatus: ServiceStatus): Boolean {
        return try {
            val service = getServiceById(serviceId)
            if (service != null) {
                val updated = service.copy(status = newStatus)
                serviceDao.update(updated)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
