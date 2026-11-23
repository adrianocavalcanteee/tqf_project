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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.tqfapp.android.R
import com.google.android.gms.maps.model.LatLng
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.ui.notifications.NotificationsFragment
import com.tqfapp.android.ui.request.AvaliarSolicitacaoActivity
import com.tqfapp.android.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.databinding.ViewSummaryCardBinding
import com.tqfapp.android.ui.common.bind
import com.tqfapp.android.ui.common.loadImage

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

        setupRequestsList()
        setupSearch()
        setupStatusSwitch(view)
        setupNotification(view)
    }

    override fun onResume() {
        super.onResume()
        // Recarrega ao voltar da tela de avaliação (aceite/recusa remove o card do feed).
        loadRequests()
    }

    private fun showGreeting(view: View, providerName: String?) {
        // "Olá!" fica no rótulo acima; aqui vai o nome em destaque.
        view.findViewById<TextView>(R.id.txtProviderGreeting)?.text =
            providerName?.trim()?.takeIf { it.isNotBlank() } ?: "Prestador"
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
            // getCurrentUser faz rede: fica no IO junto com as listas (nada bloqueia a thread principal).
            var providerName: String? = null
            var providerPhoto: String? = null
            var providerAvailable = true
            val (requests, contracts, completedServices) = withContext(Dispatchers.IO) {
                try {
                    val provider = UserRepository(appContext).getCurrentUser() ?: return@withContext Triple(emptyList(), emptyList(), emptyList())
                    providerName = provider.name
                    providerPhoto = provider.profilePictureUri
                    providerAvailable = provider.isAvailable
                    providerLocation = provider.latitude?.let { lat -> provider.longitude?.let { LatLng(lat, it) } }
                    val reqs = ServiceRepository(appContext).getProviderRequests(provider.id, provider.category)
                    val conts = ServiceRepository(appContext).getProviderContracts(provider.id)
                    val comps = ServiceRepository(appContext).getProviderCompletedServices(provider.id)
                    Triple(reqs, conts, comps)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Triple(emptyList(), emptyList(), emptyList())
                }
            }
            val root = view ?: return@launch
            showGreeting(root, providerName)
            root.findViewById<ImageView>(R.id.imgProviderAvatar)?.loadImage(providerPhoto)
            showAvailability(root, providerAvailable)
            allRequests = requests
            applySearch(etSearchRequests.text?.toString().orEmpty())

            val quotesCount = requests.size
            val activeServicesCount = contracts.size
            val completedCount = completedServices.size

            view?.findViewById<TextView>(R.id.txtQuotesCount)?.text = quotesCount.toString()
            view?.findViewById<TextView>(R.id.txtActiveServicesCount)?.text = activeServicesCount.toString()
            view?.findViewById<TextView>(R.id.txtCompletedServicesCount)?.text = completedCount.toString()

            // Card-resumo: concluídos sobre tudo que já foi aceito (em andamento + concluídos).
            val summaryView = root.findViewById<View>(R.id.cardSummary)
            ViewSummaryCardBinding.bind(summaryView).bind(
                title = "Seus serviços",
                text = "$activeServicesCount em andamento • $quotesCount pedidos novos",
                done = completedCount,
                total = activeServicesCount + completedCount,
                actionLabel = "Ver contratações"
            ) { (activity as? HomeActivity)?.showContractsTab() }
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

    private var bindingSwitch = false

    /** Switch "Disponível para chamados": grava em perfis_prestador.is_available e desfaz se falhar. */
    private fun setupStatusSwitch(view: View) {
        val switchStatus = view.findViewById<SwitchMaterial>(R.id.switchStatus) ?: return
        switchStatus.setOnCheckedChangeListener { button, isChecked ->
            if (bindingSwitch) return@setOnCheckedChangeListener
            showAvailability(view, isChecked)
            button.isEnabled = false
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { UserRepository(appContext).setAvailability(isChecked) }
                button.isEnabled = true
                result.onSuccess {
                    showToast(
                        if (isChecked) "Seu perfil agora está visível para novos chamados"
                        else "Seu perfil está oculto para novos chamados"
                    )
                }.onFailure {
                    showAvailability(view, !isChecked)
                    showToast(it.message ?: "Não foi possível alterar sua disponibilidade.")
                }
            }
        }
    }

    private fun showAvailability(view: View, isAvailable: Boolean) {
        val switchStatus = view.findViewById<SwitchMaterial>(R.id.switchStatus)
        bindingSwitch = true
        switchStatus?.isChecked = isAvailable
        bindingSwitch = false
        view.findViewById<TextView>(R.id.txtStatusTitle)?.text =
            if (isAvailable) "Status: Disponível 🟢" else "Status: Indisponível 🔴"
    }

    private fun setupNotification(view: View) {
        view.findViewById<ImageView>(R.id.imgProviderAvatar)?.setOnClickListener {
            (activity as? HomeActivity)?.showProfileTab()
        }

        view.findViewById<ImageView>(R.id.imgNotificationProvider)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<TextView>(R.id.btnSeeAllRequests)?.setOnClickListener {
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val provider = withContext(Dispatchers.IO) {
                    UserRepository(appContext).getCurrentUser()
                }
                val providerId = provider?.id ?: ""
                val providerCategory = provider?.category

                val fragment = AllRequestsFragment.newInstance(providerId, providerCategory)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
