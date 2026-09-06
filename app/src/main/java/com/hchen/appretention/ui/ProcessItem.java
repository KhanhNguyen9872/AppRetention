package com.hchen.appretention.ui;

import android.graphics.drawable.Drawable;

public class ProcessItem {
    public String appName;
    public String packageName;
    public int pid;
    public int adj;
    public Drawable icon;

    public ProcessItem(String appName, String packageName, int pid, int adj, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.pid = pid;
        this.adj = adj;
        this.icon = icon;
    }
}
