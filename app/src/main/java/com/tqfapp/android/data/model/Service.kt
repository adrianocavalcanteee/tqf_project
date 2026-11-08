package com.tqfapp.android.data.model
import com.tqfapp.android.business.ServiceStatus
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servicos")
data class Service(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val status: ServiceStatus = ServiceStatus.EM_ABERTO
)
