package com.tqfapp.android.ui.home

import android.content.Intent
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
import com.tqfapp.android.ui.auth.LoginActivity

class ProviderHomeFragment : Fragment() {

    private val allRequests = listOf(
        ServiceRequestItem(
            clientName = "Ana Lima",
            category = "Climatização",
            title = "Instalação de ar-condicionado 12000 BTUs em apartamento",
            description = "Preciso de profissional qualificado para instalação de ar split na sala. Já possuo o aparelho.",
            location = "Pinheiros (1,8 km)",
            budget = "R$ 200 - R$ 300",
            postedTime = "Há 15 min",
            avatarRes = R.drawable.ic_profile
        ),
        ServiceRequestItem(
            clientName = "Mariana Costa",
            category = "Informática e TI",
            title = "Configuração de rede Wi-Fi e roteadores Mesh em escritório",
            description = "Proprocuro técnico para configurar 3 pontos de Wi-Fi Mesh e organizar fiação de rede.",
            location = "Moema (3,0 km)",
            budget = "R$ 150 - R$ 250",
            postedTime = "Há 30 min",
            avatarRes = R.drawable.ic_profile
        ),
        ServiceRequestItem(
            clientName = "Bruno Almeida",
            category = "Elétrica",
            title = "Troca de disjuntor geral e reparo na tomada da cozinha",
            description = "Disjuntor caindo constantemente quando liga o forno. Preciso de avaliação do quadro elétrico.",
            location = "Centro (2,1 km)",
            budget = "R$ 120 - R$ 180",
            postedTime = "Há 1 hora",
            avatarRes = R.drawable.ic_profile
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
            onChatClick = { request ->
                showToast("Abrindo conversa com ${request.clientName}")
            },
            onQuoteClick = { request ->
                showSendQuoteDialog(request)
            }
        )

        rvRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = requestAdapter
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
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Notificações do Prestador")
                .setMessage("Você recebeu 3 novas solicitações de orçamento no seu bairro hoje!")
                .setPositiveButton("Entendido", null)
                .show()
        }

        view.findViewById<TextView>(R.id.btnSeeAllRequests)?.setOnClickListener {
            etSearchRequests.setText("")
            requestAdapter.updateList(allRequests)
            showToast("Exibindo todas as solicitações recentes")
        }
    }

    private fun performLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza que deseja encerrar sua sessão de prestador?")
            .setPositiveButton("Sair") { _, _ ->
                Toast.makeText(requireContext(), "Sessão encerrada com sucesso.", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSendQuoteDialog(request: ServiceRequestItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_client_profile, null)
        val etQuotePrice = dialogView.findViewById<EditText>(R.id.etClientName)
        etQuotePrice?.hint = "Valor do orçamento (R$)"
        etQuotePrice?.setText("")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enviar Orçamento para ${request.clientName}")
            .setMessage("Serviço: ${request.title}\nOrçamento estimado do cliente: ${request.budget}")
            .setView(dialogView)
            .setPositiveButton("Enviar Proposta") { _, _ ->
                val priceValue = etQuotePrice?.text?.toString()?.trim().orEmpty()
                if (priceValue.isNotEmpty()) {
                    showToast("Proposta de R$ $priceValue enviada para ${request.clientName}!")
                } else {
                    showToast("Proposta enviada ao cliente com sucesso!")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Model for Service Request
    data class ServiceRequestItem(
        val clientName: String,
        val category: String,
        val title: String,
        val description: String,
        val location: String,
        val budget: String,
        val postedTime: String,
        val avatarRes: Int
    )

    // Adapter for Service Requests
    private inner class ServiceRequestAdapter(
        private var items: List<ServiceRequestItem>,
        private val onChatClick: (ServiceRequestItem) -> Unit,
        private val onQuoteClick: (ServiceRequestItem) -> Unit
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
            val txtBudget: TextView = view.findViewById(R.id.txtBudget)
            val imgClientAvatar: ImageView = view.findViewById(R.id.imgClientAvatar)
            val btnChatClient: MaterialButton = view.findViewById(R.id.btnChatClient)
            val btnSendQuote: MaterialButton = view.findViewById(R.id.btnSendQuote)
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
            holder.txtBudget.text = item.budget
            holder.imgClientAvatar.setImageResource(item.avatarRes)

            holder.btnChatClient.setOnClickListener { onChatClick(item) }
            holder.btnSendQuote.setOnClickListener { onQuoteClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}