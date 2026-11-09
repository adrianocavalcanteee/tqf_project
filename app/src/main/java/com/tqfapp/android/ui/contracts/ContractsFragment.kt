package com.tqfapp.android.ui.contracts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tqfapp.android.R
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Proposal
import com.tqfapp.android.databinding.FragmentContractsBinding
import com.tqfapp.android.databinding.ItemContractBinding
import com.tqfapp.android.ui.chat.ChatFragment
import kotlinx.coroutines.runBlocking

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
        try {
            _binding = FragmentContractsBinding.inflate(inflater, container, false)
            return binding.root
        } catch (e: Exception) {
            e.printStackTrace()
            return View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            seedProposalsInRoom()
            setupContractsList()
            setupFilters()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seedProposalsInRoom() {
        try {
            val proposalDao = AppDatabase.getDatabase(requireContext()).proposalDao()
            runBlocking {
                val existing = try { proposalDao.getAll() } catch (_: Exception) { emptyList() }
                if (existing.isEmpty()) {
                    val initialProposals = listOf(
                        Proposal("p1", "s1", "prov1", "cli1", 150.0, "EM_NEGOCIACAO"),
                        Proposal("p2", "s2", "prov2", "cli1", 250.0, "CONCLUIDO"),
                        Proposal("p3", "s3", "prov3", "cli1", 110.0, "PENDENTE")
                    )
                    initialProposals.forEach {
                        try {
                            proposalDao.insert(it)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupContractsList() {
        try {
            contractAdapter = ContractAdapter(allContracts,
                onChatClick = { _ ->
                    activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_chat
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, ChatFragment())
                        .addToBackStack(null)
                        .commit()
                },
                onDetailsClick = { contract ->
                    try {
                        val detailsFragment = ServiceDetailsFragment.newInstance(
                            title = contract.serviceTitle,
                            professional = contract.professionalName,
                            specialty = contract.specialty,
                            schedule = contract.scheduleDate,
                            price = contract.price,
                            status = contract.status,
                            desc = "Serviço contratado com ${contract.professionalName}. Acompanhe o andamento ou entre em contato via chat em caso de dúvidas."
                        )
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, detailsFragment)
                            .addToBackStack(null)
                            .commit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("Detalhes: ${contract.serviceTitle}")
                    }
                }
            )

            binding.rvContracts.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = contractAdapter
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupFilters() {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun filterByStatus(statusFilter: String?) {
        try {
            val filtered = if (statusFilter == null) {
                allContracts
            } else {
                allContracts.filter { it.status.equals(statusFilter, ignoreCase = true) }
            }

            contractAdapter.updateList(filtered)
            if (filtered.isEmpty()) {
                showToast("Nenhuma contratação encontrada com o status '$statusFilter'")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
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

                val isCompleted = item.status.equals("Concluído", ignoreCase = true) || item.status.equals("Concluídos", ignoreCase = true)
                btnChat.visibility = if (isCompleted) View.GONE else View.VISIBLE

                btnChat.setOnClickListener { onChatClick(item) }
                btnDetails.setOnClickListener { onDetailsClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
