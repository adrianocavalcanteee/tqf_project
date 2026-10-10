package com.tqfapp.android.ui.home

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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R

class ProfessionalProfileFragment : Fragment() {

    // Photo Picker launcher for Profile Avatar
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            view?.findViewById<ImageView>(R.id.imgAvatar)?.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de perfil atualizada!", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker launcher for Cover Image
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
        setupProfileMode(view, isOwner)

        setupClickListeners(view, isOwner)
        setupPortfolio(view)
        setupReviews(view)
    }

    private fun setupProfileMode(view: View, isOwner: Boolean) {
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)
        val btnChangeCover = view.findViewById<View>(R.id.btnChangeCover)
        val btnChangeAvatar = view.findViewById<View>(R.id.btnChangeAvatar)
        val layoutActionButtons = view.findViewById<View>(R.id.layoutActionButtons)

        if (isOwner) {
            // MODO PRESTADOR (Vendo seu próprio perfil)
            btnEditProfile?.visibility = View.VISIBLE
            btnChangeCover?.visibility = View.VISIBLE
            btnChangeAvatar?.visibility = View.VISIBLE
            layoutActionButtons?.visibility = View.GONE
        } else {
            // MODO CONTRATANTE (Cliente visualizando o prestador)
            layoutActionButtons?.visibility = View.VISIBLE
            btnEditProfile?.visibility = View.GONE
            btnChangeCover?.visibility = View.GONE
            btnChangeAvatar?.visibility = View.GONE
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

    private fun showEditProfileDialog(rootView: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_provider_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etSpecialty = dialogView.findViewById<TextInputEditText>(R.id.etSpecialty)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)

        val txtName = rootView.findViewById<TextView>(R.id.txtProfessionalName)
        val txtSpecialty = rootView.findViewById<TextView>(R.id.txtSpecialty)
        val txtBio = rootView.findViewById<TextView>(R.id.txtBio)

        etName?.setText(txtName?.text)
        etSpecialty?.setText(txtSpecialty?.text)
        etBio?.setText(txtBio?.text)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newSpecialty = etSpecialty?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()

                if (newName.isNotEmpty()) txtName?.text = newName
                if (newSpecialty.isNotEmpty()) txtSpecialty?.text = newSpecialty
                if (newBio.isNotEmpty()) txtBio?.text = newBio

                showToast("Perfil de prestador atualizado!")
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
        val reviews = listOf(
            ReviewItem(
                reviewerName = "Mariana Costa",
                date = "10 de mar. de 2025",
                comment = "Excelente profissional! Resolveu meu problema com muita agilidade. Recomendo!",
                avatarRes = R.drawable.ic_profile
            )
        )

        view.findViewById<RecyclerView>(R.id.rvReviews)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ReviewAdapter(reviews) {
                showToast("Avaliação de ${it.reviewerName}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_IS_OWNER = "arg_is_owner"

        fun newInstance(isOwner: Boolean = false): ProfessionalProfileFragment {
            val fragment = ProfessionalProfileFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_OWNER, isOwner)
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