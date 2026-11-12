package com.tqfapp.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tqfapp.android.business.ServiceStatus

@Entity(tableName = "servicos")
data class Service(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String = "Geral",
    val clientId: String = "c1",
    val clientName: String = "Cliente",
    val providerId: String = "p1",
    val providerName: String = "Prestador",
    val providerPhone: String? = null,
    val price: Double = 0.0,
    val status: ServiceStatus = ServiceStatus.PENDENTE,
    val date: String = ""
)