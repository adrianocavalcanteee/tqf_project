package com.tqfapp.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.databinding.FragmentProfileBinding
import com.tqfapp.android.databinding.ItemRecentServiceBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Photo Picker launcher for changing profile picture
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            binding.imgProfile.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de perfil atualizada com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupRecentServices()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSettings.setOnClickListener {
            showEditProfileDialog()
        }

        // Open device photo gallery when clicking camera badge or profile picture
        binding.btnChangePhoto.setOnClickListener {
            openGallery()
        }

        binding.imgProfile.setOnClickListener {
            openGallery()
        }

        binding.menuData.setOnClickListener {
            showEditProfileDialog()
        }

        binding.menuAddresses.setOnClickListener {
            showToast("Meus endereços")
        }

        binding.menuFavorites.setOnClickListener {
            showToast("Meus favoritos")
        }

        binding.menuNotifications.setOnClickListener {
            showToast("Notificações")
        }

        binding.btnSeeAllServices.setOnClickListener {
            showToast("Ver todos os serviços")
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_client_profile, null)
        val etClientName = dialogView.findViewById<TextInputEditText>(R.id.etClientName)

        etClientName?.setText(binding.txtProfileName.text)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etClientName?.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    binding.txtProfileName.text = newName
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

    private fun setupRecentServices() {
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

        binding.rvRecentServices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = RecentServiceAdapter(services) { service ->
                showToast("Serviço: ${service.title}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
        private val items: List<RecentServiceItem>,
        private val onItemClick: (RecentServiceItem) -> Unit
    ) : RecyclerView.Adapter<RecentServiceAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemRecentServiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRecentServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                txtServiceTitle.text = item.title
                txtProviderName.text = item.providerName
                txtServiceDate.text = item.date
                txtStatus.text = item.status
                imgService.setImageResource(item.imageRes)
                root.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}