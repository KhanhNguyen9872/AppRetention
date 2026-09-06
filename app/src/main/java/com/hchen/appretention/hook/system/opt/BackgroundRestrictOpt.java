package com.hchen.appretention.hook.system.opt;

import static com.hchen.appretention.data.path.SystemClass.RecentTasks;
import static com.hchen.appretention.data.path.SystemClass.Task;
import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.getField;
import static com.hchen.hooktool.core.CoreTool.callMethod;
import static com.hchen.hooktool.core.CoreTool.hookMethod;

import android.content.ComponentName;
import android.content.Intent;

import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;
import java.util.HashSet;

/**
 * Manages Background Restriction (Chặn chạy ngầm / Blacklist):
 * 1. Default restricted behavior: Allowed in recents, but terminated and prevented from background
 *    running when cleared/swiped from Recents.
 * 2. Immediate Kill behavior (when persist.hchen.restrict.immediate_kill is enabled):
 *    Terminates the restricted app immediately when leaving foreground.
 *
 * @author Antigravity & HChenX
 */
public final class BackgroundRestrictOpt {
    private static final String TAG = "BackgroundRestrictOpt";

    public static final String PROP_RESTRICT_PACKAGES = "persist.hchen.restrict.packages";
    public static final String PROP_IMMEDIATE_KILL = "persist.hchen.restrict.immediate_kill";

    private static long lastCheckTime = 0;
    private static HashSet<String> cachedRestrictedSet = new HashSet<>();

    public static void init() {
        hookRecentTasksRemove();
        XposedLog.logI(TAG, "BackgroundRestrictOpt initialized successfully!");
    }

    public static synchronized HashSet<String> getRestrictedPackages() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime > 3000) {
            lastCheckTime = now;
            HashSet<String> set = new HashSet<>();
            try {
                String prop = SystemPropTool.getProp(PROP_RESTRICT_PACKAGES, "");
                if (!prop.isEmpty()) {
                    for (String p : prop.split(",")) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) set.add(trimmed);
                    }
                }
                cachedRestrictedSet = set;
            } catch (Throwable ignored) {
            }
        }
        return cachedRestrictedSet;
    }

    public static boolean isRestricted(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        return getRestrictedPackages().contains(packageName);
    }

    public static boolean isImmediateKillEnabled() {
        return SystemPropTool.getProp(PROP_IMMEDIATE_KILL, false);
    }

    private static void hookRecentTasksRemove() {
        Class<?> recentTasksClass = findClassIfExists(RecentTasks);
        if (recentTasksClass == null) {
            XposedLog.logW(TAG, "RecentTasks class not found, cannot hook remove!");
            return;
        }

        // Hook remove(Task task)
        try {
            hookMethod(RecentTasks, "remove", Task, new IHook() {
                @Override
                public void after() {
                    Object taskObj = getArg(0);
                    if (taskObj == null) return;

                    String pkg = extractPackageNameFromTask(taskObj);
                    if (pkg != null && isRestricted(pkg)) {
                        XposedLog.logI(TAG, "Restricted task cleared from Recents: " + pkg);
                        terminatePackage(pkg, "recents_cleared");
                    }
                }
            });
            XposedLog.logD(TAG, "Hooked RecentTasks.remove(Task) successfully.");
        } catch (Throwable t) {
            XposedLog.logE(TAG, "Failed to hook RecentTasks.remove(Task)", t);
        }
    }

    public static String extractPackageNameFromTask(Object task) {
        if (task == null) return null;
        try {
            Object realActivity = getField(task, "realActivity");
            if (realActivity instanceof ComponentName) {
                return ((ComponentName) realActivity).getPackageName();
            }
            Object origActivity = getField(task, "origActivity");
            if (origActivity instanceof ComponentName) {
                return ((ComponentName) origActivity).getPackageName();
            }
            Object baseIntent = getField(task, "intent");
            if (baseIntent == null) {
                baseIntent = callMethod(task, "getBaseIntent");
            }
            if (baseIntent instanceof Intent) {
                Intent in = (Intent) baseIntent;
                if (in.getComponent() != null) return in.getComponent().getPackageName();
                if (in.getPackage() != null) return in.getPackage();
            }
            Object affinity = getField(task, "affinity");
            if (affinity instanceof String && !((String) affinity).isEmpty()) {
                return (String) affinity;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void terminatePackage(String packageName, String reason) {
        if (packageName == null || packageName.isEmpty()) return;
        XposedLog.logI(TAG, "Terminating restricted package [" + packageName + "], reason: " + reason);

        // 1. Use system_server ActivityManagerService forceStopPackage API
        try {
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            Method getService = amClass.getMethod("getService");
            Object amService = getService.invoke(null);
            if (amService != null) {
                Method forceStop = amService.getClass().getMethod("forceStopPackage", String.class, int.class);
                forceStop.invoke(amService, packageName, -1); // -1 = USER_ALL
                XposedLog.logD(TAG, "Successfully invoked forceStopPackage for " + packageName);
                return;
            }
        } catch (Throwable t) {
            XposedLog.logW(TAG, "Failed forceStopPackage via ActivityManager: " + t.getMessage());
        }

        // 2. Fallback via runtime command
        try {
            Runtime.getRuntime().exec(new String[]{"am", "force-stop", packageName});
        } catch (Throwable ignored) {
        }
    }
}
