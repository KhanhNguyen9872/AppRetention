package com.hchen.appretention.hook.system.opt;

import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.findMethodIfExists;
import static com.hchen.hooktool.core.CoreTool.hook;

import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;

/**
 * Auto-exempts user background apps from Android Doze mode and App Standby power restrictions.
 * Ensures background network sockets, alarms, and jobs remain active when screen is off.
 *
 * @author Antigravity
 */
public final class DeviceIdleOpt {
    private static final String TAG = "DeviceIdleOpt";

    public static void init() {
        if (!isEnabled()) {
            XposedLog.logD(TAG, "DeviceIdleOpt is disabled by property.");
            return;
        }

        Class<?> dicClass = findClassIfExists("com.android.server.DeviceIdleController");
        if (dicClass == null) {
            XposedLog.logW(TAG, "DeviceIdleController class not found!");
            return;
        }

        Method isPowerSaveWhitelistApp = findMethodIfExists(dicClass, "isPowerSaveWhitelistApp", String.class);
        if (isPowerSaveWhitelistApp != null) {
            hook(isPowerSaveWhitelistApp, new IHook() {
                @Override
                public void before() {
                    String pkg = (String) getArg(0);
                    if (isTargetUserApp(pkg)) {
                        setResult(true);
                    }
                }
            });
        }

        Method isPowerSaveWhitelistExceptIdleApp = findMethodIfExists(dicClass, "isPowerSaveWhitelistExceptIdleApp", String.class);
        if (isPowerSaveWhitelistExceptIdleApp != null) {
            hook(isPowerSaveWhitelistExceptIdleApp, new IHook() {
                @Override
                public void before() {
                    String pkg = (String) getArg(0);
                    if (isTargetUserApp(pkg)) {
                        setResult(true);
                    }
                }
            });
        }

        XposedLog.logI(TAG, "DeviceIdleOpt initialized successfully!");
    }

    private static boolean isEnabled() {
        return SystemPropTool.getProp("persist.hchen.doze.opt.enable", true);
    }

    private static boolean isTargetUserApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        if (BackgroundRestrictOpt.isRestricted(packageName)) return false;
        if ("android".equals(packageName)) return false;
        if (packageName.startsWith("com.android.providers.")) return false;
        if (packageName.startsWith("com.android.server.")) return false;
        return true;
    }
}
