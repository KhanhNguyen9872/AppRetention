package com.hchen.appretention.hook.system.opt;

import static com.hchen.appretention.data.path.SystemClass.ActivityManagerService;
import static com.hchen.appretention.data.path.SystemClass.ProcessList;
import static com.hchen.appretention.data.path.SystemClass.RecentTasks;
import static com.hchen.appretention.data.path.SystemClass.Task;
import static com.hchen.hooktool.core.CoreTool.callMethod;
import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.getField;
import static com.hchen.hooktool.core.CoreTool.hook;
import static com.hchen.hooktool.core.CoreTool.hookMethod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Binder;

import com.hchen.appretention.data.field.SystemField;
import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.HashSet;

/**
 * Manages Background Restriction (Chặn chạy ngầm / Blacklist):
 * 1. Default restricted behavior: Allowed in recents, but terminated and prevented from background
 *    running when cleared/swiped from Recents.
 * 2. Immediate Kill behavior (when persist.hchen.restrict.immediate_kill is enabled):
 *    Terminates the restricted app immediately when leaving foreground (Home pressed, app switched, etc.).
 * 3. Wakeup Suppression: Intercepts and suppresses background broadcast auto-restarts for restricted apps.
 *
 * @author Antigravity & HChenX
 */
public final class BackgroundRestrictOpt {
    private static final String TAG = "BackgroundRestrictOpt";

    public static final String PROP_RESTRICT_PACKAGES = "persist.hchen.restrict.packages";
    public static final String PROP_IMMEDIATE_KILL = "persist.hchen.restrict.immediate_kill";

    public static final String RESTRICT_FILE_PATH_DE = "/data/user_de/0/com.hchen.appretention/files/restricted_packages.txt";
    public static final String RESTRICT_FILE_PATH_CE = "/data/user/0/com.hchen.appretention/files/restricted_packages.txt";
    public static final String IMMEDIATE_KILL_FILE_PATH = "/data/user_de/0/com.hchen.appretention/files/immediate_kill.txt";
    public static final String PACKAGE_APPRETENTION = "com.hchen.appretention";

    public static boolean shouldTerminateOnTaskRemoved(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        return isRestricted(packageName) || PACKAGE_APPRETENTION.equals(packageName);
    }

    private static long lastCheckTime = 0;
    private static HashSet<String> cachedRestrictedSet = new HashSet<>();

    public static void init() {
        hookRecentTasksRemove();
        hookBroadcastWakeupSuppression();
        hookForegroundActivitiesChanged();
        hookActivityRecordLifecycle();
        hookActivityTaskManagerServiceActivityStopped();
        XposedLog.logI(TAG, "BackgroundRestrictOpt initialized successfully with all lifecycle hooks!");
    }

    public static synchronized HashSet<String> getRestrictedPackages() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime > 1500) {
            lastCheckTime = now;
            HashSet<String> set = new HashSet<>();

            // 1. Read from shared configuration files (no 91-char limit)
            File[] candidateFiles = new File[]{
                new File(RESTRICT_FILE_PATH_DE),
                new File(RESTRICT_FILE_PATH_CE)
            };
            for (File f : candidateFiles) {
                if (f.exists() && f.canRead()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) set.add(trimmed);
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 2. Read from system prop
            try {
                String prop = SystemPropTool.getProp(PROP_RESTRICT_PACKAGES, "");
                if (!prop.isEmpty()) {
                    for (String p : prop.split(",")) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) set.add(trimmed);
                    }
                }
            } catch (Throwable ignored) {}

            cachedRestrictedSet = set;
            if (!cachedRestrictedSet.isEmpty()) {
                XposedLog.logD(TAG, "Active restricted packages: " + cachedRestrictedSet);
            }
        }
        return cachedRestrictedSet;
    }

    public static boolean isRestricted(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        return getRestrictedPackages().contains(packageName);
    }

    public static boolean isImmediateKillEnabled() {
        if (SystemPropTool.getProp(PROP_IMMEDIATE_KILL, false)) return true;
        try {
            File f = new File(IMMEDIATE_KILL_FILE_PATH);
            if (f.exists() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String trimmed = line.trim();
                        return "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Hook Point 1: Swiping or removing tasks from Recents
     */
    private static void hookRecentTasksRemove() {
        Class<?> recentTasksClass = findClassIfExists(RecentTasks);
        if (recentTasksClass != null) {
            try {
                hookMethod(RecentTasks, "remove", Task, new IHook() {
                    @Override
                    public void after() {
                        Object taskObj = getArg(0);
                        if (taskObj == null) return;

                        String pkg = extractPackageNameFromTask(taskObj);
                        if (shouldTerminateOnTaskRemoved(pkg)) {
                            XposedLog.logI(TAG, "Task cleared from RecentTasks.remove: " + pkg);
                            terminatePackage(pkg, 0, "recents_cleared");
                        }
                    }
                });
                XposedLog.logD(TAG, "Hooked RecentTasks.remove(Task) successfully.");
            } catch (Throwable t) {
                XposedLog.logE(TAG, "Failed to hook RecentTasks.remove(Task)", t);
            }
        }

        Class<?> taskClass = findClassIfExists(Task);
        if (taskClass != null) {
            try {
                for (Method m : taskClass.getDeclaredMethods()) {
                    if (m.getName().startsWith("remove")) {
                        hook(m, new IHook() {
                            @Override
                            public void before() {
                                Object taskObj = getThisObject();
                                String pkg = extractPackageNameFromTask(taskObj);
                                if (shouldTerminateOnTaskRemoved(pkg)) {
                                    XposedLog.logI(TAG, "Task removed via Task." + m.getName() + ": " + pkg);
                                    terminatePackage(pkg, 0, "task_removed");
                                }
                            }
                        });
                    }
                }
                XposedLog.logD(TAG, "Hooked Task remove methods successfully.");
            } catch (Throwable t) {
                XposedLog.logE(TAG, "Failed to hook Task remove methods", t);
            }
        }

        Class<?> atmsClass = findClassIfExists("com.android.server.wm.ActivityTaskManagerService");
        if (atmsClass != null) {
            try {
                for (Method m : atmsClass.getDeclaredMethods()) {
                    if ("removeTask".equals(m.getName())) {
                        hook(m, new IHook() {
                            @Override
                            public void before() {
                                Object[] args = getArgs();
                                if (args != null && args.length >= 1 && args[0] instanceof Integer) {
                                    int taskId = (Integer) args[0];
                                    try {
                                        Object rwc = getField(getThisObject(), "mRootWindowContainer");
                                        if (rwc != null) {
                                            Object taskObj = null;
                                            for (Method atm : rwc.getClass().getMethods()) {
                                                if ("anyTaskForId".equals(atm.getName())) {
                                                    try {
                                                        if (atm.getParameterTypes().length == 1) {
                                                            taskObj = atm.invoke(rwc, taskId);
                                                        } else if (atm.getParameterTypes().length == 2) {
                                                            taskObj = atm.invoke(rwc, taskId, 0);
                                                        }
                                                        if (taskObj != null) break;
                                                    } catch (Throwable ignored) {}
                                                }
                                            }
                                            if (taskObj != null) {
                                                String pkg = extractPackageNameFromTask(taskObj);
                                                if (shouldTerminateOnTaskRemoved(pkg)) {
                                                    XposedLog.logI(TAG, "Task removed via ATMS.removeTask: " + pkg);
                                                    terminatePackage(pkg, 0, "atms_remove_task");
                                                }
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        });
                    }
                }
                XposedLog.logD(TAG, "Hooked ActivityTaskManagerService.removeTask successfully.");
            } catch (Throwable t) {
                XposedLog.logE(TAG, "Failed to hook ATMS.removeTask", t);
            }
        }
    }

    /**
     * Hook Point 2: AMS dispatchForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities)
     * Fires immediately when an app loses its foreground activity status (e.g. user presses Home).
     */
    private static void hookForegroundActivitiesChanged() {
        Class<?> amsClass = findClassIfExists(ActivityManagerService);
        if (amsClass == null) return;

        try {
            for (Method m : amsClass.getDeclaredMethods()) {
                if ("dispatchForegroundActivitiesChanged".equals(m.getName())) {
                    hook(m, new IHook() {
                        @Override
                        public void before() {
                            try {
                                Object[] args = getArgs();
                                if (args != null && args.length >= 3) {
                                    int pid = (Integer) args[0];
                                    int uid = (Integer) args[1];
                                    boolean foregroundActivities = (Boolean) args[2];
                                    if (!foregroundActivities && isImmediateKillEnabled()) {
                                        handleProcessNoForeground(pid, uid);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            }
            XposedLog.logD(TAG, "Hooked ActivityManagerService.dispatchForegroundActivitiesChanged successfully.");
        } catch (Throwable t) {
            XposedLog.logE(TAG, "Failed to hook dispatchForegroundActivitiesChanged", t);
        }
    }

    private static void handleProcessNoForeground(int pid, int uid) {
        HashSet<String> restricted = getRestrictedPackages();
        if (restricted.isEmpty()) return;

        // 1. Try to match by reading /proc/<pid>/cmdline
        String procName = null;
        try {
            File cmdline = new File("/proc/" + pid + "/cmdline");
            if (cmdline.exists()) {
                try (BufferedReader r = new BufferedReader(new FileReader(cmdline))) {
                    String line = r.readLine();
                    if (line != null) {
                        procName = line.trim().replace("\0", "");
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (procName != null) {
            for (String pkg : restricted) {
                if (procName.equals(pkg) || procName.startsWith(pkg + ":")) {
                    XposedLog.logI(TAG, "dispatchForegroundActivitiesChanged(false): terminating " + pkg + " (pid=" + pid + ")");
                    terminatePackage(pkg, pid, "fg_activities_false");
                    return;
                }
            }
        }

        // 2. Try match by UID
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method getPm = atClass.getMethod("getPackageManager");
            Object pm = getPm.invoke(null);
            if (pm != null) {
                Method getPkgs = pm.getClass().getMethod("getPackagesForUid", int.class);
                String[] pkgs = (String[]) getPkgs.invoke(pm, uid);
                if (pkgs != null) {
                    for (String p : pkgs) {
                        if (restricted.contains(p)) {
                            XposedLog.logI(TAG, "dispatchForegroundActivitiesChanged(false) by UID " + uid + ": terminating " + p + " (pid=" + pid + ")");
                            terminatePackage(p, pid, "fg_activities_false_uid");
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Hook Point 3: ActivityRecord.setState(ActivityState state, String reason)
     * Fires immediately when any Activity transitions to STOPPED or PAUSED.
     */
    private static void hookActivityRecordLifecycle() {
        Class<?> arClass = findClassIfExists("com.android.server.wm.ActivityRecord");
        if (arClass == null) return;

        try {
            for (Method m : arClass.getDeclaredMethods()) {
                if ("setState".equals(m.getName()) && m.getParameterTypes().length >= 1) {
                    hook(m, new IHook() {
                        @Override
                        public void after() {
                            if (!isImmediateKillEnabled()) return;
                            try {
                                Object stateObj = getArg(0);
                                if (stateObj == null) return;
                                String stateName = stateObj.toString();
                                if ("STOPPED".equals(stateName) || "PAUSED".equals(stateName)) {
                                    Object ar = getThisObject();
                                    String pkg = (String) getField(ar, "packageName");
                                    if (pkg != null && isRestricted(pkg)) {
                                        int pid = 0;
                                        try {
                                            Object wpc = getField(ar, "app");
                                            if (wpc != null) {
                                                Boolean hasResumed = (Boolean) callMethod(wpc, "hasResumedActivity");
                                                if (hasResumed != null && hasResumed) return; // Still active
                                                Object pidVal = callMethod(wpc, "getPid");
                                                if (pidVal instanceof Integer) pid = (Integer) pidVal;
                                            }
                                        } catch (Throwable ignored) {}

                                        XposedLog.logI(TAG, "ActivityRecord.setState(" + stateName + "): terminating " + pkg + " (pid=" + pid + ")");
                                        terminatePackage(pkg, pid, "activity_state_" + stateName.toLowerCase());
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            }
            XposedLog.logD(TAG, "Hooked ActivityRecord.setState successfully.");
        } catch (Throwable t) {
            XposedLog.logE(TAG, "Failed to hook ActivityRecord.setState", t);
        }
    }

    /**
     * Hook Point 4: ActivityTaskManagerService.activityStopped(IBinder token, ...)
     * Client tells ATMS that an activity has fully stopped.
     */
    private static void hookActivityTaskManagerServiceActivityStopped() {
        Class<?> atmsClass = findClassIfExists("com.android.server.wm.ActivityTaskManagerService");
        if (atmsClass == null) return;

        try {
            for (Method m : atmsClass.getDeclaredMethods()) {
                if ("activityStopped".equals(m.getName())) {
                    hook(m, new IHook() {
                        @Override
                        public void after() {
                            if (!isImmediateKillEnabled()) return;
                            Object token = getArg(0);
                            if (token == null) return;
                            try {
                                Class<?> arClass = findClassIfExists("com.android.server.wm.ActivityRecord");
                                if (arClass != null) {
                                    Method forToken = null;
                                    for (Method arm : arClass.getDeclaredMethods()) {
                                        if ("forTokenLocked".equals(arm.getName())) {
                                            forToken = arm;
                                            break;
                                        }
                                    }
                                    if (forToken != null) {
                                        Object ar = forToken.invoke(null, token);
                                        if (ar != null) {
                                            String pkg = (String) getField(ar, "packageName");
                                            if (pkg != null && isRestricted(pkg)) {
                                                int pid = 0;
                                                try {
                                                    Object wpc = getField(ar, "app");
                                                    if (wpc != null) {
                                                        Object pidVal = callMethod(wpc, "getPid");
                                                        if (pidVal instanceof Integer) pid = (Integer) pidVal;
                                                    }
                                                } catch (Throwable ignored) {}
                                                terminatePackage(pkg, pid, "atms_activity_stopped");
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            }
            XposedLog.logD(TAG, "Hooked ATMS.activityStopped successfully.");
        } catch (Throwable t) {
            XposedLog.logE(TAG, "Failed to hook ATMS.activityStopped", t);
        }
    }

    /**
     * Hook Point 5: Prevent background broadcast wakeups for restricted apps
     */
    private static void hookBroadcastWakeupSuppression() {
        Class<?> plClass = findClassIfExists(ProcessList);
        if (plClass == null) return;

        try {
            for (Method m : plClass.getDeclaredMethods()) {
                if ("startProcessLocked".equals(m.getName())) {
                    hook(m, new IHook() {
                        @Override
                        public void before() {
                            Object[] args = getArgs();
                            if (args == null) return;

                            String pkg = null;
                            boolean isBackgroundWakeup = false;

                            for (Object arg : args) {
                                if (arg == null) continue;
                                if (pkg == null) {
                                    if (arg.getClass().getName().endsWith("ProcessRecord")) {
                                        try {
                                            ApplicationInfo info = (ApplicationInfo) getField(arg, SystemField.info);
                                            if (info != null) pkg = info.packageName;
                                        } catch (Throwable ignored) {}
                                    } else if (arg instanceof ApplicationInfo) {
                                        pkg = ((ApplicationInfo) arg).packageName;
                                    }
                                }
                                if (arg.getClass().getName().endsWith("HostingRecord")) {
                                    try {
                                        Object typeObj = callMethod(arg, "getType");
                                        String type = typeObj != null ? typeObj.toString().toLowerCase() : "";
                                        if (type.contains("broadcast") || type.contains("backup")) {
                                            isBackgroundWakeup = true;
                                        }
                                    } catch (Throwable ignored) {}
                                } else if (arg instanceof String) {
                                    String str = ((String) arg).toLowerCase();
                                    if (str.equals("broadcast") || str.equals("backup")) {
                                        isBackgroundWakeup = true;
                                    }
                                }
                            }

                            if (pkg != null && isRestricted(pkg) && isBackgroundWakeup) {
                                XposedLog.logI(TAG, "Suppressed background broadcast auto-restart for restricted app: " + pkg);
                                returnNull();
                            }
                        }
                    });
                }
            }
            XposedLog.logD(TAG, "Hooked ProcessList.startProcessLocked for wakeup suppression.");
        } catch (Throwable t) {
            XposedLog.logE(TAG, "Failed to hook ProcessList.startProcessLocked", t);
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

    public static void terminatePackage(String packageName, int pid, String reason) {
        if (packageName == null || packageName.isEmpty()) return;
        XposedLog.logI(TAG, "Terminating restricted package [" + packageName + "], pid: " + pid + ", reason: " + reason);

        // 1. Instant SIGKILL to the process PID if known
        if (pid > 0) {
            try {
                android.os.Process.killProcess(pid);
                android.os.Process.sendSignal(pid, 9);
                XposedLog.logD(TAG, "Sent SIGKILL to pid: " + pid);
            } catch (Throwable t) {
                XposedLog.logW(TAG, "SIGKILL failed for pid " + pid + ": " + t.getMessage());
            }
        }

        // 2. Force-stop via ActivityManagerService with elevated identity
        long ident = Binder.clearCallingIdentity();
        try {
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            Method getService = amClass.getMethod("getService");
            Object amService = getService.invoke(null);
            if (amService != null) {
                Method forceStop = amService.getClass().getMethod("forceStopPackage", String.class, int.class);
                try {
                    forceStop.invoke(amService, packageName, 0); // user 0 (Primary)
                } catch (Throwable t1) {
                    XposedLog.logW(TAG, "forceStopPackage user 0: " + t1.getMessage());
                }
                try {
                    forceStop.invoke(amService, packageName, -1); // USER_ALL
                } catch (Throwable ignored) {}
                XposedLog.logD(TAG, "Successfully invoked forceStopPackage for " + packageName);
            }
        } catch (Throwable t) {
            XposedLog.logW(TAG, "Failed forceStopPackage via ActivityManager: " + t.getMessage());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        // 3. Fallback via runtime command (am force-stop and pkill -9)
        try {
            Runtime.getRuntime().exec(new String[]{"am", "force-stop", packageName});
            Runtime.getRuntime().exec(new String[]{"pkill", "-9", "-f", packageName});
        } catch (Throwable ignored) {
        }
    }
}
