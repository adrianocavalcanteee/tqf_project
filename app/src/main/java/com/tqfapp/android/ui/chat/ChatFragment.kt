package com.tqfapp.android.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tqfapp.android.R

class ChatFragment : Fragment() {

    private val allConversations = listOf(
        ConversationItem(
            name = "Carlos Mendes",
            specialty = "Eletricista Residencial",
            lastMessage = "Olá! Posso sim realizar a instalação no sábado às 09:00.",
            time = "10:42",
            unreadCount = 2,
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ConversationItem(
            name = "Rafael Lima",
            specialty = "Encanador / Bombeiro Hidráulico",
            lastMessage = "Orçamento aprovado. Estou a caminho!",
            time = "08:15",
            unreadCount = 1,
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ConversationItem(
            name = "Juliana Alves",
            specialty = "Diarista e Faxineira",
            lastMessage = "Obrigada! Até a próxima semana.",
            time = "Ontem",
            unreadCount = 0,
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        ),
        ConversationItem(
            name = "João Ferreira",
            specialty = "Técnico em Notebooks e Computadores",
            lastMessage = "Pronto! O sistema foi configurado e testado com sucesso.",
            time = "12 de mar.",
            unreadCount = 0,
            isVerified = true,
            avatarRes = R.drawable.ic_profile
        )
    )

    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var rvConversations: RecyclerView
    private lateinit var etSearchChat: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvConversations = view.findViewById(R.id.rvConversations)
        etSearchChat = view.findViewById(R.id.etSearchChat)

        setupConversationsList()
        setupSearch()
    }

    private fun setupConversationsList() {
        conversationAdapter = ConversationAdapter(allConversations) { conversation ->
            openChatDialog(conversation)
        }

        rvConversations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conversationAdapter
        }
    }

    private fun setupSearch() {
        etSearchChat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                val filtered = if (query.isEmpty()) {
                    allConversations
                } else {
                    allConversations.filter {
                        it.name.contains(query, ignoreCase = true) ||
                                it.specialty.contains(query, ignoreCase = true) ||
                                it.lastMessage.contains(query, ignoreCase = true)
                    }
                }
                conversationAdapter.updateList(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun openChatDialog(conversation: ConversationItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_chat_thread, null)
        val txtContactName = dialogView.findViewById<TextView>(R.id.txtChatContactName)
        val txtInitialMessage = dialogView.findViewById<TextView>(R.id.txtInitialMessage)
        val containerMessages = dialogView.findViewById<LinearLayout>(R.id.containerMessages)
        val scrollMessages = dialogView.findViewById<ScrollView>(R.id.scrollMessages)
        val etMessageInput = dialogView.findViewById<EditText>(R.id.etMessageInput)
        val btnSendMessage = dialogView.findViewById<View>(R.id.btnSendMessage)

        txtContactName?.text = conversation.name
        txtInitialMessage?.text = conversation.lastMessage

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()

        btnSendMessage?.setOnClickListener {
            val text = etMessageInput?.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                val sentBubble = TextView(requireContext()).apply {
                    this.text = text
                    this.setTextColor(requireContext().getColor(R.color.white))
                    this.textSize = 13f
                    this.setBackgroundResource(R.drawable.bg_chip)
                    this.backgroundTintList = requireContext().getColorStateList(R.color.brand_blue)
                    this.setPadding(28, 20, 28, 20)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END
                        topMargin = 12
                    }
                    this.layoutParams = params
                }

                containerMessages?.addView(sentBubble)
                etMessageInput?.setText("")
                scrollMessages?.post { scrollMessages.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    // Model for Conversation
    data class ConversationItem(
        val name: String,
        val specialty: String,
        val lastMessage: String,
        val time: String,
        val unreadCount: Int,
        val isVerified: Boolean,
        val avatarRes: Int
    )

    // Adapter for Conversations
    private inner class ConversationAdapter(
        private var items: List<ConversationItem>,
        private val onItemClick: (ConversationItem) -> Unit
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        fun updateList(newList: List<ConversationItem>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtName)
            val txtLastMessage: TextView = view.findViewById(R.id.txtLastMessage)
            val txtTime: TextView = view.findViewById(R.id.txtTime)
            val txtUnreadBadge: TextView = view.findViewById(R.id.txtUnreadBadge)
            val imgVerified: ImageView = view.findViewById(R.id.imgVerified)
            val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_conversation, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtName.text = item.name
            holder.txtLastMessage.text = item.lastMessage
            holder.txtTime.text = item.time
            holder.imgAvatar.setImageResource(item.avatarRes)
            holder.imgVerified.visibility = if (item.isVerified) View.VISIBLE else View.GONE

            if (item.unreadCount > 0) {
                holder.txtUnreadBadge.visibility = View.VISIBLE
                holder.txtUnreadBadge.text = item.unreadCount.toString()
            } else {
                holder.txtUnreadBadge.visibility = View.GONE
            }

            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}