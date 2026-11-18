package com.tqfapp.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tqfapp.android.business.ServiceStatus
import java.io.Serializable

/**
 * Solicitação de serviço feita por um Contratante a um Prestador.
 * Também exposta como [Solicitacao] (ver Solicitacao.kt).
 */
@Entity(tableName = "servicos")
data class Service(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val clientPhone: String? = null,
    val providerId: String = "",
    val providerName: String = "",
    val price: Double = 0.0,
    val status: ServiceStatus = ServiceStatus.PENDENTE,
    val date: String = ""
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
