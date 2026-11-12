package com.tqfapp.android.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.ui.notifications.NotificationsFragment
import java.util.UUID

class ProviderHomeFragment : Fragment() {

    private val allRequests = mutableListOf(
        ServiceRequestItem(
            serviceId = "s1",
            clientName = "Ana Lima",
            category = "Reformas e Reparos",
            title = "Instalação de ar-condicionado 12000 BTUs em apartamento",
            description = "Preciso de profissional qualificado para instalação de ar split na sala. Já possuo o aparelho.",
            location = "Pinheiros (1,8 km)",
            budget = "R$ 200 - R$ 300",
            postedTime = "Há 15 min",
            clientPhone = "11999998888",
            avatarRes = R.drawable.ic_profile
        ),
        ServiceRequestItem(
            serviceId = "s2",
            clientName = "Mariana Costa",
            category = "Assistência Técnica",
            title = "Configuração de rede Wi-Fi e roteadores Mesh em escritório",
            description = "Procuro técnico para configurar 3 pontos de Wi-Fi Mesh e organizar fiação de rede.",
            location = "Moema (3,0 km)",
            budget = "R$ 150 - R$ 250",
            postedTime = "Há 30 min",
            clientPhone = "11988887777",
            avatarRes = R.drawable.ic_profile
        ),
        ServiceRequestItem(
            serviceId = "s3",
            clientName = "Bruno Almeida",
            category = "Reformas e Reparos",
            title = "Troca de disjuntor geral e reparo na tomada da cozinha",
            description = "Disjuntor caindo constantemente quando liga o forno. Preciso de avaliação do quadro elétrico.",
            location = "Centro (2,1 km)",
            budget = "R$ 120 - R$ 180",
            postedTime = "Há 1 hora",
            clientPhone = "11977776666",
            avatarRes = R.drawable.ic_profile
        )
    )

    private val categorySuggestions = listOf(
        HomeFragment.ProfessionalItem(
            name = "Carlos Mendes",
            specialty = "Eletricista Residencial e Predial",
            category = "Reformas e Reparos",
            subcategory = "Eletricista Residencial e Predial",
            rating = "4,9 (86 avaliações)",
            ratingValue = 4.9,
            location = "2,1 km • Centro",
            distanceKm = 2.1,
            price = "R$ 80,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        HomeFragment.ProfessionalItem(
            name = "Rafael Lima",
            specialty = "Encanador / Bombeiro Hidráulico",
            category = "Reformas e Reparos",
            subcategory = "Encanador / Bombeiro Hidráulico",
            rating = "4,8 (64 avaliações)",
            ratingValue = 4.8,
            location = "3,4 km • Jardim Paulista",
            distanceKm = 3.4,
            price = "R$ 90,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        HomeFragment.ProfessionalItem(
            name = "João Ferreira",
            specialty = "Técnico em Manutenção de Notebooks e Computadores",
            category = "Assistência Técnica",
            subcategory = "Técnico em Manutenção de Notebooks e Computadores",
            rating = "5,0 (289 avaliações)",
            ratingValue = 5.0,
            location = "2,1 km • Centro",
            distanceKm = 2.1,
            price = "R$ 100,00",
            status = "⚡ Atende hoje",
            isVerified = true
        )
    )

    private lateinit var requestAdapter: ServiceRequestAdapter
    private lateinit var rvRequests: RecyclerView
    private lateinit var etSearchRequests: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_provider_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvRequests = view.findViewById(R.id.rvServiceRequests)
        etSearchRequests = view.findViewById(R.id.etSearchRequests)

        setupRequestsList()
        setupSearch()
        setupStatusSwitch(view)
        setupNotification(view)
    }

    private fun setupRequestsList() {
        requestAdapter = ServiceRequestAdapter(
            allRequests,
            onAcceptClick = { request ->
                acceptServiceRequest(request)
            },
            onRefuseClick = { request ->
                refuseServiceRequest(request)
            }
        )

        rvRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = requestAdapter
        }
    }

    private fun acceptServiceRequest(request: ServiceRequestItem) {
        val serviceDao = AppDatabase.getDatabase(requireContext()).serviceDao()
        val acceptedService = Service(
            id = request.serviceId.ifEmpty { UUID.randomUUID().toString() },
            title = request.title,
            description = request.description,
            category = request.category,
            clientId = "c1",
            clientName = request.clientName,
            providerId = "p1",
            providerName = "João Ferreira",
            providerPhone = request.clientPhone,
            status = ServiceStatus.EM_ANDAMENTO,
            date = "Hoje"
        )

        try {
            serviceDao.insert(acceptedService)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        allRequests.remove(request)
        requestAdapter.updateList(allRequests)

        Toast.makeText(requireContext(), "Solicitação aceita! Adicionada à aba 'Contratações'. Liberação do WhatsApp...", Toast.LENGTH_LONG).show()

        // Abre direcionamento direto no WhatsApp
        openWhatsApp(request.clientPhone, "Olá ${request.clientName}! Aceitei sua solicitação de serviço no TQFapp: '${request.title}'. Vamos alinhar os detalhes?")
    }

    private fun refuseServiceRequest(request: ServiceRequestItem) {
        val serviceDao = AppDatabase.getDatabase(requireContext()).serviceDao()
        val refusedService = Service(
            id = request.serviceId.ifEmpty { UUID.randomUUID().toString() },
            title = request.title,
            description = request.description,
            category = request.category,
            clientId = "c1",
            clientName = request.clientName,
            providerId = "p1",
            providerName = "João Ferreira",
            status = ServiceStatus.RECUSADO,
            date = "Hoje"
        )

        try {
            serviceDao.insert(refusedService)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        allRequests.remove(request)
        requestAdapter.updateList(allRequests)

        showToast("Solicitação recusada. O cliente receberá sugestões de outros prestadores.")
    }

    private fun openWhatsApp(phoneNumber: String, message: String) {
        try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            val url = "https://api.whatsapp.com/send?phone=55$cleanPhone&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Não foi possível abrir o WhatsApp no dispositivo.")
        }
    }

    private fun setupSearch() {
        etSearchRequests.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                val filtered = if (query.isEmpty()) {
                    allRequests
                } else {
                    allRequests.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.clientName.contains(query, ignoreCase = true) ||
                                it.category.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                    }
                }
                requestAdapter.updateList(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupStatusSwitch(view: View) {
        val switchStatus = view.findViewById<SwitchMaterial>(R.id.switchStatus)
        val txtStatusTitle = view.findViewById<TextView>(R.id.txtStatusTitle)

        switchStatus?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                txtStatusTitle?.text = "Status: Disponível 🟢"
                showToast("Seu perfil agora está visível para novos chamados")
            } else {
                txtStatusTitle?.text = "Status: Indisponível 🔴"
                showToast("Seu perfil está oculto para novos chamados")
            }
        }
    }

    private fun setupNotification(view: View) {
        view.findViewById<ImageView>(R.id.imgNotificationProvider)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<TextView>(R.id.btnSeeAllRequests)?.setOnClickListener {
            etSearchRequests.setText("")
            requestAdapter.updateList(allRequests)
            showToast("Exibindo todas as solicitações recentes")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Model for Service Request
    data class ServiceRequestItem(
        val serviceId: String = "s1",
        val clientName: String,
        val category: String,
        val title: String,
        val description: String,
        val location: String,
        val budget: String,
        val postedTime: String,
        val clientPhone: String = "11999998888",
        val avatarRes: Int
    )

    // Adapter for Service Requests
    private inner class ServiceRequestAdapter(
        private var items: List<ServiceRequestItem>,
        private val onAcceptClick: (ServiceRequestItem) -> Unit,
        private val onRefuseClick: (ServiceRequestItem) -> Unit
    ) : RecyclerView.Adapter<ServiceRequestAdapter.ViewHolder>() {

        fun updateList(newList: List<ServiceRequestItem>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtClientName: TextView = view.findViewById(R.id.txtClientName)
            val txtCategoryBadge: TextView = view.findViewById(R.id.txtCategoryBadge)
            val txtPostedTime: TextView = view.findViewById(R.id.txtPostedTime)
            val txtRequestTitle: TextView = view.findViewById(R.id.txtRequestTitle)
            val txtRequestDescription: TextView = view.findViewById(R.id.txtRequestDescription)
            val txtLocation: TextView = view.findViewById(R.id.txtLocation)
            val imgClientAvatar: ImageView = view.findViewById(R.id.imgClientAvatar)
            val btnRefuseService: MaterialButton = view.findViewById(R.id.btnRefuseService)
            val btnAcceptService: MaterialButton = view.findViewById(R.id.btnAcceptService)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service_request, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtClientName.text = item.clientName
            holder.txtCategoryBadge.text = item.category
            holder.txtPostedTime.text = item.postedTime
            holder.txtRequestTitle.text = item.title
            holder.txtRequestDescription.text = item.description
            holder.txtLocation.text = item.location
            holder.imgClientAvatar.setImageResource(item.avatarRes)

            holder.btnRefuseService.setOnClickListener { onRefuseClick(item) }
            holder.btnAcceptService.setOnClickListener { onAcceptClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    // Adapter for Suggested Providers
    private inner class SuggestedProviderAdapter(
        private val items: List<HomeFragment.ProfessionalItem>,
        private val onItemClick: (HomeFragment.ProfessionalItem) -> Unit
    ) : RecyclerView.Adapter<SuggestedProviderAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtSuggestedName)
            val txtSpecialty: TextView = view.findViewById(R.id.txtSuggestedSpecialty)
            val txtRating: TextView = view.findViewById(R.id.txtSuggestedRating)
            val imgAvatar: ImageView = view.findViewById(R.id.imgSuggestedAvatar)
            val btnSelect: MaterialButton = view.findViewById(R.id.btnSelectSuggested)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggested_provider, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtName.text = item.name
            holder.txtSpecialty.text = item.specialty
            holder.txtRating.text = item.rating
            holder.btnSelect.setOnClickListener { onItemClick(item) }
            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
