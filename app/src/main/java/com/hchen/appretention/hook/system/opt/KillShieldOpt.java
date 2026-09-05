package com.hchen.appretention.hook.system.opt;

import static com.hchen.appretention.data.path.SystemClass.ActivityManagerService;
import static com.hchen.appretention.data.path.SystemClass.ProcessList;
import static com.hchen.appretention.data.path.SystemClass.ProcessRecord;
import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.findMethodIfExists;
import static com.hchen.hooktool.core.CoreTool.getField;
import static com.hchen.hooktool.core.CoreTool.hook;

import android.content.pm.ApplicationInfo;
import com.hchen.appretention.data.field.SystemField;
import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Intercepts and shields user background processes from automated system kills
 * such as broadcast delivery timeout, cached idle sweeps, excessive wake locks/cpu, etc.
 *
 * Explicit user actions (swiping from Recents, Force Stop) and real crashes/ANRs
 * are never blocked.
 *
 * @author Antigravity
 */
public final class KillShieldOpt {
    private static final String TAG = "KillShieldOpt";

    public static void init() {
        if (!isEnabled()) {
            XposedLog.logD(TAG, "KillShield is disabled by property.");
            return;
        }

        hookProcessRecordKill();
        hookProcessListKill();
        hookBroadcastQueueTimeout();

        XposedLog.logI(TAG, "KillShield initialized successfully!");
    }

    private static boolean isEnabled() {
        return SystemPropTool.getProp("persist.hchen.killshield.enable", true);
    }

    private static boolean isAutomatedKillReason(String reason) {
        if (reason == null) return false;
        String r = reason.toLowerCase(Locale.ROOT);

        // Explicit user-driven actions or fatal states - NEVER block these
        if (r.contains("remove task") ||
            r.contains("force stop") ||
            r.contains("user request") ||
            r.contains("user-requested") ||
            r.contains("crash") ||
            r.contains("anr") ||
            r.contains("stopped")) {
            return false;
        }

        // Automated background trims / timeout kills - BLOCK these for user apps
        return r.contains("broadcast delivery timeout") ||
               r.contains("delivery timeout") ||
               r.contains("cached idle") ||
               r.contains("excessive cpu") ||
               r.contains("excessive wake lock") ||
               r.contains("excessive power") ||
               r.contains("kill background") ||
               r.contains("empty") ||
               r.contains("trim") ||
               r.contains("imperceptible") ||
               r.contains("bg restricted") ||
               r.contains("bg-restricted") ||
               r.contains("low memory") ||
               r.contains("auto clean");
    }

    private static boolean isUserApp(Object appRecord) {
        if (appRecord == null) return false;
        try {
            ApplicationInfo info = (ApplicationInfo) getField(appRecord, SystemField.info);
            if (info != null) {
                return (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            }
            Integer uid = (Integer) getField(appRecord, "uid");
            return uid != null && uid >= 10000;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void hookProcessRecordKill() {
        Class<?> prClass = findClassIfExists(ProcessRecord);
        if (prClass == null) return;

        Method[] methods = prClass.getDeclaredMethods();
        for (Method method : methods) {
            if ("killLocked".equals(method.getName())) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length >= 1 && params[0] == String.class) {
                    hook(method, new IHook() {
                        @Override
                        public void before() {
                            Object reasonObj = getArg(0);
                            String reason = reasonObj != null ? reasonObj.toString() : "";
                            Object thisProcess = thisObject();

                            if (isUserApp(thisProcess) && isAutomatedKillReason(reason)) {
                                Object procName = getField(thisProcess, "processName");
                                XposedLog.logI(TAG, "Blocked automated killLocked: [" + procName + "], reason: " + reason);
                                returnNull();
                            }
                        }
                    });
                }
            }
        }
    }

    private static void hookProcessListKill() {
        Method killProcessQuietMethod = findMethodIfExists(ProcessList, "killProcessQuiet", int.class, String.class);
        if (killProcessQuietMethod != null) {
            hook(killProcessQuietMethod, new IHook() {
                @Override
                public void before() {
                    Object reasonObj = getArg(1);
                    String reason = reasonObj != null ? reasonObj.toString() : "";
                    if (isAutomatedKillReason(reason)) {
                        int pid = (int) getArg(0);
                        XposedLog.logI(TAG, "Blocked killProcessQuiet pid=" + pid + ", reason: " + reason);
                        returnNull();
                    }
                }
            });
        }
    }

    private static void hookBroadcastQueueTimeout() {
        Class<?> bqModern = findClassIfExists("com.android.server.am.BroadcastQueueModernImpl");
        if (bqModern != null) {
            for (Method m : bqModern.getDeclaredMethods()) {
                if ("deliveryTimeoutLocked".equals(m.getName())) {
                    hook(m, new IHook() {
                        @Override
                        public void before() {
                            XposedLog.logD(TAG, "Suppressed BroadcastQueueModernImpl deliveryTimeoutLocked");
                            returnNull();
                        }
                    });
                }
            }
        }
    }
}
