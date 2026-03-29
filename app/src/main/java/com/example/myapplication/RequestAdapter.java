package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {

    private List<User> requestList;
    private OnRequestClickListener listener;

    // 클릭 이벤트를 처리하기 위한 인터페이스
    public interface OnRequestClickListener {
        void onAcceptClick(User user, int position);
        void onRejectClick(User user, int position);
    }

    public RequestAdapter(List<User> requestList, OnRequestClickListener listener) {
        this.requestList = requestList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.caregiver_link_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = requestList.get(position);
        holder.tvName.setText(user.getName());
        holder.tvId.setText("ID: " + user.getId());

        holder.btnAccept.setOnClickListener(v -> listener.onAcceptClick(user, position));
        holder.btnReject.setOnClickListener(v -> listener.onRejectClick(user, position));
    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId;
        Button btnAccept, btnReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRequesterName);
            tvId = itemView.findViewById(R.id.tvRequesterId);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}