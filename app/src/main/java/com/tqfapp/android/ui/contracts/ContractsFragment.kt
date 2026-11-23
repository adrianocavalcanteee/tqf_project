package com.tqfapp.android.ui.contracts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.model.label
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.FragmentContractsBinding
import com.tqfapp.android.databinding.ItemContractBinding
import com.tqfapp.android.ui.common.showReviewDialog
import com.tqfapp.android.ui.common.showSkeleton
import com.tqfapp.android.ui.common.setPrimaryEmphasis
import com.tqfapp.android.ui.common.loadProviderPhoto
import com.tqfapp.android.ui.common.applyStatusBadge
import com.tqfapp.android.ui.request.AvaliarSolicitacaoActivity
import com.tqfapp.android.ui.request.SugestaoPrestadoresActivity
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast

/**
 * Aba por papel do usuário:
 * - Contratante ➔ "Minhas Solicitações": todas as solicitações com o status atual
 *   (pendente, aceita/em andamento, recusada, concluída), para acompanhar a resposta do prestador.
 * - Prestador ➔ "Contratações": aceitas em andamento + concluídas (para avaliar o cliente).
 */
class ContractsFragment : Fragment() {

    private var _binding: FragmentContractsBinding? = null
    private val binding get() = _binding!!

    private var isProvider = false
    private var selectedStatusFilter: ServiceStatus? = null
    private var loaded = LoadedContracts(null, emptyList(), emptyMap(), emptySet())
    private lateinit var contractAdapter: ContractAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContractsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isProvider = UserSessionManager.isProviderUser(requireContext())

        if (isProvider) {
            binding.txtHeaderTitle.text = "Contratações"
            binding.txtHeaderSubtitle.text = "Em andamento e concluídas"
        } else {
            binding.txtHeaderTitle.text = "Minhas contratações"
            binding.txtHeaderSubtitle.text = "Acompanhe a resposta dos prestadores"
        }

        contractAdapter = ContractAdapter(
            isProvider = isProvider,
            onDetailsClick = { service -> openDetails(service) },
            onReviewClick = { service -> reviewOtherParty(service) },
            onCancelClick = { service -> confirmCancel(service) }
        )
        binding.rvContracts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contractAdapter
        }
        setupStatusFilter()
    }

    /** Pílulas de status: filtro visual sobre a lista já carregada (não refaz consultas). */
    private fun setupStatusFilter() {
        val options = if (isProvider) {
            listOf(null, ServiceStatus.EM_ANDAMENTO, ServiceStatus.CONCLUIDO)
        } else {
            listOf(null, ServiceStatus.PENDENTE, ServiceStatus.EM_ANDAMENTO, ServiceStatus.CONCLUIDO, ServiceStatus.RECUSADO, ServiceStatus.CANCELADO)
        }
        binding.chipGroupStatusFilter.removeAllViews()
        options.forEach { status ->
            binding.chipGroupStatusFilter.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = status?.let { FILTER_LABELS.getValue(it) } ?: "Todas"
                tag = status
                isCheckable = true
                isChecked = status == selectedStatusFilter
            })
        }
        binding.chipGroupStatusFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            selectedStatusFilter = chip.tag as? ServiceStatus
            showContracts()
        }
    }

    private fun showContracts() {
        if (_binding == null) return
        val visible = selectedStatusFilter?.let { status -> loaded.services.filter { it.status == status } } ?: loaded.services
        contractAdapter.updateData(visible, loaded.ratings, loaded.reviewedServiceIds)
        showEmptyState(loaded.user, visible.isEmpty(), filtered = selectedStatusFilter != null && loaded.services.isNotEmpty())
    }

    override fun onResume() {
        super.onResume()
        loadContracts()
    }

    private fun loadContracts() {
        binding.progressContracts.showSkeleton(true)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val user = UserRepository(appContext).getCurrentUser()
                    val repository = ServiceRepository(appContext)
                    val list = when {
                        user == null -> emptyList()
                        // Em andamento primeiro; concluídos ficam na lista para o prestador avaliar o cliente.
                        isProvider -> repository.getProviderContracts(user.id) +
                                repository.getProviderCompletedServices(user.id).sortedByDescending { it.date }
                        else -> repository.getClientRequests(user.id).sortedByDescending { it.date }
                    }
                    // Contratante: nota média real de cada prestador (uma consulta só). O prestador não
                    // recebe média do cliente: o RLS de avaliacoes só lhe mostra as que ele próprio escreveu.
                    val ratings = if (isProvider) emptyMap() else
                        ReviewRepository(appContext).getRatingSummaries(list.map { it.providerId })
                    // Serviços que o usuário logado já avaliou (reviewer_id = ele).
                    val reviewed = user?.let { u ->
                        ReviewRepository(appContext).getReviewsByClient(u.id).map { it.serviceId }.toSet()
                    }.orEmpty()
                    LoadedContracts(user, list, ratings, reviewed)
                } catch (e: Exception) {
                    e.printStackTrace()
                    LoadedContracts(null, emptyList(), emptyMap(), emptySet())
                }
            }

            if (_binding == null) return@launch
            binding.progressContracts.showSkeleton(false)
            loaded = result
            showContracts()
        }
    }

    private fun showEmptyState(user: User?, isEmpty: Boolean, filtered: Boolean = false) {
        binding.txtEmptyContracts.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.txtEmptyContracts.text = when {
            user == null -> "Faça login para ver seus serviços."
            filtered -> "Nada por aqui com esse filtro."
            isProvider -> "Nenhuma contratação ainda. Aceite solicitações na tela inicial."
            else -> "Você ainda não fez solicitações."
        }
    }

    /**
     * Avaliação direto do card (CONCLUIDO ainda não avaliado): o contratante avalia o prestador e o
     * prestador avalia o cliente.
     */
    private fun reviewOtherParty(service: Service) {
        showReviewDialog(
            serviceId = service.id,
            reviewedId = if (isProvider) service.clientId else service.providerId,
            serviceTitle = service.title,
            reviewedName = if (isProvider) service.clientName.ifBlank { "o cliente" } else service.providerName.ifBlank { "o prestador" }
        ) { loadContracts() }
    }

    /** Contratante cancela uma solicitação PENDENTE ou RECUSADA (com confirmação). */
    private fun confirmCancel(service: Service) {
        val refused = service.status == ServiceStatus.RECUSADO
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (refused) "Cancelar serviço?" else "Cancelar solicitação?")
            .setMessage(
                if (refused) "\"${service.title}\" será encerrado. Você pode abrir um novo pedido quando quiser."
                else "\"${service.title}\" deixará de aparecer para os prestadores."
            )
            .setPositiveButton(if (refused) "Cancelar serviço" else "Cancelar solicitação") { _, _ ->
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { ServiceRepository(appContext).cancelRequest(service) }
                    result.onSuccess {
                        Toast.makeText(appContext, "Solicitação cancelada.", Toast.LENGTH_SHORT).show()
                        loadContracts()
                    }.onFailure {
                        Toast.makeText(appContext, it.message ?: "Não foi possível cancelar.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private data class LoadedContracts(
        val user: User?,
        val services: List<Service>,
        val ratings: Map<String, List<Review>>,
        val reviewedServiceIds: Set<String>
    )

    private fun openDetails(service: Service) {
        if (isProvider) {
            // Prestador: WhatsApp do cliente e "Concluir serviço"
            startActivity(AvaliarSolicitacaoActivity.newIntent(requireContext(), service))
            return
        }

        // Contratante: recusada ➔ sugestões de outros prestadores da mesma categoria
        if (service.status == ServiceStatus.RECUSADO) {
            startActivity(SugestaoPrestadoresActivity.newIntent(requireContext(), service))
            return
        }

        // Contratante: detalhes da solicitação (avaliação liberada somente se concluída)
        val detailsFragment = ServiceDetailsFragment.newInstance(
            title = service.title,
            professional = service.providerName.ifBlank { "Prestador" },
            specialty = service.category,
            schedule = service.date,
            price = formatPrice(service.price),
            status = service.status.label(),
            desc = service.description,
            serviceId = service.id,
            providerId = service.providerId,
            clientId = service.clientId
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, detailsFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ContractAdapter(
        private val isProvider: Boolean,
        private val onDetailsClick: (Service) -> Unit,
        private val onReviewClick: (Service) -> Unit,
        private val onCancelClick: (Service) -> Unit
    ) : RecyclerView.Adapter<ContractAdapter.ViewHolder>() {

        private var items: List<Service> = emptyList()
        private var ratings: Map<String, List<Review>> = emptyMap()
        private var reviewedServiceIds: Set<String> = emptySet()

        fun updateData(
            newList: List<Service>,
            providerRatings: Map<String, List<Review>>,
            reviewed: Set<String>
        ) {
            items = newList
            ratings = providerRatings
            reviewedServiceIds = reviewed
            notifyDataSetChanged()
        }

        class ViewHolder(val binding: ItemContractBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemContractBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                // Mostra a outra parte do contrato: o cliente para o prestador e vice-versa.
                txtProfessionalName.text = if (isProvider) {
                    item.clientName.ifBlank { "Cliente" }
                } else {
                    item.providerName.ifBlank {
                        // Pedido aberto ainda sem prestador: aguardando o primeiro aceite da categoria.
                        if (item.providerId.isBlank()) "Aguardando prestador" else "Prestador"
                    }
                }
                txtSpecialty.text = item.category
                txtServiceTitle.text = item.title
                txtScheduleDate.text = item.date
                txtStatusBadge.applyStatusBadge(item.status)
                imgAvatar.loadProviderPhoto(null, item.category)
                val providerAverage = RatingFormat.average(ratings[item.providerId].orEmpty())
                txtRatingValue.visibility = if (providerAverage == null) View.GONE else View.VISIBLE
                providerAverage?.let { txtRatingValue.text = "★ ${RatingFormat.value(it)}" }
                imgVerified.visibility = View.GONE
                val canReview = item.status == ServiceStatus.CONCLUIDO && item.id !in reviewedServiceIds
                // Contratante pode cancelar enquanto não há resposta ou depois de uma recusa.
                val canCancel = !isProvider &&
                        (item.status == ServiceStatus.PENDENTE || item.status == ServiceStatus.RECUSADO)
                btnCancelRequest.visibility = if (canCancel) View.VISIBLE else View.GONE
                btnCancelRequest.text = if (item.status == ServiceStatus.RECUSADO) "Cancelar serviço" else "Cancelar solicitação"
                btnCancelRequest.setOnClickListener { onCancelClick(item) }
                btnDetails.text = when {
                    !isProvider && item.status == ServiceStatus.PENDENTE -> "Detalhes"
                    canReview && isProvider -> "⭐ Avaliar cliente"
                    canReview -> "⭐ Avaliar prestador"
                    item.status == ServiceStatus.CONCLUIDO -> "✓ Avaliado • Detalhes"
                    isProvider -> "Contato e conclusão"
                    item.status == ServiceStatus.RECUSADO -> "Ver outros prestadores"
                    else -> "Detalhes"
                }

                btnDetails.setPrimaryEmphasis(canReview)
                btnDetails.setOnClickListener {
                    when {
                        canReview -> onReviewClick(item)
                        else -> onDetailsClick(item)
                    }
                }
                root.setOnClickListener { onDetailsClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private companion object {
        val FILTER_LABELS = mapOf(
            ServiceStatus.PENDENTE to "Pendentes",
            ServiceStatus.EM_ANDAMENTO to "Em andamento",
            ServiceStatus.CONCLUIDO to "Concluídas",
            ServiceStatus.RECUSADO to "Recusadas",
            ServiceStatus.CANCELADO to "Canceladas"
        )

        fun formatPrice(price: Double): String =
            if (price > 0) User.formatCurrency(price) else "Valor negociado"
    }
}
