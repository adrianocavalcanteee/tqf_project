package com.tqfapp.android.ui.home

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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.tqfapp.android.R
import com.google.android.gms.maps.model.LatLng
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.resumoParaPrestador
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.ui.notifications.NotificationsFragment
import com.tqfapp.android.ui.request.AvaliarSolicitacaoActivity
import com.tqfapp.android.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Feed do Prestador: chamados PENDENTE direcionados a ele (ou pedidos abertos da sua categoria),
 * do mais urgente para o menos urgente.
 * Fluxo: Recebe Solicitação ➔ Avalia a Descrição (AvaliarSolicitacaoActivity) ➔ Aceita ou Recusa.
 */
class ProviderHomeFragment : Fragment() {

    /** Solicitações carregadas do backend; a lista começa vazia. */
    private var allRequests: List<Service> = emptyList()

    private lateinit var requestAdapter: ServiceRequestAdapter
    private lateinit var rvRequests: RecyclerView
    private lateinit var etSearchRequests: EditText
    private var txtEmptyRequests: TextView? = null
    /** Localização do escritório do prestador, para mostrar a distância até o pedido. */
    private var providerLocation: LatLng? = null

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
        txtEmptyRequests = view.findViewById(R.id.txtEmptyRequests)

        setupGreeting(view)
        setupRequestsList()
        setupSearch()
        setupStatusSwitch(view)
        setupNotification(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { setupGreeting(it) }
        // Recarrega ao voltar da tela de avaliação (aceite/recusa remove o card do feed).
        loadRequests()
    }

    private fun setupGreeting(view: View) {
        val currentUser = UserRepository(requireContext()).getCurrentUser()
        val name = currentUser?.name?.trim()
        val greetingText = if (!name.isNullOrBlank()) {
            val firstName = name.split(" ").firstOrNull() ?: name
            "Olá, $firstName!"
        } else {
            "Olá!"
        }
        view.findViewById<TextView>(R.id.txtProviderGreeting)?.text = greetingText
    }

    private fun setupRequestsList() {
        requestAdapter = ServiceRequestAdapter({ request -> distanceTo(request) }) { request ->
            startActivity(AvaliarSolicitacaoActivity.newIntent(requireContext(), request))
        }

        rvRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = requestAdapter
        }
    }

    private fun loadRequests() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val (requests, contracts) = withContext(Dispatchers.IO) {
                try {
                    val provider = UserRepository(appContext).getCurrentUser() ?: return@withContext Pair(emptyList(), emptyList())
                    providerLocation = provider.latitude?.let { lat -> provider.longitude?.let { LatLng(lat, it) } }
                    val reqs = ServiceRepository(appContext).getProviderRequests(provider.id, provider.category)
                    val conts = ServiceRepository(appContext).getProviderContracts(provider.id)
                    Pair(reqs, conts)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Pair(emptyList(), emptyList())
                }
            }
            if (view == null) return@launch
            allRequests = requests
            applySearch(etSearchRequests.text?.toString().orEmpty())

            val quotesCount = requests.size
            val activeServicesCount = contracts.size
            val totalEarnings = contracts.sumOf { it.price }

            view?.findViewById<TextView>(R.id.txtQuotesCount)?.text = quotesCount.toString()
            view?.findViewById<TextView>(R.id.txtActiveServicesCount)?.text = activeServicesCount.toString()
            view?.findViewById<TextView>(R.id.txtEarningsCount)?.text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", totalEarnings)
        }
    }

    private fun distanceTo(request: Service): Double? {
        val from = providerLocation ?: return null
        val lat = request.latitude ?: return null
        val lng = request.longitude ?: return null
        return LocationUtils.distanceKm(from, LatLng(lat, lng))
    }

    private fun applySearch(rawQuery: String) {
        val query = rawQuery.trim()
        val filtered = if (query.isEmpty()) {
            allRequests
        } else {
            allRequests.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.clientName.contains(query, ignoreCase = true) ||
                        it.category.contains(query, ignoreCase = true) ||
                        it.locationText.orEmpty().contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        requestAdapter.updateData(filtered)
        txtEmptyRequests?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupSearch() {
        etSearchRequests.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearch(s?.toString().orEmpty())
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
            loadRequests()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private class ServiceRequestAdapter(
        private val distanceOf: (Service) -> Double?,
        private val onEvaluateClick: (Service) -> Unit
    ) : RecyclerView.Adapter<ServiceRequestAdapter.ViewHolder>() {

        private var items: List<Service> = emptyList()

        fun updateData(newList: List<Service>) {
            items = newList
            notifyDataSetChanged()
        }

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtClientName: TextView = view.findViewById(R.id.txtClientName)
            val txtCategoryBadge: TextView = view.findViewById(R.id.txtCategoryBadge)
            val txtPostedTime: TextView = view.findViewById(R.id.txtPostedTime)
            val txtRequestTitle: TextView = view.findViewById(R.id.txtRequestTitle)
            val txtRequestDescription: TextView = view.findViewById(R.id.txtRequestDescription)
            val txtLocation: TextView = view.findViewById(R.id.txtLocation)
            val btnEvaluateService: MaterialButton = view.findViewById(R.id.btnEvaluateService)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service_request, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtClientName.text = item.clientName.ifBlank { "Cliente" }
            holder.txtCategoryBadge.text = item.category
            holder.txtPostedTime.text = item.date
            holder.txtRequestTitle.text = item.title
            holder.txtRequestDescription.text = item.description
            holder.txtLocation.text = item.resumoParaPrestador(distanceOf(item))

            holder.btnEvaluateService.setOnClickListener { onEvaluateClick(item) }
            holder.view.setOnClickListener { onEvaluateClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
