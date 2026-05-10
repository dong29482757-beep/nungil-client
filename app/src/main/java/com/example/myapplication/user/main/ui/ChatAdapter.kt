package com.example.myapplication.user.main.ui

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.common.model.UserChatMessage
import com.example.myapplication.R

class ChatAdapter(
    private val chatList: MutableList<UserChatMessage>,
    private val listener: OnSuggestionClickListener
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    interface OnSuggestionClickListener {
        fun onSuggestionClick(text: String?)
    }

    override fun getItemViewType(position: Int): Int {
        return chatList[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        // ChatMessage.TYPE_MINE으로 직접 참조 (Companion 생략 가능)
        val layoutRes = if (viewType == UserChatMessage.TYPE_MINE) {
            R.layout.item_chat_mine
        } else {
            R.layout.item_chat_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = chatList[position]

        // [수정] apply 내부에서는 TextView.xxx 가 아니라 그냥 xxx()를 호출해야 합니다.
        holder.tvContent?.apply {
            if (!msg.content.isNullOrEmpty()) {
                text = msg.content      // TextView.setText 대신 text 속성 사용
                visibility = View.VISIBLE // View.setVisibility 대신 visibility 속성 사용
            } else {
                visibility = View.GONE
            }
        }

        holder.ivImage?.apply {
            if (msg.isImage && msg.imageBitmap != null) {
                visibility = View.VISIBLE
                setImageBitmap(msg.imageBitmap) // ImageView.setImageBitmap 아님
            } else {
                visibility = View.GONE
            }
        }

        holder.layoutSuggestions?.apply {
            removeAllViews() // ViewGroup.removeAllViews 아님
            msg.suggestions?.let { list ->
                if (list.isNotEmpty()) {
                    visibility = View.VISIBLE
                    for (suggestionText in list) {
                        // context를 가져올 때도 this.context 또는 context 사용
                        val chip = TextView(context).apply {
                            text = suggestionText
                            textSize = 14f
                            setTextColor(Color.parseColor("#1E88E5"))
                            setBackgroundResource(R.drawable.bg_suggestion_chip)
                            setPadding(30, 15, 30, 15)
                            gravity = Gravity.CENTER

                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 15, 15) }

                            setOnClickListener { listener.onSuggestionClick(suggestionText) }
                        }
                        addView(chip) // addView 바로 호출
                    }
                } else {
                    visibility = View.GONE
                }
            } ?: run { visibility = View.GONE }
        }
    }

    override fun getItemCount(): Int = chatList.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvContent: TextView? = itemView.findViewById(R.id.tvContent)
        val ivImage: ImageView? = itemView.findViewById(R.id.ivImage)
        val layoutSuggestions: LinearLayout? = itemView.findViewById(R.id.layoutSuggestions)
    }
}