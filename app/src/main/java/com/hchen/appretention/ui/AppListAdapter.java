package com.hchen.appretention.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hchen.appretention.R;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    private final List<AppItem> fullList;
    private final List<AppItem> displayList;

    public AppListAdapter(List<AppItem> list) {
        this.fullList = list;
        this.displayList = new ArrayList<>(list);
    }

    public void filter(String query) {
        displayList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayList.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();
            for (AppItem item : fullList) {
                if (item.appName.toLowerCase().contains(lower) || item.packageName.toLowerCase().contains(lower)) {
                    displayList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
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
        holder.cbVip.setOnCheckedChangeListener(null);
        holder.cbVip.setChecked(item.isVip);
        holder.cbVip.setOnCheckedChangeListener((buttonView, isChecked) -> item.isVip = isChecked);
        holder.itemView.setOnClickListener(v -> holder.cbVip.toggle());
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvPackage;
        CheckBox cbVip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvName = itemView.findViewById(R.id.tvAppName);
            tvPackage = itemView.findViewById(R.id.tvAppPackage);
            cbVip = itemView.findViewById(R.id.cbVip);
        }
    }
}
