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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProcessAdapter extends RecyclerView.Adapter<ProcessAdapter.ViewHolder> {

    public interface OnProcessClickListener {
        void onProcessClick(ProcessItem item, int position);
    }

    private final List<ProcessItem> processList = new ArrayList<>();
    private final Set<String> vipPackages = new HashSet<>();
    private final Set<String> restrictedPackages = new HashSet<>();
    private OnProcessClickListener clickListener;

    public ProcessAdapter(List<ProcessItem> initialList) {
        if (initialList != null) {
            this.processList.addAll(initialList);
        }
    }

    public void setOnProcessClickListener(OnProcessClickListener listener) {
        this.clickListener = listener;
    }

    public void setPolicyPackages(Set<String> vips, Set<String> restricted) {
        vipPackages.clear();
        if (vips != null) vipPackages.addAll(vips);
        restrictedPackages.clear();
        if (restricted != null) restrictedPackages.addAll(restricted);
        notifyDataSetChanged();
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

        String basePkg = item.getBasePackageName();
        boolean isVip = vipPackages.contains(basePkg);
        boolean isRestricted = restrictedPackages.contains(basePkg);

        if (isVip) {
            holder.tvBadge.setText("ADJ " + item.adj + " • " + holder.itemView.getContext().getString(R.string.badge_proc_keep_alive));
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
            holder.tvBadge.setTextColor(0xFF22C55E);
        } else if (isRestricted) {
            holder.tvBadge.setText("ADJ " + item.adj + " • " + holder.itemView.getContext().getString(R.string.badge_proc_restricted));
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
            holder.tvBadge.setTextColor(0xFFEF4444);
        } else if (item.adj <= 249) {
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

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && clickListener != null && pos < processList.size()) {
                clickListener.onProcessClick(processList.get(pos), pos);
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

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivProcIcon);
            tvName = itemView.findViewById(R.id.tvProcName);
            tvPackage = itemView.findViewById(R.id.tvProcPackage);
            tvPid = itemView.findViewById(R.id.tvProcPid);
            tvBadge = itemView.findViewById(R.id.tvAdjBadge);
        }
    }
}
