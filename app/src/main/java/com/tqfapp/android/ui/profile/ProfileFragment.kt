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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.data.model.label
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.data.repository.ServiceRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.ui.common.loadImage
import com.tqfapp.android.ui.common.loadProviderPhoto
import com.tqfapp.android.ui.common.applyStatusBadge

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
        if (uri != null) uploadAvatar(uri)
    }

    private var clientAvatarImageViewRef: ImageView? = null

    private val clientPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) uploadAvatar(uri)
    }

    /**
     * Foto de perfil (tela e diálogo de edição): antes era gravada como URI local (content://),
     * que nenhum outro aparelho abre. Agora sobe ao Storage em IO; se falhar, volta a foto anterior.
     */
    private fun uploadAvatar(uri: Uri) {
        val previous = currentUser?.profilePictureUri
        clientAvatarImageViewRef?.setImageURI(uri)
        if (_binding != null) binding.imgProfile.setImageURI(uri)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UserRepository(appContext).updateProfilePicture(uri) }
            result.onSuccess { url ->
                currentUser = currentUser?.copy(profilePictureUri = url)
                clientAvatarImageViewRef?.loadImage(url)
                if (_binding != null) binding.imgProfile.loadImage(url)
                showToast("Foto de perfil atualizada!")
            }.onFailure {
                clientAvatarImageViewRef?.loadImage(previous)
                if (_binding != null) binding.imgProfile.loadImage(previous)
                showToast(it.message ?: "Não foi possível atualizar a foto.")
            }
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
                // Ano de criação da conta no Supabase Auth (sem valor fixo no layout).
                val memberSinceYear = SupabaseClient.auth.currentUserOrNull()?.createdAt?.toString()?.take(4)
                binding.txtMemberSince.text = memberSinceYear?.let {
                    if (isProviderProfile) "Prestador desde $it" else "Cliente desde $it"
                } ?: if (isProviderProfile) "Prestador TQF" else "Cliente TQF"
                binding.txtBio.text = user.bio ?: user.specialty ?: user.category ?: "Usuário do TQF"
                if (_binding != null) binding.imgProfile.loadImage(user.profilePictureUri)
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
                MapUtils.applyCustomMapStyle(requireContext(), map)
                updateOfficeMapMarker()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atualiza o marcador (Marker) do escritório e centraliza a câmera nas coordenadas do prestador com Geocoder dinâmico
     */
    private fun updateOfficeMapMarker() {
        val map = googleMap ?: return

        // Converte as coordenadas de latitude e longitude reais em um endereço legível usando Geocoder
        val userAddress = currentUser?.address
        // Endereço salvo no perfil (sem Geocoder na thread principal).
        val addressText = userAddress?.takeIf { it.isNotBlank() } ?: "Endereço não informado"

        // Sincroniza o card/bloco de texto do endereço no perfil
        if (_binding != null) {
            binding.txtClientAddressDisplay.text = addressText
        }

        MapUtils.setupOfficeLocationOnMap(
            googleMap = map,
            latitude = officeLatitude,
            longitude = officeLongitude,
            title = "Escritório de " + binding.txtProfileName.text,
            snippet = addressText
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

        val cardDialogMap = dialogView.findViewById<View>(R.id.cardDialogMap)
        val dialogMapView = dialogView.findViewById<MapView>(R.id.dialogMapView)
        var dialogGoogleMap: GoogleMap? = null

        fun updateDialogMapMarker(map: GoogleMap, coords: LatLng, title: String) {
            map.clear()
            map.uiSettings.isZoomControlsEnabled = true
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(coords, 15f))
            map.addMarker(
                MarkerOptions()
                    .position(coords)
                    .title("📍 $title")
                    .snippet("Local do Escritório")
            )?.showInfoWindow()
        }

        if (dialogMapView != null) {
            try {
                MapsInitializer.initialize(requireContext())
                dialogMapView.onCreate(null)
                dialogMapView.onResume()
                dialogMapView.getMapAsync { map ->
                    dialogGoogleMap = map
                    MapUtils.applyCustomMapStyle(requireContext(), map)
                    if (officeLatitude != 0.0 && officeLongitude != 0.0) {
                        cardDialogMap?.visibility = View.VISIBLE
                        updateDialogMapMarker(map,
                            LatLng(officeLatitude, officeLongitude), etLocation?.text?.toString() ?: "Escritório")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Converte as coordenadas atuais em endereço legível para preencher o campo inicial
        // Geocoder faz rede: consulta em IO, tela atualizada ao terminar.
        val geoContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val currentAddress = withContext(Dispatchers.IO) { LocationUtils.getAddressFromCoordinates(geoContext, officeLatitude, officeLongitude) }
            if (!currentAddress.isNullOrEmpty()) {
                etLocation?.setText(currentAddress)
                txtLocationStatus?.visibility = View.VISIBLE
                txtLocationStatus?.text = "✓ Localização capturada: $currentAddress"
            }
        }

        // Lógica do Botão: Converte o texto digitado pelo prestador em coordenadas automaticamente
        btnSearchAddress?.setOnClickListener {
            val addressText = etLocation?.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(requireContext(), "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Geocoder faz rede: consulta em IO, tela atualizada ao terminar.
            val geoContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val coords = withContext(Dispatchers.IO) { LocationUtils.getCoordinatesFromAddress(geoContext, addressText) }
                if (coords != null) {
                    etLatitude?.setText(coords.latitude.toString())
                    etLongitude?.setText(coords.longitude.toString())
                    txtLocationStatus?.visibility = View.VISIBLE
                    txtLocationStatus?.text = "✓ Endereço localizado! Posição pronta no mapa."

                    cardDialogMap?.visibility = View.VISIBLE
                    dialogGoogleMap?.let { map ->
                        updateDialogMapMarker(map, coords, addressText)
                    }

                    Toast.makeText(requireContext(), "Endereço localizado e posicionado no mapa!", Toast.LENGTH_SHORT).show()
                } else {
                    txtLocationStatus?.visibility = View.VISIBLE
                    txtLocationStatus?.text = "⚠️ Endereço não encontrado. Verifique os dados digitados."
                    Toast.makeText(requireContext(), "Endereço não encontrado. Tente incluir rua, número e cidade.", Toast.LENGTH_LONG).show()
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()
                val addressText = etLocation?.text?.toString()?.trim().orEmpty()

                val typedLat = etLatitude?.text?.toString()?.toDoubleOrNull()
                val typedLng = etLongitude?.text?.toString()?.toDoubleOrNull()
                if (newName.isNotEmpty()) binding.txtProfileName.text = newName
                if (newBio.isNotEmpty()) binding.txtBio.text = newBio

                // Geocoding (se o endereço não foi localizado pelo botão) e gravação em IO, nessa ordem.
                val appContext = requireContext().applicationContext
                val user = currentUser ?: return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        val coords = if (addressText.isNotEmpty() && (typedLat == null || typedLng == null)) {
                            LocationUtils.getCoordinatesFromAddress(appContext, addressText)
                        } else null
                        val changed = user.copy(
                            name = newName.ifEmpty { user.name },
                            specialty = newBio.ifEmpty { user.specialty },
                            latitude = coords?.latitude ?: typedLat ?: user.latitude,
                            longitude = coords?.longitude ?: typedLng ?: user.longitude
                        )
                        if (UserRepository(appContext).updateUser(changed)) changed else null
                    }
                    if (_binding == null) return@launch
                    if (updated == null) {
                        showToast("Não foi possível salvar o perfil. Tente novamente.")
                        return@launch
                    }
                    currentUser = updated
                    officeLatitude = updated.latitude ?: officeLatitude
                    officeLongitude = updated.longitude ?: officeLongitude
                    updateOfficeMapMarker()
                    showToast("Perfil de prestador atualizado!")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Diálogo de edição para Contratante com busca por Geocodificação e Mapa de Confirmação
     */
    private fun showClientEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_client_profile, null)
        val txtClientNameStatic = dialogView.findViewById<TextView>(R.id.txtClientNameStatic)
        val etClientAddress = dialogView.findViewById<TextInputEditText>(R.id.etClientAddress)
        val imgClientAvatarEdit = dialogView.findViewById<ImageView>(R.id.imgClientAvatarEdit)
        val btnChangeClientPhoto = dialogView.findViewById<View>(R.id.btnChangeClientPhoto)
        val btnClientSearchAddress = dialogView.findViewById<View>(R.id.btnClientSearchAddress)
        val txtClientLocationStatus = dialogView.findViewById<TextView>(R.id.txtClientLocationStatus)
        val cardClientDialogMap = dialogView.findViewById<View>(R.id.cardClientDialogMap)
        val dialogClientMapView = dialogView.findViewById<MapView>(R.id.dialogClientMapView)

        txtClientNameStatic?.text = binding.txtProfileName.text
        currentUser?.address?.let { etClientAddress?.setText(it) }
        clientAvatarImageViewRef = imgClientAvatarEdit

        btnChangeClientPhoto?.setOnClickListener {
            clientPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        var clientLat: Double? = currentUser?.latitude
        var clientLng: Double? = currentUser?.longitude
        var clientGoogleMap: GoogleMap? = null

        fun updateClientDialogMapMarker(map: GoogleMap, coords: LatLng, title: String) {
            map.clear()
            map.uiSettings.isZoomControlsEnabled = true
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(coords, 15f))

            val customDotIcon = MapUtils.getBitmapDescriptorFromVector(
                requireContext(),
                R.drawable.ic_user_location_dot
            )

            val markerOptions = MarkerOptions()
                .position(coords)
                .title("📍 $title")
                .snippet("Seu Endereço de Entrega / Residência")
                .anchor(0.5f, 0.5f)

            if (customDotIcon != null) {
                markerOptions.icon(customDotIcon)
            }

            map.addMarker(markerOptions)?.showInfoWindow()

            map.addCircle(
                CircleOptions()
                    .center(coords)
                    .radius(500.0)
                    .strokeWidth(2f)
                    .strokeColor(ContextCompat.getColor(requireContext(), R.color.tqf_primary))
                    .fillColor(ContextCompat.getColor(requireContext(), R.color.tqf_map_radius_fill))
            )
        }

        if (dialogClientMapView != null) {
            try {
                MapsInitializer.initialize(requireContext())
                dialogClientMapView.onCreate(null)
                dialogClientMapView.onResume()
                dialogClientMapView.getMapAsync { map ->
                    clientGoogleMap = map
                    MapUtils.applyCustomMapStyle(requireContext(), map)

                    val initialAddress = currentUser?.address
                    if (!initialAddress.isNullOrBlank()) {
                        // Geocoder faz rede: consulta em IO, tela atualizada ao terminar.
                        val geoContext = requireContext().applicationContext
                        viewLifecycleOwner.lifecycleScope.launch {
                            val coords = withContext(Dispatchers.IO) { LocationUtils.getCoordinatesFromAddress(geoContext, initialAddress) }
                            if (coords != null) {
                                clientLat = coords.latitude
                                clientLng = coords.longitude
                                cardClientDialogMap?.visibility = View.VISIBLE
                                txtClientLocationStatus?.visibility = View.VISIBLE
                                txtClientLocationStatus?.text = "✓ Localização capturada no mapa"
                                updateClientDialogMapMarker(map, coords, initialAddress)
                            }
                        }
                    } else if (clientLat != null && clientLng != null) {
                        cardClientDialogMap?.visibility = View.VISIBLE
                        updateClientDialogMapMarker(map, LatLng(clientLat!!, clientLng!!), "Sua Residência")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnClientSearchAddress?.setOnClickListener {
            val addressText = etClientAddress?.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(requireContext(), "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Geocoder faz rede: consulta em IO, tela atualizada ao terminar.
            val geoContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val coords = withContext(Dispatchers.IO) { LocationUtils.getCoordinatesFromAddress(geoContext, addressText) }
                if (coords != null) {
                    clientLat = coords.latitude
                    clientLng = coords.longitude

                    txtClientLocationStatus?.visibility = View.VISIBLE
                    txtClientLocationStatus?.text = "✓ Endereço localizado! Posição pronta no mapa."
                    cardClientDialogMap?.visibility = View.VISIBLE

                    clientGoogleMap?.let { map ->
                        updateClientDialogMapMarker(map, coords, addressText)
                    }

                    Toast.makeText(requireContext(), "Endereço localizado e posicionado no mapa!", Toast.LENGTH_SHORT).show()
                } else {
                    txtClientLocationStatus?.visibility = View.VISIBLE
                    txtClientLocationStatus?.text = "⚠️ Endereço não encontrado. Verifique os dados digitados."
                    Toast.makeText(requireContext(), "Endereço não encontrado. Tente incluir rua, número e cidade.", Toast.LENGTH_LONG).show()
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newAddress = etClientAddress?.text?.toString()?.trim().orEmpty()

                val user = currentUser ?: return@setPositiveButton
                val knownLat = clientLat
                val knownLng = clientLng
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        val coords = if (newAddress.isNotEmpty() && (knownLat == null || knownLng == null)) {
                            LocationUtils.getCoordinatesFromAddress(appContext, newAddress)
                        } else null
                        val changed = user.copy(
                            address = newAddress,
                            latitude = coords?.latitude ?: knownLat ?: user.latitude,
                            longitude = coords?.longitude ?: knownLng ?: user.longitude
                        )
                        if (UserRepository(appContext).updateUser(changed)) changed else null
                    }
                    if (updated == null) {
                        showToast("Não foi possível salvar o endereço. Tente novamente.")
                        return@launch
                    }
                    currentUser = updated
                    if (_binding != null) binding.txtClientAddressDisplay.text = newAddress
                    showToast("Endereço de entrega atualizado com sucesso!")
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

    /**
     * "Serviços contratados/realizados" e a lista de recentes, lidos do Supabase (cache local só offline).
     * Contratante: solicitações aceitas (em andamento ou concluídas). Prestador: concluídas por ele.
     */
    private fun setupRecentServices() {
        if (_binding == null) return
        val user = currentUser ?: run {
            showRecentServices(emptyList(), 0)
            return
        }
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val (services, count) = withContext(Dispatchers.IO) {
                try {
                    val repository = ServiceRepository(appContext)
                    if (isProviderProfile) {
                        val completed = repository.getProviderCompletedServices(user.id)
                        (repository.getProviderContracts(user.id) + completed) to completed.size
                    } else {
                        val hired = repository.getClientRequests(user.id).filter {
                            it.status == ServiceStatus.EM_ANDAMENTO || it.status == ServiceStatus.CONCLUIDO
                        }
                        hired to hired.size
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<Service>() to 0
                }
            }
            showRecentServices(services.sortedByDescending { it.date }, count)
        }
    }

    private fun showRecentServices(services: List<Service>, count: Int) {
        if (_binding == null) return
        binding.txtServicesValue.text = count.toString()
        binding.txtNoRecentServices.visibility = if (services.isEmpty()) View.VISIBLE else View.GONE

        val recentServices = services.take(4)
        val mappedRecentItems = recentServices.map { s ->
            RecentServiceItem(
                title = s.title,
                providerName = if (isProviderProfile) {
                    "Cliente: ${s.clientName.ifBlank { "Cliente" }}"
                } else {
                    "com ${s.providerName.ifBlank { "Prestador" }}"
                },
                date = s.date,
                status = s.status,
                category = s.category
            )
        }

        binding.rvRecentServices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = RecentServiceAdapter(mappedRecentItems) { item ->
                val s = recentServices[mappedRecentItems.indexOfFirst { it === item }]
                val detailsFragment = ServiceDetailsFragment.newInstance(
                    title = s.title,
                    professional = s.providerName.ifBlank { "Prestador" },
                    specialty = s.category,
                    schedule = s.date,
                    price = if (s.price > 0) User.formatCurrency(s.price) else "Valor negociado",
                    status = s.status.label(),
                    desc = s.description,
                    serviceId = s.id,
                    providerId = s.providerId,
                    clientId = s.clientId
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
        try {
            loadUserDataFromRoom()
            setupRecentServices()
            setupClientReviews()
        } catch (_: Exception) {}
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

        // Coordenadas padrão de exemplo (Manaus, AM)
        const val DEFAULT_LATITUDE = -3.1190
        const val DEFAULT_LONGITUDE = -60.0217

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
        val status: ServiceStatus,
        val category: String
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
                txtStatus.applyStatusBadge(item.status)
                imgService.loadProviderPhoto(null, item.category)
                root.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun setupClientReviews() {
        if (_binding == null) return
        val currentUserId = currentUser?.id ?: return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            // Avaliações RECEBIDAS pelo cliente (reviewed_id = cliente). As que ele escreveu
            // pertencem ao perfil do prestador avaliado e não devem aparecer aqui.
            val dbReviews = withContext(Dispatchers.IO) {
                ReviewRepository(appContext).getReviewsByProvider(currentUserId)
            }
            if (_binding != null) showClientReviews(dbReviews)
        }
    }

    private fun showClientReviews(dbReviews: List<Review>) {
        try {
            binding.txtRatingValue.text = RatingFormat.average(dbReviews)?.let { RatingFormat.value(it) } ?: "–"

            val reviewsList = dbReviews.map {
                ClientReviewItem(
                    reviewerName = "Prestador",
                    date = it.date,
                    comment = "⭐ ${it.rating}/5 - ${it.comment}",
                    avatarRes = R.drawable.ic_profile
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
