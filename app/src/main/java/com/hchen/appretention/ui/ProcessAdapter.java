package com.hchen.appretention.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.hchen.appretention.R;

import java.util.ArrayList;
import java.util.List;

public class ProcessAdapter extends RecyclerView.Adapter<ProcessAdapter.ViewHolder> {

    public interface OnProcessKillListener {
        void onKill(ProcessItem item, int position);
    }

    private final List<ProcessItem> processList = new ArrayList<>();
    private OnProcessKillListener killListener;

    public ProcessAdapter(List<ProcessItem> initialList) {
        if (initialList != null) {
            this.processList.addAll(initialList);
        }
    }

    public void setOnProcessKillListener(OnProcessKillListener listener) {
        this.killListener = listener;
    }

    public void removeItem(int position) {
        if (position >= 0 && position < processList.size()) {
            processList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, processList.size() - position);
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
                return oldItem.adj == newItem.adj &&
                        oldItem.packageName.equals(newItem.packageName) &&
                        oldItem.getFormattedMemory().equals(newItem.getFormattedMemory());
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
        holder.tvPackage.setText(item.packageName);
        holder.tvPid.setText(holder.itemView.getContext().getString(R.string.format_process_pid, item.pid, item.getFormattedMemory()));

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

        holder.btnKill.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && killListener != null && pos < processList.size()) {
                killListener.onKill(processList.get(pos), pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return processList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvPackage, tvPid, tvBadge;
        MaterialButton btnKill;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivProcIcon);
            tvName = itemView.findViewById(R.id.tvProcName);
            tvPackage = itemView.findViewById(R.id.tvProcPackage);
            tvPid = itemView.findViewById(R.id.tvProcPid);
            tvBadge = itemView.findViewById(R.id.tvAdjBadge);
            btnKill = itemView.findViewById(R.id.btnKillProc);
        }
    }
}
