package com.tqfapp.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R

class ProfileFragment : Fragment() {

    private lateinit var imgProfile: ImageView
    private lateinit var txtProfileName: TextView

    // Photo Picker launcher for changing profile picture
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && ::imgProfile.isInitialized) {
            imgProfile.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de perfil atualizada com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgProfile = view.findViewById(R.id.imgProfile)
        txtProfileName = view.findViewById(R.id.txtProfileName)

        setupClickListeners(view)
        setupRecentServices(view)
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            showEditProfileDialog()
        }

        view.findViewById<View>(R.id.btnChangePhoto)?.setOnClickListener {
            openGallery()
        }

        imgProfile.setOnClickListener {
            openGallery()
        }

        view.findViewById<View>(R.id.menuData)?.setOnClickListener {
            showEditProfileDialog()
        }

        view.findViewById<View>(R.id.menuAddresses)?.setOnClickListener {
            showToast("Meus endereços")
        }

        view.findViewById<View>(R.id.menuFavorites)?.setOnClickListener {
            showToast("Meus favoritos")
        }

        view.findViewById<View>(R.id.menuNotifications)?.setOnClickListener {
            showToast("Notificações")
        }

        view.findViewById<View>(R.id.btnSeeAllServices)?.setOnClickListener {
            showToast("Ver todos os serviços")
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_client_profile, null)
        val etClientName = dialogView.findViewById<TextInputEditText>(R.id.etClientName)

        etClientName?.setText(txtProfileName.text)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etClientName?.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    txtProfileName.text = newName
                    showToast("Perfil de cliente atualizado com sucesso!")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openGallery() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun setupRecentServices(view: View) {
        val services = listOf(
            RecentServiceItem(
                title = "Instalação de ar-condicionado",
                providerName = "com Paulo Oliveira",
                date = "12 de mar. de 2025",
                status = "Concluído",
                imageRes = R.drawable.ic_profile
            ),
            RecentServiceItem(
                title = "Reparo em vazamento",
                providerName = "com Roberto Silva",
                date = "03 de mar. de 2025",
                status = "Concluído",
                imageRes = R.drawable.ic_profile
            )
        )

        view.findViewById<RecyclerView>(R.id.rvRecentServices)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = RecentServiceAdapter(services) { service ->
                showToast("Serviço: ${service.title}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Model for Recent Service
    data class RecentServiceItem(
        val title: String,
        val providerName: String,
        val date: String,
        val status: String,
        val imageRes: Int
    )

    // Adapter for Recent Services
    private inner class RecentServiceAdapter(
        private var items: List<RecentServiceItem>,
        private val onItemClick: (RecentServiceItem) -> Unit
    ) : RecyclerView.Adapter<RecentServiceAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtServiceTitle: TextView = view.findViewById(R.id.txtServiceTitle)
            val txtProviderName: TextView = view.findViewById(R.id.txtProviderName)
            val txtServiceDate: TextView = view.findViewById(R.id.txtServiceDate)
            val txtStatus: TextView = view.findViewById(R.id.txtStatus)
            val imgService: ImageView = view.findViewById(R.id.imgService)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_service, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtServiceTitle.text = item.title
            holder.txtProviderName.text = item.providerName
            holder.txtServiceDate.text = item.date
            holder.txtStatus.text = item.status
            holder.imgService.setImageResource(item.imageRes)
            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}