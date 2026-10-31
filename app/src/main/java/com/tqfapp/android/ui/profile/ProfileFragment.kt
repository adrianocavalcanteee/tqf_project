package com.tqfapp.android.ui.profile

import android.content.Intent
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
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.databinding.FragmentProfileBinding
import com.tqfapp.android.databinding.ItemRecentServiceBinding
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.utils.MapUtils
import com.tqfapp.android.utils.UserSessionManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Instância do Google Maps e Coordenadas do Escritório
    private var googleMap: GoogleMap? = null
    private var officeLatitude: Double = -23.550520 // Coordenada padrão (Ex: São Paulo, SP)
    private var officeLongitude: Double = -46.633308

    // Flag indicando se o usuário logado é Prestador de Serviço
    private var isProviderUser: Boolean = false

    // Photo Picker launcher para alteração da foto de perfil
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

        // Verifica o tipo de usuário através dos arguments ou da sessão do UserSessionManager
        isProviderUser = arguments?.getBoolean(ARG_IS_PROVIDER)
            ?: UserSessionManager.isProviderUser(requireContext())

        setupUserRoleViewVisibility()
        setupClickListeners()
        setupRecentServices()

        // IMPORTANTE: Só inicializa o Google Maps se o usuário logado for Prestador de Serviço
        if (isProviderUser) {
            setupOfficeMap()
        }
    }

    /**
     * Alterna a visibilidade dos elementos do layout de acordo com o papel do usuário (Prestador vs Contratante)
     */
    private fun setupUserRoleViewVisibility() {
        if (isProviderUser) {
            // Exibe a seção e o mapa do escritório para o Prestador
            binding.txtOfficeMapTitle.visibility = View.VISIBLE
            binding.cardMapContainer.visibility = View.VISIBLE
        } else {
            // Oculta completamente a seção e o mapa do escritório para o Contratante (Cliente)
            binding.txtOfficeMapTitle.visibility = View.GONE
            binding.cardMapContainer.visibility = View.GONE
        }
    }

    /**
     * Inicializa o SupportMapFragment do Google Maps de forma segura apenas para Prestadores
     */
    private fun setupOfficeMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapOffice) as? SupportMapFragment
        mapFragment?.getMapAsync { map ->
            googleMap = map
            updateOfficeMapMarker()
        }
    }

    /**
     * Atualiza o marcador do escritório e centraliza a câmera no mapa
     */
    private fun updateOfficeMapMarker() {
        val map = googleMap ?: return
        MapUtils.setupOfficeLocationOnMap(
            googleMap = map,
            latitude = officeLatitude,
            longitude = officeLongitude,
            title = "Escritório de " + binding.txtProfileName.text,
            snippet = "Localização do Atendimento"
        )
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSettings.setOnClickListener {
            showEditProfileDialog()
        }

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

        binding.menuLogout.setOnClickListener {
            performLogout()
        }

        binding.btnSeeAllServices.setOnClickListener {
            showToast("Ver todos os serviços")
        }
    }

    /**
     * Exibe o diálogo de edição de perfil correspondente ao tipo de usuário
     */
    private fun showEditProfileDialog() {
        if (isProviderUser) {
            showProviderEditProfileDialog()
        } else {
            showClientEditProfileDialog()
        }
    }

    /**
     * Diálogo de edição para Prestadores (inclui campos de Latitude e Longitude)
     */
    private fun showProviderEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_provider_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)
        val etLatitude = dialogView.findViewById<TextInputEditText>(R.id.etLatitude)
        val etLongitude = dialogView.findViewById<TextInputEditText>(R.id.etLongitude)

        etName?.setText(binding.txtProfileName.text)
        etBio?.setText(binding.txtBio.text)
        etLatitude?.setText(officeLatitude.toString())
        etLongitude?.setText(officeLongitude.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()
                val newLat = etLatitude?.text?.toString()?.toDoubleOrNull()
                val newLng = etLongitude?.text?.toString()?.toDoubleOrNull()

                if (newName.isNotEmpty()) {
                    binding.txtProfileName.text = newName
                }
                if (newBio.isNotEmpty()) {
                    binding.txtBio.text = newBio
                }
                if (newLat != null && newLng != null) {
                    officeLatitude = newLat
                    officeLongitude = newLng
                    updateOfficeMapMarker()
                }

                showToast("Perfil de prestador atualizado!")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Diálogo de edição simples para Contratantes
     */
    private fun showClientEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_client_profile, null)
        val etClientName = dialogView.findViewById<TextInputEditText>(R.id.etClientName)

        etClientName?.setText(binding.txtProfileName.text)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etClientName?.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    binding.txtProfileName.text = newName
                    showToast("Perfil de cliente atualizado!")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun performLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza que deseja encerrar sua sessão?")
            .setPositiveButton("Sair") { _, _ ->
                UserSessionManager.clearSession(requireContext())
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
        googleMap = null
        _binding = null
    }

    companion object {
        private const val ARG_IS_PROVIDER = "arg_is_provider"

        fun newInstance(isProvider: Boolean = false): ProfileFragment {
            val fragment = ProfileFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_PROVIDER, isProvider)
            fragment.arguments = args
            return fragment
        }
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
