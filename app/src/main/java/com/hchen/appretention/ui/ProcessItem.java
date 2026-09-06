package com.hchen.appretention.ui;

import android.graphics.drawable.Drawable;

import java.util.Locale;

public class ProcessItem {
    public String appName;
    public String packageName;
    public int pid;
    public int adj;
    public Drawable icon;
    public long memoryBytes;

    public ProcessItem(String appName, String packageName, int pid, int adj, Drawable icon, long memoryBytes) {
        this.appName = appName;
        this.packageName = packageName;
        this.pid = pid;
        this.adj = adj;
        this.icon = icon;
        this.memoryBytes = memoryBytes;
    }

    public String getFormattedMemory() {
        if (memoryBytes <= 0) {
            return "0 KB";
        }
        double kb = memoryBytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.US, "%d KB", (int) Math.round(kb));
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }
}
