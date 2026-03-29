package com.example.myapplication;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private List<User> displayList; // 화면에 보여줄 리스트
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(User user);
    }

    public UserAdapter(List<User> list, OnItemClickListener listener) {
        // 처음에 빈 리스트를 보여주려면 new ArrayList<>() 사용
        this.displayList = new ArrayList<>(list);
        this.listener = listener;
    }

    // ⭐ 데이터를 외부에서 주입하고 화면을 갱신하는 핵심 메서드
    public void updateList(List<User> newList) {
        this.displayList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = displayList.get(position);
        holder.text1.setText("성명: " + user.getName());
        holder.text1.setTextColor(Color.BLACK);
        holder.text2.setText("ID: " + user.getId());
        holder.text2.setTextColor(Color.parseColor("#1976D2"));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(user));
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        ViewHolder(View v) {
            super(v);
            text1 = v.findViewById(android.R.id.text1);
            text2 = v.findViewById(android.R.id.text2);
        }
    }
}