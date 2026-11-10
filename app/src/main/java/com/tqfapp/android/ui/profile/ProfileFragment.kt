package com.tqfapp.android.ui.profile

import android.content.Intent
import android.net.Uri
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
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.FragmentProfileBinding
import com.tqfapp.android.databinding.ItemRecentServiceBinding
import com.tqfapp.android.databinding.ItemReviewBinding
import com.tqfapp.android.ui.contracts.ContractsFragment
import com.tqfapp.android.ui.contracts.ServiceDetailsFragment
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.ui.notifications.NotificationsFragment
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils
import com.tqfapp.android.utils.UserSessionManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Instância do Google Maps
    private var googleMap: GoogleMap? = null

    // Coordenadas estáticas de exemplo do escritório do prestador (São Paulo, SP)
    private var officeLatitude: Double = DEFAULT_LATITUDE
    private var officeLongitude: Double = DEFAULT_LONGITUDE

    // Flag indicando se o PERFIL VISUALIZADO pertence a um Prestador de Serviço
    private var isProviderProfile: Boolean = false
    private var isMapCreated = false
    private lateinit var userRepository: UserRepository
    private var currentUser: User? = null

    // Photo Picker launcher para alteração da foto de perfil
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (_binding != null) {
                binding.imgProfile.setImageURI(uri)
            }
            Toast.makeText(requireContext(), "Foto de perfil atualizada com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    private var clientAvatarImageViewRef: ImageView? = null

    private val clientPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            clientAvatarImageViewRef?.setImageURI(uri)
            if (_binding != null) {
                binding.imgProfile.setImageURI(uri)
            }
            val user = currentUser
            if (user != null) {
                val updated = user.copy(profilePictureUri = uri.toString())
                userRepository.updateUser(updated)
                currentUser = updated
            }
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
        try {
            userRepository = UserRepository(requireContext())

            isProviderProfile = if (arguments?.containsKey(ARG_IS_PROVIDER_PROFILE) == true) {
                arguments?.getBoolean(ARG_IS_PROVIDER_PROFILE) ?: true
            } else {
                UserSessionManager.isProviderUser(requireContext())
            }

            officeLatitude = arguments?.getDouble(ARG_LATITUDE, DEFAULT_LATITUDE) ?: DEFAULT_LATITUDE
            officeLongitude = arguments?.getDouble(ARG_LONGITUDE, DEFAULT_LONGITUDE) ?: DEFAULT_LONGITUDE

            val customName = arguments?.getString(ARG_PROVIDER_NAME)
            if (!customName.isNullOrEmpty()) {
                binding.txtProfileName.text = customName
            }

            loadUserDataFromRoom()
            setupUserRoleViewVisibility()

            setupClickListeners()
            setupRecentServices()
            setupClientReviews()

            if (isProviderProfile) {
                setupOfficeMapView(savedInstanceState)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadUserDataFromRoom() {
        if (_binding == null) return
        try {
            currentUser = userRepository.getCurrentUser()
            val user = currentUser
            if (user != null && _binding != null) {
                binding.txtProfileName.text = user.name
                binding.txtBio.text = user.bio ?: user.specialty ?: user.category ?: "Usuário do TQF"
                user.profilePictureUri?.let {
                    try {
                        if (_binding != null) {
                            binding.imgProfile.setImageURI(Uri.parse(it))
                        }
                    } catch (_: Exception) {}
                }
                user.address?.let {
                    if (it.isNotBlank()) {
                        binding.txtClientAddressDisplay.text = it
                    }
                }
                if (user.latitude != null && user.longitude != null) {
                    officeLatitude = user.latitude
                    officeLongitude = user.longitude
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Alterna a visibilidade dos elementos do layout de acordo com o PERFIL EXIBIDO.
     */
    private fun setupUserRoleViewVisibility() {
        if (isProviderProfile) {
            binding.txtBioTitle.visibility = View.VISIBLE
            binding.txtBio.visibility = View.VISIBLE
            binding.txtOfficeMapTitle.visibility = View.VISIBLE
            binding.cardMapContainer.visibility = View.VISIBLE
            binding.txtServicesLabel.text = "Serviços\nrealizados"
        } else {
            binding.txtBioTitle.visibility = View.GONE
            binding.txtBio.visibility = View.GONE
            binding.txtOfficeMapTitle.visibility = View.GONE
            binding.cardMapContainer.visibility = View.GONE
            binding.txtServicesLabel.text = "Serviços\ncontratados"
        }
    }

    /**
     * Inicializa o MapView com pré-carregamento dos recursos gráficos (evitando tela preta)
     */
    private fun setupOfficeMapView(savedInstanceState: Bundle?) {
        try {
            MapsInitializer.initialize(requireContext())
            binding.mapOffice.onCreate(savedInstanceState)
            isMapCreated = true
            binding.mapOffice.getMapAsync { map ->
                googleMap = map
                updateOfficeMapMarker()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atualiza o marcador (Marker) do escritório e centraliza a câmera nas coordenadas do prestador
     */
    private fun updateOfficeMapMarker() {
        val map = googleMap ?: return
        MapUtils.setupOfficeLocationOnMap(
            googleMap = map,
            latitude = officeLatitude,
            longitude = officeLongitude,
            title = "Escritório de " + binding.txtProfileName.text,
            snippet = "São Paulo - SP (Atendimento Presencial)"
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

        binding.menuAddresses.setOnClickListener {
            val address = currentUser?.address?.takeIf { it.isNotBlank() } ?: "Nenhum endereço cadastrado"
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Meus Endereços")
                .setMessage("Endereço atual cadastrado:\n\n$address")
                .setPositiveButton("Editar Endereço") { _, _ ->
                    showClientEditProfileDialog()
                }
                .setNegativeButton("Fechar", null)
                .show()
        }

        binding.menuFavorites.setOnClickListener {
            showToast("Meus favoritos")
        }

        binding.menuNotifications.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.menuLogout.setOnClickListener {
            performLogout()
        }

        binding.btnSeeAllServices.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ContractsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Exibe o diálogo de edição correspondente ao tipo do perfil exibido
     */
    private fun showEditProfileDialog() {
        if (isProviderProfile) {
            showProviderEditProfileDialog()
        } else {
            showClientEditProfileDialog()
        }
    }

    /**
     * Diálogo amigável para o Prestador definir a localização digitando um endereço (Geocoding)
     * sem precisar inserir Latitude e Longitude manualmente.
     */
    private fun showProviderEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_provider_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)
        val etLocation = dialogView.findViewById<TextInputEditText>(R.id.etLocation)
        val etLatitude = dialogView.findViewById<TextInputEditText>(R.id.etLatitude)
        val etLongitude = dialogView.findViewById<TextInputEditText>(R.id.etLongitude)
        val btnSearchAddress = dialogView.findViewById<View>(R.id.btnSearchAddress)
        val txtLocationStatus = dialogView.findViewById<TextView>(R.id.txtLocationStatus)

        etName?.setText(binding.txtProfileName.text)
        etBio?.setText(binding.txtBio.text)
        etLatitude?.setText(officeLatitude.toString())
        etLongitude?.setText(officeLongitude.toString())

        // Converte as coordenadas atuais em endereço legível para preencher o campo inicial
        val currentAddress = LocationUtils.getAddressFromCoordinates(requireContext(), officeLatitude, officeLongitude)
        if (!currentAddress.isNullOrEmpty()) {
            etLocation?.setText(currentAddress)
            txtLocationStatus?.visibility = View.VISIBLE
            txtLocationStatus?.text = "✓ Localização capturada: $currentAddress"
        }

        // Lógica do Botão: Converte o texto digitado pelo prestador em coordenadas automaticamente
        btnSearchAddress?.setOnClickListener {
            val addressText = etLocation?.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(requireContext(), "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val coords = LocationUtils.getCoordinatesFromAddress(requireContext(), addressText)
            if (coords != null) {
                etLatitude?.setText(coords.latitude.toString())
                etLongitude?.setText(coords.longitude.toString())
                txtLocationStatus?.visibility = View.VISIBLE
                txtLocationStatus?.text = "✓ Endereço localizado! Posição pronta no mapa."
                Toast.makeText(requireContext(), "Endereço localizado no mapa!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Endereço não encontrado. Tente incluir rua, número e cidade.", Toast.LENGTH_LONG).show()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()
                val addressText = etLocation?.text?.toString()?.trim().orEmpty()

                var newLat = etLatitude?.text?.toString()?.toDoubleOrNull()
                var newLng = etLongitude?.text?.toString()?.toDoubleOrNull()

                // Converte automaticamente o texto do endereço se o usuário digitou mas não clicou no botão
                if (addressText.isNotEmpty() && (newLat == null || newLng == null)) {
                    val coords = LocationUtils.getCoordinatesFromAddress(requireContext(), addressText)
                    if (coords != null) {
                        newLat = coords.latitude
                        newLng = coords.longitude
                    }
                }

                if (newName.isNotEmpty()) binding.txtProfileName.text = newName
                if (newBio.isNotEmpty()) binding.txtBio.text = newBio

                if (newLat != null && newLng != null) {
                    officeLatitude = newLat
                    officeLongitude = newLng
                    updateOfficeMapMarker()
                }

                // Atualiza o usuário no banco Room
                val user = currentUser
                if (user != null) {
                    val updated = user.copy(
                        name = if (newName.isNotEmpty()) newName else user.name,
                        specialty = if (newBio.isNotEmpty()) newBio else user.specialty,
                        latitude = if (newLat != null) newLat else user.latitude,
                        longitude = if (newLng != null) newLng else user.longitude
                    )
                    userRepository.updateUser(updated)
                    currentUser = updated
                }

                showToast("Perfil de prestador atualizado!")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Diálogo de edição para Contratante
     */
    private fun showClientEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_client_profile, null)
        val txtClientNameStatic = dialogView.findViewById<TextView>(R.id.txtClientNameStatic)
        val etClientAddress = dialogView.findViewById<TextInputEditText>(R.id.etClientAddress)
        val imgClientAvatarEdit = dialogView.findViewById<ImageView>(R.id.imgClientAvatarEdit)
        val btnChangeClientPhoto = dialogView.findViewById<View>(R.id.btnChangeClientPhoto)

        txtClientNameStatic?.text = binding.txtProfileName.text
        currentUser?.address?.let { etClientAddress?.setText(it) }
        clientAvatarImageViewRef = imgClientAvatarEdit

        btnChangeClientPhoto?.setOnClickListener {
            clientPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newAddress = etClientAddress?.text?.toString()?.trim().orEmpty()
                val user = currentUser
                if (user != null) {
                    val updated = user.copy(address = newAddress)
                    userRepository.updateUser(updated)
                    currentUser = updated
                    showToast("Endereço de entrega atualizado!")
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
                userRepository.logout()
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
                val detailsFragment = ServiceDetailsFragment.newInstance(
                    title = service.title,
                    professional = service.providerName.removePrefix("com "),
                    specialty = "Especialista TQF",
                    schedule = service.date,
                    price = "R$ 150,00",
                    status = service.status,
                    desc = "Serviço contratado. Acompanhe os detalhes e avaliações."
                )
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, detailsFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Repasse de ciclo de vida do MapView
    override fun onResume() {
        super.onResume()
        if (isMapCreated && _binding != null) {
            try { binding.mapOffice.onResume() } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        if (isMapCreated && _binding != null) {
            try { binding.mapOffice.onPause() } catch (_: Exception) {}
        }
        super.onPause()
    }

    override fun onDestroyView() {
        if (isMapCreated && _binding != null) {
            try { binding.mapOffice.onDestroy() } catch (_: Exception) {}
        }
        isMapCreated = false
        googleMap = null
        _binding = null
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (isMapCreated && _binding != null) {
            try { binding.mapOffice.onLowMemory() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val ARG_IS_PROVIDER_PROFILE = "arg_is_provider_profile"
        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"
        private const val ARG_PROVIDER_NAME = "arg_provider_name"

        // Coordenadas estáticas de exemplo (São Paulo, SP)
        const val DEFAULT_LATITUDE = -23.550520
        const val DEFAULT_LONGITUDE = -46.633308

        fun newInstance(
            isProviderProfile: Boolean = true,
            latitude: Double = DEFAULT_LATITUDE,
            longitude: Double = DEFAULT_LONGITUDE,
            providerName: String? = null
        ): ProfileFragment {
            val fragment = ProfileFragment()
            val args = Bundle().apply {
                putBoolean(ARG_IS_PROVIDER_PROFILE, isProviderProfile)
                putDouble(ARG_LATITUDE, latitude)
                putDouble(ARG_LONGITUDE, longitude)
                providerName?.let { putString(ARG_PROVIDER_NAME, it) }
            }
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

    private fun setupClientReviews() {
        if (_binding == null) return
        try {
            val db = try { AppDatabase.getDatabase(requireContext()) } catch (_: Exception) { null }
            val reviewDao = db?.reviewDao()
            val dbReviews = try { reviewDao?.getAll() } catch (_: Exception) { null } ?: emptyList()

            val reviewsList = if (dbReviews.isNotEmpty()) {
                dbReviews.map {
                    ClientReviewItem(
                        reviewerName = "Prestador de Serviço",
                        date = it.date,
                        comment = "⭐ ${it.rating}/5 - ${it.comment}",
                        avatarRes = R.drawable.ic_profile
                    )
                }
            } else {
                listOf(
                    ClientReviewItem(
                        reviewerName = "Paulo Oliveira",
                        date = "12 de mar. de 2025",
                        comment = "⭐ 5/5 - Ótima cliente! Comunicação excelente e pagamento pontual.",
                        avatarRes = R.drawable.ic_profile
                    )
                )
            }

            binding.rvClientReviews.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = ClientReviewAdapter(reviewsList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class ClientReviewItem(
        val reviewerName: String,
        val date: String,
        val comment: String,
        val avatarRes: Int
    )

    private inner class ClientReviewAdapter(
        private val items: List<ClientReviewItem>
    ) : RecyclerView.Adapter<ClientReviewAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                txtReviewerName.text = item.reviewerName
                txtReviewDate.text = item.date
                txtReviewComment.text = item.comment
                imgReviewerAvatar.setImageResource(item.avatarRes)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
