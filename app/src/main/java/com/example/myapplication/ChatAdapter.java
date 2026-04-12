package com.example.myapplication;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> chatList;
    private OnSuggestionClickListener listener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(String text);
    }

    public ChatAdapter(List<ChatMessage> chatList, OnSuggestionClickListener listener) {
        this.chatList = chatList;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return chatList.get(position).getType();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (viewType == ChatMessage.TYPE_MINE) ? R.layout.item_chat_mine : R.layout.item_chat_other;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage msg = chatList.get(position);

        // [핵심 수정] 텍스트 갱신 보장
        if (holder.tvContent != null) {
            String content = msg.getContent();
            if (content != null && !content.isEmpty()) {
                holder.tvContent.setText(content);
                holder.tvContent.setVisibility(View.VISIBLE);
            } else {
                holder.tvContent.setVisibility(View.GONE);
            }
        }

        // 이미지 표시 로직
        if (holder.ivImage != null) {
            if (msg.isImage() && msg.getImageBitmap() != null) {
                holder.ivImage.setVisibility(View.VISIBLE);
                holder.ivImage.setImageBitmap(msg.getImageBitmap());
            } else {
                holder.ivImage.setVisibility(View.GONE);
            }
        }

        // 추천 질문(칩) 생성 로직
        if (holder.layoutSuggestions != null) {
            holder.layoutSuggestions.removeAllViews(); // 이전 뷰들 삭제
            List<String> suggestions = msg.getSuggestions();
            if (suggestions != null && !suggestions.isEmpty()) {
                holder.layoutSuggestions.setVisibility(View.VISIBLE);
                for (String text : suggestions) {
                    TextView chip = new TextView(holder.itemView.getContext());
                    chip.setText(text);
                    chip.setTextSize(14);
                    chip.setTextColor(Color.parseColor("#1E88E5"));
                    chip.setBackgroundResource(R.drawable.bg_suggestion_chip);
                    chip.setPadding(30, 15, 30, 15); // 패딩 직접 추가로 시인성 확보
                    chip.setGravity(Gravity.CENTER);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 0, 15, 15);
                    chip.setLayoutParams(params);

                    chip.setOnClickListener(v -> listener.onSuggestionClick(text));
                    holder.layoutSuggestions.addView(chip);
                }
            } else {
                holder.layoutSuggestions.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() { return chatList.size(); }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        ImageView ivImage;
        LinearLayout layoutSuggestions;

        ChatViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvContent);
            ivImage = itemView.findViewById(R.id.ivImage);
            layoutSuggestions = itemView.findViewById(R.id.layoutSuggestions);
        }
    }
}