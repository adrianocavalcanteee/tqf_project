package com.tqfapp.android.ui.contracts

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.business.ReviewRules
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServiceDetailsFragment : Fragment() {

    private val evidenceUris = mutableListOf<Uri>()
    private var evidenceAdapter: PortfolioPreviewAdapter? = null

    private val pickEvidenceLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (!evidenceUris.contains(uri)) {
                    evidenceUris.add(uri)
                }
            }
            evidenceAdapter?.notifyDataSetChanged()
            Toast.makeText(requireContext(), "${uris.size} foto(s) de evidência adicionada(s)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_service_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE).orEmpty()
        val profName = arguments?.getString(ARG_PROFESSIONAL).orEmpty()
        val specialty = arguments?.getString(ARG_SPECIALTY).orEmpty()
        val schedule = arguments?.getString(ARG_SCHEDULE).orEmpty()
        val price = arguments?.getString(ARG_PRICE).orEmpty()
        val status = arguments?.getString(ARG_STATUS).orEmpty()
        val description = arguments?.getString(ARG_DESC).orEmpty()

        view.findViewById<TextView>(R.id.txtDetailTitle)?.text = title
        view.findViewById<TextView>(R.id.txtDetailProfessionalName)?.text = profName
        view.findViewById<TextView>(R.id.txtDetailSpecialty)?.text = specialty
        view.findViewById<TextView>(R.id.txtDetailSchedule)?.text = schedule
        view.findViewById<TextView>(R.id.txtDetailPrice)?.text = price
        view.findViewById<TextView>(R.id.txtDetailStatus)?.text = status
        view.findViewById<TextView>(R.id.txtDetailDescription)?.text = description

        val layoutEvidenceSection = view.findViewById<View>(R.id.layoutEvidenceSection)
        val isProvider = UserSessionManager.isProviderUser(requireContext())
        if (isProvider) {
            layoutEvidenceSection?.visibility = View.VISIBLE
        } else {
            layoutEvidenceSection?.visibility = View.GONE
        }

        val btnAddEvidence = view.findViewById<View>(R.id.btnAddEvidence)
        val rvServiceEvidence = view.findViewById<RecyclerView>(R.id.rvServiceEvidence)

        evidenceAdapter = PortfolioPreviewAdapter(evidenceUris) { uriToRemove ->
            evidenceUris.remove(uriToRemove)
            evidenceAdapter?.notifyDataSetChanged()
        }
        rvServiceEvidence?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvServiceEvidence?.adapter = evidenceAdapter

        btnAddEvidence?.setOnClickListener {
            pickEvidenceLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.btnBackDetails)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val btnReviewService = view.findViewById<MaterialButton>(R.id.btnReviewService)
        val isCompleted = status.equals("Concluído", ignoreCase = true) || status.equals("Concluídos", ignoreCase = true)

        // Somente o Contratante avalia, e somente serviços CONCLUIDO.
        if (isCompleted && !isProvider) {
            btnReviewService?.visibility = View.VISIBLE
            btnReviewService?.text = "⭐ Avaliar Atendimento"
            btnReviewService?.setOnClickListener {
                showReviewDialog(title, profName)
            }
            // Uma avaliação por contratação: se já existe no Supabase, o botão fica desabilitado.
            val serviceId = arguments?.getString(ARG_SERVICE_ID).orEmpty()
            if (serviceId.isNotBlank()) {
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val reviewed = withContext(Dispatchers.IO) {
                        ReviewRepository(appContext).hasReviewedService(serviceId)
                    }
                    if (reviewed) markAsReviewed()
                }
            }
        } else {
            // Serviços em andamento: o contato é feito pelo prestador via WhatsApp e a conclusão
            // acontece na aba "Contratações" (AvaliarSolicitacaoActivity). Sem chat interno.
            btnReviewService?.visibility = View.GONE
        }
    }

    private fun markAsReviewed() {
        view?.findViewById<MaterialButton>(R.id.btnReviewService)?.apply {
            text = "✓ Serviço avaliado"
            isEnabled = false
        }
    }

    private fun showReviewDialog(title: String, targetName: String) {
        // Avaliação exige a solicitação real e o prestador real; não há IDs de fallback.
        val serviceId = arguments?.getString(ARG_SERVICE_ID).orEmpty()
        val providerId = arguments?.getString(ARG_PROVIDER_ID).orEmpty()
        if (serviceId.isBlank() || providerId.isBlank()) {
            Toast.makeText(requireContext(), "Não foi possível identificar este serviço para avaliação.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etComment = dialogView.findViewById<TextInputEditText>(R.id.etReviewComment)
        val txtSubtitle = dialogView.findViewById<TextView>(R.id.txtReviewDialogSubtitle)

        txtSubtitle?.text = "Como foi a sua experiência com $targetName no serviço '$title'?"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Enviar Avaliação") { _, _ ->
                val ratingValue = ratingBar?.rating?.toInt() ?: 0
                if (!ReviewRules.isValidRating(ratingValue)) {
                    Toast.makeText(requireContext(), "Escolha de 1 a 5 estrelas.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val commentText = etComment?.text?.toString()?.trim().orEmpty()

                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ReviewRepository(appContext).addReview(serviceId, providerId, ratingValue, commentText)
                    }
                    if (result.isSuccess) markAsReviewed()
                    val message = result.fold(
                        onSuccess = { "Avaliação registrada para $targetName!" },
                        onFailure = { it.message ?: "Não foi possível registrar a avaliação." }
                    )
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

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

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_PROFESSIONAL = "arg_professional"
        private const val ARG_SPECIALTY = "arg_specialty"
        private const val ARG_SCHEDULE = "arg_schedule"
        private const val ARG_PRICE = "arg_price"
        private const val ARG_STATUS = "arg_status"
        private const val ARG_DESC = "arg_desc"
        private const val ARG_SERVICE_ID = "arg_service_id"
        private const val ARG_PROVIDER_ID = "arg_provider_id"
        private const val ARG_CLIENT_ID = "arg_client_id"

        fun newInstance(
            title: String,
            professional: String,
            specialty: String,
            schedule: String,
            price: String,
            status: String,
            desc: String,
            serviceId: String = "",
            providerId: String = "",
            clientId: String = ""
        ): ServiceDetailsFragment {
            val fragment = ServiceDetailsFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_PROFESSIONAL, professional)
                putString(ARG_SPECIALTY, specialty)
                putString(ARG_SCHEDULE, schedule)
                putString(ARG_PRICE, price)
                putString(ARG_STATUS, status)
                putString(ARG_DESC, desc)
                putString(ARG_SERVICE_ID, serviceId)
                putString(ARG_PROVIDER_ID, providerId)
                putString(ARG_CLIENT_ID, clientId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
