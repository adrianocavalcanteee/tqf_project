package com.tqfapp.android.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceCategories
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.EmailConfirmationPendingException
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityRegisterBinding
import com.tqfapp.android.ui.home.HomeActivity
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var userRepository: UserRepository

    private var isProviderMode: Boolean = false
    private var selectedAvatarUri: Uri? = null
    private val selectedPortfolioUris = mutableListOf<Uri>()
    private lateinit var portfolioAdapter: PortfolioPreviewAdapter

    // Launcher para selecionar Foto de Perfil
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            binding.imgRegisterAvatar.setImageURI(uri)
            binding.txtSelectAvatarLabel.text = "Alterar Foto de Perfil"
            Toast.makeText(this, "Foto de perfil selecionada!", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para selecionar Imagens do Portfólio (Máximo 5)
    private val pickPortfolioLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (selectedPortfolioUris.size < 5 && !selectedPortfolioUris.contains(uri)) {
                    selectedPortfolioUris.add(uri)
                }
            }
            portfolioAdapter.notifyDataSetChanged()
            Toast.makeText(this, "${selectedPortfolioUris.size} foto(s) no portfólio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

        setupUserTypeToggle()
        setupMapPreview()
        setupCategoryDropdown()
        setupPhotoPickers()
        setupPortfolioRecyclerView()
        setupClickListeners()
    }

    private fun setupUserTypeToggle() {
        binding.toggleRegisterUserType.check(R.id.btnRegisterContractor)
        binding.toggleRegisterUserType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnRegisterContractor -> {
                        isProviderMode = false
                        binding.layoutProviderFields.visibility = View.GONE
                        binding.cardRegisterMap.visibility = View.GONE
                        binding.btnRegisterContractor.setTextColor(getColor(R.color.brand_blue))
                        binding.btnRegisterProvider.setTextColor(getColor(R.color.text_secondary))
                    }
                    R.id.btnRegisterProvider -> {
                        isProviderMode = true
                        binding.layoutProviderFields.visibility = View.VISIBLE
                        // O mapa só aparece depois de buscar o endereço, então manteremos oculto ao alternar
                        binding.cardRegisterMap.visibility = View.GONE
                        binding.btnRegisterProvider.setTextColor(getColor(R.color.brand_blue))
                        binding.btnRegisterContractor.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            }
        }
    }

    private fun setupCategoryDropdown() {
        val categories = ServiceCategories.ALL

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategory.setAdapter(adapter)
    }

    private fun setupPhotoPickers() {
        // Seleção de Foto de Perfil
        binding.containerAvatar.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.txtSelectAvatarLabel.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Seleção de Fotos de Portfólio
        binding.btnAddPortfolio.setOnClickListener {
            if (selectedPortfolioUris.size >= 5) {
                Toast.makeText(this, "Limite máximo de 5 fotos atingido.", Toast.LENGTH_SHORT).show()
            } else {
                pickPortfolioLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    private fun setupPortfolioRecyclerView() {
        portfolioAdapter = PortfolioPreviewAdapter(selectedPortfolioUris) { uriToRemove ->
            selectedPortfolioUris.remove(uriToRemove)
            portfolioAdapter.notifyDataSetChanged()
        }

        binding.rvPortfolioPreviews.apply {
            layoutManager = LinearLayoutManager(this@RegisterActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = portfolioAdapter
        }
    }

    private var registerGoogleMap: GoogleMap? = null
    private var isRegisterMapCreated = false

    override fun onResume() {
        super.onResume()
        if (isRegisterMapCreated) binding.registerMapView.onResume()
    }

    override fun onPause() {
        if (isRegisterMapCreated) binding.registerMapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (isRegisterMapCreated) binding.registerMapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (isRegisterMapCreated) binding.registerMapView.onLowMemory()
    }

    private fun setupMapPreview() {
        try {
            MapsInitializer.initialize(this)
            binding.registerMapView.onCreate(null)
            isRegisterMapCreated = true
            binding.registerMapView.getMapAsync { map ->
                registerGoogleMap = map
                MapUtils.applyCustomMapStyle(this, map)
                map.uiSettings.isZoomControlsEnabled = true
            }

            // Impede conflito de rolagem ao arrastar o mapa dentro do ScrollView
            binding.registerMapTouchOverlay.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        binding.root.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        binding.root.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMapMarker(coords: LatLng, title: String) {
        registerGoogleMap?.let { map ->
            map.clear()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(coords, 15f))
            map.addMarker(
                MarkerOptions()
                    .position(coords)
                    .title("📍 Seu Escritório")
                    .snippet(title)
            )?.showInfoWindow()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.txtLogin.setOnClickListener {
            finish()
        }

        // Botão para buscar endereço e converter em coordenadas
        binding.btnSearchAddress.setOnClickListener {
            val addressText = binding.etLocation.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(this, "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSearchAddress.isEnabled = false
            lifecycleScope.launch {
                val coords = withContext(Dispatchers.IO) {
                    LocationUtils.getCoordinatesFromAddress(applicationContext, addressText)
                }
                binding.btnSearchAddress.isEnabled = true
                showLocatedAddress(addressText, coords)
            }
        }

        binding.etLocation.doAfterTextChanged {
            binding.etLatitude.text?.clear()
            binding.etLongitude.text?.clear()
        }

        binding.btnRegister.setOnClickListener {
            performRegistration()
        }
    }

    private fun showLocatedAddress(addressText: String, coords: LatLng?) {
        if (coords != null) {
            binding.etLatitude.setText(coords.latitude.toString())
            binding.etLongitude.setText(coords.longitude.toString())
            binding.txtLocationStatus.visibility = View.VISIBLE
            binding.txtLocationStatus.text = "✓ Endereço localizado com sucesso!"

            binding.cardRegisterMap.visibility = View.VISIBLE
            updateMapMarker(coords, addressText)

            Toast.makeText(this, "Endereço localizado no mapa!", Toast.LENGTH_SHORT).show()
        } else {
            binding.cardRegisterMap.visibility = View.GONE
            binding.txtLocationStatus.visibility = View.VISIBLE
            binding.txtLocationStatus.text = "⚠️ Endereço não encontrado."
            Toast.makeText(this, "Endereço não encontrado. Tente incluir rua, número e cidade.", Toast.LENGTH_LONG).show()
        }
    }

    private fun performRegistration() {
        clearErrors()

        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

        // Validação dos Campos Obrigatórios
        var isValid = true

        if (name.isEmpty()) {
            binding.tilName.error = "Digite seu nome completo"
            isValid = false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Digite um e-mail válido"
            isValid = false
        }

        if (phone.isEmpty()) {
            binding.tilPhone.error = "Digite seu telefone / WhatsApp"
            isValid = false
        }

        if (password.length < 6) {
            binding.tilPassword.error = "A senha deve conter no mínimo 6 caracteres"
            isValid = false
        }

        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "As senhas não coincidem"
            isValid = false
        }

        // Validação Específica para Prestador de Serviço
        var category: String? = null
        var specialty: String? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var officeAddress: String? = null

        if (isProviderMode) {
            category = binding.actvCategory.text?.toString()?.trim().orEmpty()
            specialty = binding.etSpecialty.text?.toString()?.trim().orEmpty()

            officeAddress = binding.etLocation.text?.toString()?.trim().orEmpty()
            // Coordenadas reais do endereço: as do botão "Localizar" ou um novo geocoding. Sem endereço
            // localizado o prestador apareceria no mapa num lugar falso, então o cadastro é bloqueado.
            latitude = binding.etLatitude.text?.toString()?.toDoubleOrNull()
            longitude = binding.etLongitude.text?.toString()?.toDoubleOrNull()
            if (officeAddress.isEmpty()) {
                binding.tilLocation.error = "Informe o endereço onde você atende"
                isValid = false
            }

            if (category.isEmpty()) {
                binding.tilCategory.error = "Selecione a categoria do seu serviço"
                isValid = false
            }

            if (specialty.isEmpty()) {
                binding.tilSpecialty.error = "Digite sua especialidade"
                isValid = false
            }
        }

        if (!isValid) return

        // Cria a instância do modelo de usuário pronto para integração
        val newUser = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            telephone = phone,
            isProvider = isProviderMode,
            category = category,
            specialty = specialty,
            profilePictureUri = selectedAvatarUri?.toString(),
            portfolioUris = selectedPortfolioUris.map { it.toString() },
            address = officeAddress,
            latitude = latitude,
            longitude = longitude
        )

        // Geocoding (se o endereço não foi localizado pelo botão) e signUp fazem rede: rodam em IO.
        setRegisterLoading(true)
        lifecycleScope.launch {
            var userToRegister = newUser
            if (isProviderMode && (newUser.latitude == null || newUser.longitude == null)) {
                val coords = withContext(Dispatchers.IO) {
                    LocationUtils.getCoordinatesFromAddress(applicationContext, newUser.address.orEmpty())
                }
                if (coords == null) {
                    setRegisterLoading(false)
                    binding.tilLocation.error = "Endereço não encontrado. Inclua rua, número e cidade."
                    return@launch
                }
                userToRegister = newUser.copy(latitude = coords.latitude, longitude = coords.longitude)
            }
            val result = withContext(Dispatchers.IO) { userRepository.registerUser(userToRegister, password) }
            setRegisterLoading(false)
            handleRegisterResult(result, email)
        }
    }

    private fun setRegisterLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "Cadastrando..." else "Finalizar Cadastro"
    }

    private fun handleRegisterResult(result: Result<User>, email: String) {
        result.onSuccess { user ->
            UserSessionManager.saveUserSession(this, user.isProvider, user.name, user.email)

            val profileType = if (user.isProvider) "Prestador de Serviço" else "Contratante"
            Toast.makeText(this, "Cadastro de $profileType realizado com sucesso!", Toast.LENGTH_LONG).show()

            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_IS_PROVIDER, user.isProvider)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }.onFailure { exception ->
            if (exception is EmailConfirmationPendingException) {
                // Conta criada: volta ao login com e-mail e tipo de conta já preenchidos.
                Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
                startActivity(LoginActivity.newIntent(this, email, isProviderMode))
                finish()
                return@onFailure
            }
            binding.tilEmail.error = exception.message ?: "Erro ao cadastrar usuário"
            Toast.makeText(this, exception.message ?: "Falha no cadastro", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
        binding.tilCategory.error = null
        binding.tilSpecialty.error = null
        binding.tilLocation.error = null
    }

    // Adapter interno para exibição das fotos de portfólio selecionadas
    private inner class PortfolioPreviewAdapter(
        private val items: List<Uri>,
        private val onItemClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<PortfolioPreviewAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val imgPortfolio: ImageView = view.findViewById(R.id.imgPortfolio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_portfolio, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = items[position]
            holder.imgPortfolio.setImageURI(uri)
            holder.view.setOnClickListener { onItemClick(uri) }
        }

        override fun getItemCount(): Int = items.size
    }
}