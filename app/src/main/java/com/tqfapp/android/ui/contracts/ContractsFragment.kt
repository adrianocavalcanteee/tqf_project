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
import com.tqfapp.android.utils.UserSessionManager

class ContractsFragment : Fragment() {

    private var _binding: FragmentContractsBinding? = null
    private val binding get() = _binding!!

    private val allContracts = listOf(
        ContractItem(
            professionalName = "Paulo Oliveira",
            specialty = "Climatização",
            serviceTitle = "Instalação de ar-condicionado 12000 BTUs",
            scheduleDate = "12 de mar. de 2025 • 14:00",
            price = "R$ 250,00",
            status = "Concluído",
            rating = "★ 5.0",
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ContractItem(
            professionalName = "Carlos Mendes",
            specialty = "Eletricista Residencial",
            serviceTitle = "Instalação de tomadas e iluminação LED",
            scheduleDate = "05 de mar. de 2025 • 09:30",
            price = "R$ 150,00",
            status = "Concluído",
            rating = "★ 4.9",
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ContractItem(
            professionalName = "Rafael Lima",
            specialty = "Encanador / Bombeiro Hidráulico",
            serviceTitle = "Reparo em vazamento de tubulação da cozinha",
            scheduleDate = "22 de fev. de 2025 • 10:00",
            price = "R$ 110,00",
            status = "Concluído",
            rating = "★ 4.8",
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ContractItem(
            professionalName = "Juliana Alves",
            specialty = "Diarista e Faxineira",
            serviceTitle = "Limpeza residencial completa e pós-obra",
            scheduleDate = "10 de fev. de 2025 • 08:00",
            price = "R$ 180,00",
            status = "Concluído",
            rating = "★ 5.0",
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
            setupContractsList()
            setupSearch()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupContractsList() {
        try {
            val isProvider = UserSessionManager.isProviderUser(requireContext())

            if (isProvider) {
                binding.txtHeaderTitle.text = "Contratações em Andamento"
            } else {
                binding.txtHeaderTitle.text = "Histórico de Serviços"
            }

            contractAdapter = ContractAdapter(allContracts,
                onDetailsClick = { contract ->
                    try {
                        val detailsFragment = ServiceDetailsFragment.newInstance(
                            title = contract.serviceTitle,
                            professional = contract.professionalName,
                            specialty = contract.specialty,
                            schedule = contract.scheduleDate,
                            price = contract.price,
                            status = contract.status,
                            desc = "Serviço contratado com ${contract.professionalName}."
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

    private fun setupSearch() {
        try {
            binding.btnSearchContract.setOnClickListener {
                showToast("Buscar no histórico por nome do prestador ou serviço")
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
        val rating: String = "★ 5.0",
        val isVerified: Boolean = true,
        val avatarRes: Int = R.drawable.ic_profile
    )

    // Adapter for Contracts / History
    private inner class ContractAdapter(
        private var items: List<ContractItem>,
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
                txtRatingValue.text = item.rating
                imgVerified.visibility = if (item.isVerified) View.VISIBLE else View.GONE

                btnDetails.setOnClickListener { onDetailsClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
