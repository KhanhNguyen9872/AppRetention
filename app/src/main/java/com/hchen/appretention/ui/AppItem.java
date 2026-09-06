package com.hchen.appretention.ui;

import android.graphics.drawable.Drawable;

public class AppItem {
    public String appName;
    public String packageName;
    public Drawable icon;
    public boolean isVip;
    public boolean isRestricted;
    public boolean isSystemApp;

    public AppItem(String appName, String packageName, Drawable icon, boolean isVip, boolean isRestricted, boolean isSystemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isVip = isVip;
        this.isRestricted = isRestricted;
        this.isSystemApp = isSystemApp;
    }
}
