package com.hchen.appretention.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.hchen.appretention.R;

import java.util.ArrayList;
import java.util.List;

public class ProcessAdapter extends RecyclerView.Adapter<ProcessAdapter.ViewHolder> {
    private final List<ProcessItem> processList = new ArrayList<>();

    public ProcessAdapter(List<ProcessItem> initialList) {
        if (initialList != null) {
            this.processList.addAll(initialList);
        }
    }

    public void updateList(List<ProcessItem> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return processList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return processList.get(oldPos).pid == newList.get(newPos).pid;
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                ProcessItem oldItem = processList.get(oldPos);
                ProcessItem newItem = newList.get(newPos);
                return oldItem.adj == newItem.adj && oldItem.packageName.equals(newItem.packageName);
            }
        });

        processList.clear();
        processList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_process, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProcessItem item = processList.get(position);
        holder.tvName.setText(item.appName);
        holder.tvDetail.setText(item.packageName + " • PID " + item.pid);
        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon);
        }

        if (item.adj <= 249) {
            holder.tvBadge.setText("ADJ " + item.adj + " [PERCEPTIBLE]");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
            holder.tvBadge.setTextColor(0xFF22C55E);
        } else if (item.adj <= 499) {
            holder.tvBadge.setText("ADJ " + item.adj + " [BACKUP]");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            holder.tvBadge.setTextColor(0xFF38BDF8);
        } else {
            holder.tvBadge.setText("ADJ " + item.adj + " [SERVICE]");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            holder.tvBadge.setTextColor(0xFFF59E0B);
        }
    }

    @Override
    public int getItemCount() {
        return processList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvDetail, tvBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivProcIcon);
            tvName = itemView.findViewById(R.id.tvProcName);
            tvDetail = itemView.findViewById(R.id.tvProcDetail);
            tvBadge = itemView.findViewById(R.id.tvAdjBadge);
        }
    }
}
