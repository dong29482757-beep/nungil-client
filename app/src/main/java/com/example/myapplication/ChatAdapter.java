package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<ChatMessage> list;
    public ChatAdapter(List<ChatMessage> list) { this.list = list; }

    @Override
    public int getItemViewType(int p) { return list.get(p).getType(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
        View v = LayoutInflater.from(p.getContext()).inflate(vt == ChatMessage.TYPE_MINE ? R.layout.item_chat_mine : R.layout.item_chat_other, p, false);
        return new ChatVH(v, vt);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
        ChatMessage m = list.get(p);
        ChatVH vh = (ChatVH) h;
        if (m.isImg()) {
            vh.tv.setVisibility(View.GONE); vh.iv.setVisibility(View.VISIBLE); vh.iv.setImageBitmap(m.getImage());
        } else {
            vh.tv.setVisibility(View.VISIBLE); vh.iv.setVisibility(View.GONE); vh.tv.setText(m.getContent());
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ChatVH extends RecyclerView.ViewHolder {
        TextView tv; ImageView iv;
        ChatVH(View v, int vt) {
            super(v);
            tv = v.findViewById(vt == ChatMessage.TYPE_MINE ? R.id.tvMessageMine : R.id.tvMessageOther);
            iv = v.findViewById(vt == ChatMessage.TYPE_MINE ? R.id.ivMessageMine : R.id.ivMessageOther);
        }
    }
}