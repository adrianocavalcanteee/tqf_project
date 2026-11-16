package com.tqfapp.android.data.repository

import android.content.Context
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service

/**
 * Repositório desacoplado para a entidade Serviço/Solicitação.
 * Concentra buscas, criações e atualizações de status.
 */
class ServiceRepository(private val context: Context) {

    private val serviceDao = AppDatabase.getDatabase(context).serviceDao()

    fun getAllServices(): List<Service> {
        return try {
            val list = serviceDao.getAll()
            if (list.isEmpty()) {
                val initialServices = listOf(
                    Service("s1", "Instalação de ar-condicionado 12000 BTUs", "Instalação de ar split na sala.", category = "Climatização", clientId = "c1", clientName = "Ana Lima", providerId = "p1", providerName = "Paulo Oliveira", price = 250.0, status = ServiceStatus.CONCLUIDO, date = "12 de mar. de 2025 • 14:00"),
                    Service("s2", "Instalação de tomadas e iluminação LED", "Passagem de fiação e tomadas.", category = "Reformas e Reparos", clientId = "c1", clientName = "Ana Lima", providerId = "p2", providerName = "Carlos Mendes", price = 150.0, status = ServiceStatus.CONCLUIDO, date = "05 de mar. de 2025 • 09:30"),
                    Service("s3", "Reparo em vazamento de tubulação", "Desentupimento e vazamentos.", category = "Reformas e Reparos", clientId = "c1", clientName = "Ana Lima", providerId = "p3", providerName = "Rafael Lima", price = 110.0, status = ServiceStatus.CONCLUIDO, date = "22 de fev. de 2025 • 10:00"),
                    Service("s4", "Limpeza residencial completa", "Higienização completa.", category = "Serviços Domésticos", clientId = "c1", clientName = "Ana Lima", providerId = "p4", providerName = "Juliana Alves", price = 180.0, status = ServiceStatus.CONCLUIDO, date = "10 de fev. de 2025 • 08:00")
                )
                initialServices.forEach { try { serviceDao.insert(it) } catch (_: Exception) {} }
                initialServices
            } else {
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getServiceById(id: String): Service? {
        return try {
            serviceDao.getById(id) ?: getAllServices().find { it.id == id }
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
