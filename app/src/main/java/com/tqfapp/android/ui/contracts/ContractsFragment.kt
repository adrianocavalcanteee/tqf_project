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
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.model.label
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.FragmentContractsBinding
import com.tqfapp.android.databinding.ItemContractBinding
import com.tqfapp.android.ui.request.AvaliarSolicitacaoActivity
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aba por papel do usuário:
 * - Contratante ➔ "Histórico": SOMENTE solicitações CONCLUIDO.
 * - Prestador ➔ "Contratações": SOMENTE solicitações aceitas (EM_ANDAMENTO).
 */
class ContractsFragment : Fragment() {

    private var _binding: FragmentContractsBinding? = null
    private val binding get() = _binding!!

    private var isProvider = false
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
            binding.txtHeaderTitle.text = "Contratações em Andamento"
            binding.txtHeaderSubtitle.text = "Serviços que você aceitou"
        } else {
            binding.txtHeaderTitle.text = "Histórico de Serviços"
            binding.txtHeaderSubtitle.text = "Serviços concluídos"
        }

        contractAdapter = ContractAdapter(isProvider) { service -> openDetails(service) }
        binding.rvContracts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contractAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        loadContracts()
    }

    private fun loadContracts() {
        binding.progressContracts.visibility = View.VISIBLE
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val (user, services) = withContext(Dispatchers.IO) {
                try {
                    val user = UserRepository(appContext).getCurrentUser()
                    val repository = ServiceRepository(appContext)
                    val list = when {
                        user == null -> emptyList()
                        isProvider -> repository.getProviderContracts(user.id)
                        else -> repository.getClientHistory(user.id)
                    }
                    user to list
                } catch (e: Exception) {
                    e.printStackTrace()
                    null to emptyList<Service>()
                }
            }

            if (_binding == null) return@launch
            binding.progressContracts.visibility = View.GONE
            contractAdapter.updateData(services)
            showEmptyState(user, services.isEmpty())
        }
    }

    private fun showEmptyState(user: User?, isEmpty: Boolean) {
        binding.txtEmptyContracts.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.txtEmptyContracts.text = when {
            user == null -> "Faça login para ver seus serviços."
            isProvider -> "Nenhuma contratação em andamento. Aceite solicitações na tela inicial."
            else -> "Você ainda não tem serviços concluídos."
        }
    }

    private fun openDetails(service: Service) {
        if (isProvider) {
            // Prestador: WhatsApp do cliente e "Concluir serviço"
            startActivity(AvaliarSolicitacaoActivity.newIntent(requireContext(), service))
            return
        }

        // Contratante: detalhes do serviço concluído + avaliação
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
        private val onDetailsClick: (Service) -> Unit
    ) : RecyclerView.Adapter<ContractAdapter.ViewHolder>() {

        private var items: List<Service> = emptyList()

        fun updateData(newList: List<Service>) {
            items = newList
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
                    item.providerName.ifBlank { "Prestador" }
                }
                txtSpecialty.text = item.category
                txtServiceTitle.text = item.title
                txtScheduleDate.text = item.date
                txtContractPrice.text = formatPrice(item.price)
                txtStatusBadge.text = item.status.label()
                txtRatingValue.visibility = View.GONE
                imgVerified.visibility = View.GONE
                btnDetails.text = if (isProvider) "Contato e conclusão" else "Detalhes"

                btnDetails.setOnClickListener { onDetailsClick(item) }
                root.setOnClickListener { onDetailsClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private companion object {
        fun formatPrice(price: Double): String =
            if (price > 0) User.formatCurrency(price) else "Valor negociado"
    }
}
