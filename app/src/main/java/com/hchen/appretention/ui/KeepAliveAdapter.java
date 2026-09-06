package com.hchen.appretention.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.hchen.appretention.R;

import java.util.ArrayList;
import java.util.List;

public class KeepAliveAdapter extends RecyclerView.Adapter<KeepAliveAdapter.ViewHolder> {

    public interface OnKeepAliveChangeListener {
        void onKeepAliveChanged(AppItem item, boolean isPinned, int position);
    }

    private final List<AppItem> fullList = new ArrayList<>();
    private final List<AppItem> displayList = new ArrayList<>();
    private OnKeepAliveChangeListener listener;
    private String currentQuery = "";
    private boolean filterKeepAliveOnly = false;

    public KeepAliveAdapter(List<AppItem> list) {
        if (list != null) {
            this.fullList.addAll(list);
            this.displayList.addAll(list);
        }
    }

    public void setOnKeepAliveChangeListener(OnKeepAliveChangeListener listener) {
        this.listener = listener;
    }

    public void setAllApps(List<AppItem> list) {
        this.fullList.clear();
        if (list != null) {
            this.fullList.addAll(list);
        }
        applyFilter();
    }

    public void setQuery(String query) {
        this.currentQuery = query != null ? query.trim().toLowerCase() : "";
        applyFilter();
    }

    public void setFilterKeepAliveOnly(boolean keepAliveOnly) {
        this.filterKeepAliveOnly = keepAliveOnly;
        applyFilter();
    }

    public int getVipCount() {
        int count = 0;
        for (AppItem item : fullList) {
            if (item.isVip) count++;
        }
        return count;
    }

    public int getTotalCount() {
        return fullList.size();
    }

    public void applyFilter() {
        displayList.clear();
        for (AppItem item : fullList) {
            if (filterKeepAliveOnly && !item.isVip) {
                continue;
            }
            if (!currentQuery.isEmpty()) {
                boolean matchName = item.appName != null && item.appName.toLowerCase().contains(currentQuery);
                boolean matchPkg = item.packageName != null && item.packageName.toLowerCase().contains(currentQuery);
                if (!matchName && !matchPkg) {
                    continue;
                }
            }
            displayList.add(item);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_keep_alive_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem item = displayList.get(position);
        holder.tvName.setText(item.appName);
        holder.tvPackage.setText(item.packageName);
        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon);
        }

        holder.tvBadgeLocked.setVisibility(item.isVip ? View.VISIBLE : View.GONE);
        holder.switchKeepAlive.setChecked(item.isVip);

        View.OnClickListener toggleAction = v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < displayList.size()) {
                AppItem currentItem = displayList.get(pos);
                boolean newState = !currentItem.isVip;
                currentItem.isVip = newState;
                holder.switchKeepAlive.setChecked(newState);
                holder.tvBadgeLocked.setVisibility(newState ? View.VISIBLE : View.GONE);
                if (listener != null) {
                    listener.onKeepAliveChanged(currentItem, newState, pos);
                }
            }
        };

        holder.itemView.setOnClickListener(toggleAction);
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvPackage, tvBadgeLocked;
        MaterialSwitch switchKeepAlive;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvName = itemView.findViewById(R.id.tvAppName);
            tvPackage = itemView.findViewById(R.id.tvAppPackage);
            tvBadgeLocked = itemView.findViewById(R.id.tvBadgeLocked);
            switchKeepAlive = itemView.findViewById(R.id.switchKeepAlive);
        }
    }
}
