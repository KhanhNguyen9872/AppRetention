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

    public static final int MODE_KEEP_ALIVE = 0;
    public static final int MODE_RESTRICTED = 1;

    public interface OnAppStateChangeListener {
        void onAppStateChanged(AppItem item, int mode, boolean enabled, int position);
        void onSystemAppRestrictedRequested(AppItem item, int position);
        void onRestrictedItemClickedInKeepAlive(AppItem item);
    }

    private final List<AppItem> fullList = new ArrayList<>();
    private final List<AppItem> displayList = new ArrayList<>();
    private OnAppStateChangeListener listener;
    private String currentQuery = "";
    private boolean filterActiveOnly = false;
    private int currentMode = MODE_KEEP_ALIVE;

    public KeepAliveAdapter(List<AppItem> list) {
        if (list != null) {
            this.fullList.addAll(list);
            this.displayList.addAll(list);
        }
    }

    public void setOnAppStateChangeListener(OnAppStateChangeListener listener) {
        this.listener = listener;
    }

    public void setMode(int mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            applyFilter();
        }
    }

    public int getMode() {
        return currentMode;
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

    public void setFilterActiveOnly(boolean activeOnly) {
        this.filterActiveOnly = activeOnly;
        applyFilter();
    }

    public int getVipCount() {
        int count = 0;
        for (AppItem item : fullList) {
            if (item.isVip) count++;
        }
        return count;
    }

    public int getRestrictedCount() {
        int count = 0;
        for (AppItem item : fullList) {
            if (item.isRestricted) count++;
        }
        return count;
    }

    public int getActiveCount() {
        return currentMode == MODE_KEEP_ALIVE ? getVipCount() : getRestrictedCount();
    }

    public int getTotalCount() {
        return fullList.size();
    }

    public void applyFilter() {
        displayList.clear();
        for (AppItem item : fullList) {
            if (filterActiveOnly) {
                if (currentMode == MODE_KEEP_ALIVE && !item.isVip) continue;
                if (currentMode == MODE_RESTRICTED && !item.isRestricted) continue;
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

        if (currentMode == MODE_KEEP_ALIVE) {
            if (item.isRestricted) {
                // Greyed out and restricted
                holder.itemView.setAlpha(0.4f);
                holder.switchKeepAlive.setEnabled(false);
                holder.switchKeepAlive.setChecked(false);
                holder.tvBadgeLocked.setVisibility(View.GONE);
                holder.tvBadgeRestricted.setVisibility(View.VISIBLE);
                holder.tvBadgeRestricted.setText(R.string.badge_restricted_short);
            } else {
                holder.itemView.setAlpha(1.0f);
                holder.switchKeepAlive.setEnabled(true);
                holder.switchKeepAlive.setChecked(item.isVip);
                holder.tvBadgeLocked.setVisibility(item.isVip ? View.VISIBLE : View.GONE);
                holder.tvBadgeRestricted.setVisibility(View.GONE);
            }
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.switchKeepAlive.setEnabled(true);
            holder.tvBadgeLocked.setVisibility(View.GONE);
            holder.tvBadgeRestricted.setVisibility(item.isRestricted ? View.VISIBLE : View.GONE);
            holder.tvBadgeRestricted.setText(R.string.badge_restricted);
            holder.switchKeepAlive.setChecked(item.isRestricted);
        }

        View.OnClickListener toggleAction = v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < displayList.size()) {
                AppItem currentItem = displayList.get(pos);
                if (currentMode == MODE_KEEP_ALIVE) {
                    if (currentItem.isRestricted) {
                        if (listener != null) {
                            listener.onRestrictedItemClickedInKeepAlive(currentItem);
                        }
                        return;
                    }
                    boolean newState = !currentItem.isVip;
                    currentItem.isVip = newState;
                    notifyItemChanged(pos);
                    if (listener != null) {
                        listener.onAppStateChanged(currentItem, MODE_KEEP_ALIVE, newState, pos);
                    }
                } else {
                    boolean newState = !currentItem.isRestricted;
                    if (newState && currentItem.isSystemApp) {
                        if (listener != null) {
                            listener.onSystemAppRestrictedRequested(currentItem, pos);
                        }
                        return;
                    }
                    currentItem.isRestricted = newState;
                    if (newState) currentItem.isVip = false;
                    notifyItemChanged(pos);
                    if (listener != null) {
                        listener.onAppStateChanged(currentItem, MODE_RESTRICTED, newState, pos);
                    }
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
        TextView tvName, tvPackage, tvBadgeLocked, tvBadgeRestricted;
        MaterialSwitch switchKeepAlive;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvName = itemView.findViewById(R.id.tvAppName);
            tvPackage = itemView.findViewById(R.id.tvAppPackage);
            tvBadgeLocked = itemView.findViewById(R.id.tvBadgeLocked);
            tvBadgeRestricted = itemView.findViewById(R.id.tvBadgeRestricted);
            switchKeepAlive = itemView.findViewById(R.id.switchKeepAlive);
        }
    }
}
