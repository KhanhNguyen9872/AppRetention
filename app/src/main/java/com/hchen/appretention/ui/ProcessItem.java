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
    public boolean isSystemApp;
    public int uid;
    public String versionName;

    public ProcessItem(String appName, String packageName, int pid, int adj, Drawable icon, long memoryBytes) {
        this(appName, packageName, pid, adj, icon, memoryBytes, false, 0, "");
    }

    public ProcessItem(String appName, String packageName, int pid, int adj, Drawable icon, long memoryBytes,
                       boolean isSystemApp, int uid, String versionName) {
        this.appName = appName;
        this.packageName = packageName;
        this.pid = pid;
        this.adj = adj;
        this.icon = icon;
        this.memoryBytes = memoryBytes;
        this.isSystemApp = isSystemApp;
        this.uid = uid;
        this.versionName = (versionName != null) ? versionName : "";
    }

    public String getBasePackageName() {
        if (packageName != null && packageName.contains(":")) {
            return packageName.substring(0, packageName.indexOf(':'));
        }
        return packageName != null ? packageName : "";
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
