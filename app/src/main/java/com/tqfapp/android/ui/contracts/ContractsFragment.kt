package com.tqfapp.android.ui.contracts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.databinding.FragmentContractsBinding
import com.tqfapp.android.databinding.ItemContractBinding

class ContractsFragment : Fragment() {

    private var _binding: FragmentContractsBinding? = null
    private val binding get() = _binding!!

    private val allContracts = listOf(
        ContractItem(
            professionalName = "Carlos Mendes",
            specialty = "Eletricista Residencial",
            serviceTitle = "Instalação de tomadas e iluminação",
            scheduleDate = "15 de mar. de 2025 • 09:00",
            price = "R$ 150,00",
            status = "Em andamento",
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ContractItem(
            professionalName = "Paulo Oliveira",
            specialty = "Climatização",
            serviceTitle = "Instalação de ar-condicionado 12000 BTUs",
            scheduleDate = "12 de mar. de 2025 • 14:00",
            price = "R$ 250,00",
            status = "Concluído",
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ContractItem(
            professionalName = "Rafael Lima",
            specialty = "Encanador / Bombeiro Hidráulico",
            serviceTitle = "Reparo em vazamento de tubulação",
            scheduleDate = "20 de mar. de 2025 • 10:30",
            price = "R$ 110,00",
            status = "Agendado",
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        )
    )

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

        seedProposalsInRoom()
        setupContractsList()
        setupFilters()
    }

    private fun seedProposalsInRoom() {
        val proposalDao = com.tqfapp.android.data.local.database.AppDatabase.getDatabase(requireContext()).proposalDao()
        kotlinx.coroutines.runBlocking {
            if (proposalDao.getAll().isEmpty()) {
                val initialProposals = listOf(
                    com.tqfapp.android.data.model.Proposal("p1", "s1", "prov1", "cli1", 150.0, "EM_NEGOCIACAO"),
                    com.tqfapp.android.data.model.Proposal("p2", "s2", "prov2", "cli1", 250.0, "CONCLUIDO"),
                    com.tqfapp.android.data.model.Proposal("p3", "s3", "prov3", "cli1", 110.0, "PENDENTE")
                )
                initialProposals.forEach { proposalDao.insert(it) }
            }
        }
    }

    private fun setupContractsList() {
        val proposalDao = com.tqfapp.android.data.local.database.AppDatabase.getDatabase(requireContext()).proposalDao()
        val serviceDao = com.tqfapp.android.data.local.database.AppDatabase.getDatabase(requireContext()).serviceDao()

        contractAdapter = ContractAdapter(allContracts,
            onChatClick = { contract ->
                showToast("Abrindo conversa com ${contract.professionalName}")
            },
            onDetailsClick = { contract ->
                // Aceitar proposta e atualizar status do serviço no banco
                kotlinx.coroutines.runBlocking {
                    val proposals = proposalDao.getAll()
                    val p = proposals.find { it.value > 0 }
                    if (p != null) {
                        val updatedP = p.copy(status = "ACEITO")
                        proposalDao.update(updatedP)

                        val service = serviceDao.getById(p.serviceId)
                        if (service != null) {
                            val newStatus = com.tqfapp.android.business.ServiceRules.statusAfterAcceptedProposal(service.status)
                            serviceDao.update(service.copy(status = newStatus))
                            showToast("Proposta aceita! Serviço ${service.title} alterado para $newStatus no banco.")
                        } else {
                            showToast("Proposta aceita e salva no banco de dados!")
                        }
                    } else {
                        showToast("Detalhes da contratação: ${contract.serviceTitle}")
                    }
                }
            }
        )

        binding.rvContracts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contractAdapter
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            filterByStatus(null)
        }

        binding.chipInProgress.setOnClickListener {
            filterByStatus("Em andamento")
        }

        binding.chipScheduled.setOnClickListener {
            filterByStatus("Agendado")
        }

        binding.chipCompleted.setOnClickListener {
            filterByStatus("Concluído")
        }

        binding.chipCanceled.setOnClickListener {
            filterByStatus("Cancelado")
        }

        binding.btnSearchContract.setOnClickListener {
            showToast("Buscar nas contratações")
        }
    }

    private fun filterByStatus(statusFilter: String?) {
        val filtered = if (statusFilter == null) {
            allContracts
        } else {
            allContracts.filter { it.status.equals(statusFilter, ignoreCase = true) }
        }

        contractAdapter.updateList(filtered)
        if (filtered.isEmpty()) {
            showToast("Nenhuma contratação encontrada com o status '$statusFilter'")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Model for Contract
    data class ContractItem(
        val professionalName: String,
        val specialty: String,
        val serviceTitle: String,
        val scheduleDate: String,
        val price: String,
        val status: String,
        val isVerified: Boolean,
        val avatarRes: Int
    )

    // Adapter for Contracts
    private inner class ContractAdapter(
        private var items: List<ContractItem>,
        private val onChatClick: (ContractItem) -> Unit,
        private val onDetailsClick: (ContractItem) -> Unit
    ) : RecyclerView.Adapter<ContractAdapter.ViewHolder>() {

        fun updateList(newList: List<ContractItem>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemContractBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemContractBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                txtProfessionalName.text = item.professionalName
                txtSpecialty.text = item.specialty
                txtServiceTitle.text = item.serviceTitle
                txtScheduleDate.text = item.scheduleDate
                txtContractPrice.text = item.price
                txtStatusBadge.text = item.status
                imgVerified.visibility = if (item.isVerified) View.VISIBLE else View.GONE

                btnChat.setOnClickListener { onChatClick(item) }
                btnDetails.setOnClickListener { onDetailsClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}