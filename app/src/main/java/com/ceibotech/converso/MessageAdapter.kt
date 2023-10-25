package com.ceibotech.converso

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<MessageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.messageTextView.text = message.content
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].role) {
            "user" -> R.layout.user_message
            "assistant" -> R.layout.assistant_message
            else -> throw IllegalArgumentException("Invalid role")
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }
}
