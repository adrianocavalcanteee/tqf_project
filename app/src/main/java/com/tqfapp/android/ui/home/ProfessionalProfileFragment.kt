package com.tqfapp.android.ui.home

import android.content.Intent
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
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils
import com.tqfapp.android.utils.UserSessionManager

class ProfessionalProfileFragment : Fragment() {

    // Instância do Google Maps e MapView
    private var googleMap: GoogleMap? = null
    private var mapView: MapView? = null
    private var officeLatitude: Double = DEFAULT_LATITUDE
    private var officeLongitude: Double = DEFAULT_LONGITUDE

    // Photo Picker launcher para Foto de Perfil
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            view?.findViewById<ImageView>(R.id.imgAvatar)?.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de perfil atualizada!", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker launcher para Foto de Capa
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            view?.findViewById<ImageView>(R.id.imgCover)?.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de capa atualizada!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_professional_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false

        // Carrega dados dinâmicos passados via Bundle
        val name = arguments?.getString(ARG_NAME)
        val specialty = arguments?.getString(ARG_SPECIALTY)
        val rating = arguments?.getString(ARG_RATING)
        val bio = arguments?.getString(ARG_BIO)

        if (!name.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtProfessionalName)?.text = name
        }
        if (!specialty.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtSpecialty)?.text = specialty
        }
        if (!rating.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtRating)?.text = rating
        }
        if (!bio.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtBio)?.text = bio
        }

        // Carrega as coordenadas específicas do prestador com fallback seguro para São Paulo
        officeLatitude = arguments?.getDouble(ARG_LATITUDE, DEFAULT_LATITUDE) ?: DEFAULT_LATITUDE
        officeLongitude = arguments?.getDouble(ARG_LONGITUDE, DEFAULT_LONGITUDE) ?: DEFAULT_LONGITUDE

        setupProfileMode(view, isOwner)
        setupClickListeners(view, isOwner)
        setupPortfolio(view)
        setupReviews(view)

        // REGRA: Como este fragmento exibe o perfil de um Prestador, o mapa e o marcador SEMPRE aparecem
        val txtOfficeMapTitle = view.findViewById<View>(R.id.txtOfficeMapTitle)
        val cardMapContainer = view.findViewById<View>(R.id.cardMapContainer)
        mapView = view.findViewById(R.id.mapOffice)

        txtOfficeMapTitle?.visibility = View.VISIBLE
        cardMapContainer?.visibility = View.VISIBLE

        setupOfficeMapView(view, savedInstanceState)
    }

    /**
     * Inicializa o MapView evitando problemas de renderização (tela preta)
     */
    private fun setupOfficeMapView(view: View, savedInstanceState: Bundle?) {
        try {
            MapsInitializer.initialize(requireContext())
            mapView?.onCreate(savedInstanceState)
            mapView?.getMapAsync { map ->
                googleMap = map
                updateOfficeMapMarker(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atualiza o marcador do escritório e centraliza a câmera no mapa
     */
    private fun updateOfficeMapMarker(view: View) {
        val map = googleMap ?: return
        val txtName = view.findViewById<TextView>(R.id.txtProfessionalName)?.text?.toString() ?: "Prestador"
        MapUtils.setupOfficeLocationOnMap(
            googleMap = map,
            latitude = officeLatitude,
            longitude = officeLongitude,
            title = "Escritório de $txtName",
            snippet = "São Paulo - SP (Atendimento Presencial)"
        )
    }

    private fun setupProfileMode(view: View, isOwner: Boolean) {
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)
        val btnChangeCover = view.findViewById<View>(R.id.btnChangeCover)
        val btnChangeAvatar = view.findViewById<View>(R.id.btnChangeAvatar)
        val layoutActionButtons = view.findViewById<View>(R.id.layoutActionButtons)
        val cardProviderMenu = view.findViewById<View>(R.id.cardProviderMenu)

        if (isOwner) {
            btnEditProfile?.visibility = View.VISIBLE
            btnChangeCover?.visibility = View.VISIBLE
            btnChangeAvatar?.visibility = View.VISIBLE
            cardProviderMenu?.visibility = View.VISIBLE
            layoutActionButtons?.visibility = View.GONE
        } else {
            layoutActionButtons?.visibility = View.VISIBLE
            btnEditProfile?.visibility = View.GONE
            btnChangeCover?.visibility = View.GONE
            btnChangeAvatar?.visibility = View.GONE
            cardProviderMenu?.visibility = View.GONE
        }
    }

    private fun setupClickListeners(view: View, isOwner: Boolean) {
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<View>(R.id.btnFavorite)?.setOnClickListener {
            showToast("Adicionado aos favoritos")
        }

        view.findViewById<View>(R.id.btnShare)?.setOnClickListener {
            showToast("Compartilhar perfil")
        }

        view.findViewById<View>(R.id.menuLogoutProvider)?.setOnClickListener {
            performLogout()
        }

        view.findViewById<View>(R.id.btnChangeAvatar)?.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.imgAvatar)?.setOnClickListener {
            if (isOwner) {
                pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        view.findViewById<View>(R.id.btnChangeCover)?.setOnClickListener {
            pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.imgCover)?.setOnClickListener {
            if (isOwner) {
                pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            showEditProfileDialog(view)
        }

        view.findViewById<View>(R.id.btnHireNow)?.setOnClickListener {
            showToast("Iniciando contratação...")
        }

        view.findViewById<View>(R.id.btnCallChat)?.setOnClickListener {
            showToast("Abrindo conversa no chat")
        }

        view.findViewById<View>(R.id.btnSeeAllPortfolio)?.setOnClickListener {
            showToast("Ver todo o portfólio")
        }

        view.findViewById<View>(R.id.btnSeeAllReviews)?.setOnClickListener {
            showToast("Ver todas as avaliações")
        }
    }

    /**
     * Diálogo amigável para o Prestador definir o endereço do escritório
     */
    private fun showEditProfileDialog(rootView: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_provider_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etSpecialty = dialogView.findViewById<TextInputEditText>(R.id.etSpecialty)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)
        val etLocation = dialogView.findViewById<TextInputEditText>(R.id.etLocation)
        val etLatitude = dialogView.findViewById<TextInputEditText>(R.id.etLatitude)
        val etLongitude = dialogView.findViewById<TextInputEditText>(R.id.etLongitude)
        val btnSearchAddress = dialogView.findViewById<View>(R.id.btnSearchAddress)
        val txtLocationStatus = dialogView.findViewById<TextView>(R.id.txtLocationStatus)

        val txtName = rootView.findViewById<TextView>(R.id.txtProfessionalName)
        val txtSpecialty = rootView.findViewById<TextView>(R.id.txtSpecialty)
        val txtBio = rootView.findViewById<TextView>(R.id.txtBio)

        etName?.setText(txtName?.text)
        etSpecialty?.setText(txtSpecialty?.text)
        etBio?.setText(txtBio?.text)
        etLatitude?.setText(officeLatitude.toString())
        etLongitude?.setText(officeLongitude.toString())

<<<<<<< HEAD
        // Converte as coordenadas atuais em endereço legível
        val currentAddress = LocationUtils.getAddressFromCoordinates(requireContext(), officeLatitude, officeLongitude)
        if (!currentAddress.isNullOrEmpty()) {
            etLocation?.setText(currentAddress)
            txtLocationStatus?.visibility = View.VISIBLE
            txtLocationStatus?.text = "✓ Localização capturada: $currentAddress"
        }

        // Converte o texto do endereço digitado pelo prestador em coordenadas
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
=======
        val userRepository = com.tqfapp.android.data.repository.UserRepository(requireContext())
>>>>>>> origin/feature/database-persistence

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newSpecialty = etSpecialty?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()
                val addressText = etLocation?.text?.toString()?.trim().orEmpty()

                var newLat = etLatitude?.text?.toString()?.toDoubleOrNull()
                var newLng = etLongitude?.text?.toString()?.toDoubleOrNull()

                // Se digitou endereço e não buscou manualmente, converte ao salvar
                if (addressText.isNotEmpty() && (newLat == null || newLng == null)) {
                    val coords = LocationUtils.getCoordinatesFromAddress(requireContext(), addressText)
                    if (coords != null) {
                        newLat = coords.latitude
                        newLng = coords.longitude
                    }
                }

                if (newName.isNotEmpty()) txtName?.text = newName
                if (newSpecialty.isNotEmpty()) txtSpecialty?.text = newSpecialty
                if (newBio.isNotEmpty()) txtBio?.text = newBio

                if (newLat != null && newLng != null) {
                    officeLatitude = newLat
                    officeLongitude = newLng
                    updateOfficeMapMarker(rootView)
                }

                val user = userRepository.getCurrentUser()
                if (user != null) {
                    val updated = user.copy(
                        name = if (newName.isNotEmpty()) newName else user.name,
                        specialty = if (newSpecialty.isNotEmpty()) newSpecialty else user.specialty,
                        latitude = if (newLat != null) newLat else user.latitude,
                        longitude = if (newLng != null) newLng else user.longitude
                    )
                    userRepository.updateUser(updated)
                }

                showToast("Perfil de prestador atualizado!")
            }
            .setNegativeButton("Sair da conta") { _, _ ->
                performLogout()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun performLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza que deseja encerrar sua sessão de prestador?")
            .setPositiveButton("Sair") { _, _ ->
                val userRepository = com.tqfapp.android.data.repository.UserRepository(requireContext())
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

    private fun setupPortfolio(view: View) {
        val portfolioImages = listOf(
            R.drawable.ic_profile,
            R.drawable.ic_profile,
            R.drawable.ic_profile,
            R.drawable.ic_profile
        )

        view.findViewById<RecyclerView>(R.id.rvPortfolio)?.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = PortfolioAdapter(portfolioImages) {
                showToast("Visualizando imagem do portfólio")
            }
        }
    }

    private fun setupReviews(view: View) {
        val reviewDao = com.tqfapp.android.data.local.database.AppDatabase.getDatabase(requireContext()).reviewDao()
        val dbReviews = kotlinx.coroutines.runBlocking { reviewDao.getAll() }

        val reviewsList = if (dbReviews.isNotEmpty()) {
            dbReviews.map {
                ReviewItem(
                    reviewerName = "Cliente",
                    date = it.date,
                    comment = it.comment,
                    avatarRes = R.drawable.ic_profile
                )
            }
        } else {
            val initialReview = com.tqfapp.android.data.model.Review(
                id = "r1",
                serviceId = "s1",
                providerId = "p1",
                clientId = "c1",
                rating = 5,
                comment = "Excelente profissional! Resolveu meu problema com muita agilidade. Recomendo!",
                date = "10 de mar. de 2025"
            )
            kotlinx.coroutines.runBlocking { reviewDao.insert(initialReview) }

            listOf(
                ReviewItem(
                    reviewerName = "Mariana Costa",
                    date = initialReview.date,
                    comment = initialReview.comment,
                    avatarRes = R.drawable.ic_profile
                )
            )
        }

        view.findViewById<RecyclerView>(R.id.rvReviews)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ReviewAdapter(reviewsList) {
                showToast("Avaliação de ${it.reviewerName}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Passagem de Ciclo de Vida para o MapView evitar telas pretas
    override fun onResume() {
        super.onResume()
        try { mapView?.onResume() } catch (_: Exception) {}
    }

    override fun onPause() {
        try { mapView?.onPause() } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroyView() {
        try { mapView?.onDestroy() } catch (_: Exception) {}
        googleMap = null
        mapView = null
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try { mapView?.onLowMemory() } catch (_: Exception) {}
    }

    companion object {
        private const val ARG_IS_OWNER = "arg_is_owner"
        private const val ARG_NAME = "arg_name"
        private const val ARG_SPECIALTY = "arg_specialty"
        private const val ARG_RATING = "arg_rating"
        private const val ARG_BIO = "arg_bio"
        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"

        // Coordenadas estáticas de exemplo (São Paulo, SP)
        const val DEFAULT_LATITUDE = -23.550520
        const val DEFAULT_LONGITUDE = -46.633308

        fun newInstance(
            isOwner: Boolean = false,
            name: String? = null,
            specialty: String? = null,
            rating: String? = null,
            bio: String? = null,
            latitude: Double = DEFAULT_LATITUDE,
            longitude: Double = DEFAULT_LONGITUDE
        ): ProfessionalProfileFragment {
            val fragment = ProfessionalProfileFragment()
            val args = Bundle().apply {
                putBoolean(ARG_IS_OWNER, isOwner)
                name?.let { putString(ARG_NAME, it) }
                specialty?.let { putString(ARG_SPECIALTY, it) }
                rating?.let { putString(ARG_RATING, it) }
                bio?.let { putString(ARG_BIO, it) }
                putDouble(ARG_LATITUDE, latitude)
                putDouble(ARG_LONGITUDE, longitude)
            }
            fragment.arguments = args
            return fragment
        }
    }

    data class ReviewItem(
        val reviewerName: String,
        val date: String,
        val comment: String,
        val avatarRes: Int
    )

    private inner class PortfolioAdapter(
        private val items: List<Int>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PortfolioAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val imgPortfolio: ImageView = view.findViewById(R.id.imgPortfolio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_portfolio, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.imgPortfolio.setImageResource(item)
            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    private inner class ReviewAdapter(
        private val items: List<ReviewItem>,
        private val onItemClick: (ReviewItem) -> Unit
    ) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtReviewerName: TextView = view.findViewById(R.id.txtReviewerName)
            val txtReviewDate: TextView = view.findViewById(R.id.txtReviewDate)
            val txtReviewComment: TextView = view.findViewById(R.id.txtReviewComment)
            val imgReviewerAvatar: ImageView = view.findViewById(R.id.imgReviewerAvatar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtReviewerName.text = item.reviewerName
            holder.txtReviewDate.text = item.date
            holder.txtReviewComment.text = item.comment
            holder.imgReviewerAvatar.setImageResource(item.avatarRes)
            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
