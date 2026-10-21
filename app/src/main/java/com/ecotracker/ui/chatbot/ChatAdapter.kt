package com.ecotracker.ui.chatbot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.ecotracker.R

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutBotMessage: LinearLayout = view.findViewById(R.id.layoutBotMessage)
        val tvBotText: TextView = view.findViewById(R.id.tvBotText)
        val layoutUserMessage: LinearLayout = view.findViewById(R.id.layoutUserMessage)
        val tvUserText: TextView = view.findViewById(R.id.tvUserText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.isUser) {
            holder.layoutUserMessage.visibility = View.VISIBLE
            holder.layoutBotMessage.visibility = View.GONE
            holder.tvUserText.text = msg.text
        } else {
            holder.layoutUserMessage.visibility = View.GONE
            holder.layoutBotMessage.visibility = View.VISIBLE
            holder.tvBotText.text = formatCleanChatText(msg.text)
        }
    }

    private fun formatCleanChatText(rawText: String): CharSequence {
        if (rawText.isBlank()) return ""

        // 1. Remove composite markdown/regex artifacts like #*, *#, etc.
        var text = rawText.replace("#*", "").replace("*#", "")

        // 2. Remove markdown header hashes at the beginning of lines (e.g. ### Heading -> Heading)
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")

        // 3. Convert markdown bullet lists (* or - at start of line) to clean bullet symbol
        text = text.replace(Regex("(?m)^\\s*[*\\-]\\s+"), "• ")

        // 4. Convert markdown bold **text** to <b>text</b>
        text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")

        // 5. Convert markdown italic *text* or _text_ to <i>text</i>
        text = text.replace(Regex("(?<![\\w*])\\*([^*]+?)\\*(?![\\w*])"), "<i>$1</i>")
        text = text.replace(Regex("(?<![\\w_])_([^_]+?)_(?![\\w_])"), "<i>$1</i>")

        // 6. Remove any leftover stray formatting symbols or raw asterisks/hashes
        text = text.replace(Regex("[#*`~]"), "")

        // 7. Convert newlines to HTML line breaks
        val htmlFormatted = text.replace("\n", "<br/>")

        return HtmlCompat.fromHtml(
            htmlFormatted,
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        val count = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, count)
    }
}
